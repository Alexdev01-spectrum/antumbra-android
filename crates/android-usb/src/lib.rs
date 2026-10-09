//! Android USB Host backend implementing the Penumbra `MtkPort` trait.
//!
//! The actual `MtkPort` trait lives in upstream `penumbra-mtk`.
//! This crate provides the concrete type that talks to
//! `android.hardware.usb.UsbDeviceConnection` via JNI.

use jni::objects::{GlobalRef, JObject, JValue};
use jni::JNIEnv;
use std::time::Duration;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AndroidUsbError {
    #[error("JNI error: {0}")]
    Jni(String),
    #[error("USB transfer failed: {0}")]
    Transfer(String),
    #[error("Device not open")]
    NotOpen,
    #[error("Re-enumeration not supported on Android USB Host")]
    ReenumerateUnsupported,
}

pub type Result<T> = std::result::Result<T, AndroidUsbError>;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConnectionType {
    Brom,
    Preloader,
    Da,
}

/// Concrete port that holds a GlobalRef to the Java UsbDeviceConnection.
pub struct AndroidUsbMTKPort {
    connection: GlobalRef,
    /// Java UsbEndpoint objects (GlobalRef) for bulk IN / OUT
    ep_in: GlobalRef,
    ep_out: GlobalRef,
    timeout_ms: i32,
    connection_type: ConnectionType,
}

impl AndroidUsbMTKPort {
    /// Construct from an already-opened + claimed UsbDeviceConnection.
    /// Called from the JNI layer after the Kotlin side has done
    /// UsbManager.openDevice + claimInterface.
    pub fn from_java(
        env: &mut JNIEnv,
        connection: JObject,
        ep_in: JObject,
        ep_out: JObject,
        conn_type: ConnectionType,
    ) -> Result<Self> {
        let connection = env
            .new_global_ref(connection)
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;
        let ep_in = env
            .new_global_ref(ep_in)
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;
        let ep_out = env
            .new_global_ref(ep_out)
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;

        Ok(Self {
            connection,
            ep_in,
            ep_out,
            timeout_ms: 1000,
            connection_type: conn_type,
        })
    }

    fn bulk_transfer(
        &self,
        env: &mut JNIEnv,
        endpoint: &GlobalRef,
        buf: &mut [u8],
        length: i32,
    ) -> Result<i32> {
        // UsbDeviceConnection.bulkTransfer(UsbEndpoint, byte[], int, int)
        let java_buf = env
            .byte_array_from_slice(buf)
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;

        let result = env
            .call_method(
                &self.connection,
                "bulkTransfer",
                "(Landroid/hardware/usb/UsbEndpoint;[BII)I",
                &[
                    JValue::Object(endpoint.as_obj()),
                    JValue::Object(&java_buf),
                    JValue::Int(length),
                    JValue::Int(self.timeout_ms),
                ],
            )
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?
            .i()
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;

        if result < 0 {
            return Err(AndroidUsbError::Transfer(format!("bulkTransfer returned {result}")));
        }

        // copy data back for IN transfers
        if result > 0 {
            let bytes = env
                .convert_byte_array(&java_buf)
                .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;
            let n = result as usize;
            buf[..n].copy_from_slice(&bytes[..n]);
        }

        Ok(result)
    }

    pub fn read_exact(&self, env: &mut JNIEnv, buf: &mut [u8]) -> Result<usize> {
        let mut filled = 0;
        while filled < buf.len() {
            let n = self.bulk_transfer(env, &self.ep_in, &mut buf[filled..], (buf.len() - filled) as i32)?;
            if n == 0 {
                break;
            }
            filled += n as usize;
        }
        Ok(filled)
    }

    pub fn write_all(&self, env: &mut JNIEnv, buf: &[u8]) -> Result<()> {
        let mut sent = 0;
        let mut tmp = buf.to_vec();
        while sent < buf.len() {
            let n = self.bulk_transfer(env, &self.ep_out, &mut tmp[sent..], (buf.len() - sent) as i32)?;
            if n <= 0 {
                return Err(AndroidUsbError::Transfer("write stalled".into()));
            }
            sent += n as usize;
        }
        Ok(())
    }

    pub fn set_timeout(&mut self, timeout: Duration) {
        self.timeout_ms = timeout.as_millis().min(i32::MAX as u128) as i32;
    }

    pub fn connection_type(&self) -> ConnectionType {
        self.connection_type
    }

    pub fn set_connection_type(&mut self, t: ConnectionType) {
        self.connection_type = t;
    }
}
