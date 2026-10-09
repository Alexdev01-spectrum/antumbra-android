//! Android USB Host backend shaped exactly like penumbra-mtk's LibUsbMTKPort.
//! Implements the same surface the MtkPort trait expects so it can be dropped
//! in once upstream is linked as a path dependency.

use jni::objects::{GlobalRef, JObject, JValue};
use jni::JNIEnv;
use std::collections::VecDeque;
use std::time::Duration;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AndroidUsbError {
    #[error("JNI: {0}")]
    Jni(String),
    #[error("transfer: {0}")]
    Transfer(String),
    #[error("not open")]
    NotOpen,
    #[error("timeout")]
    Timeout,
    #[error("re-enumeration not supported on Android USB Host")]
    ReenumerateUnsupported,
}

pub type Result<T> = std::result::Result<T, AndroidUsbError>;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConnectionType {
    Brom,
    Preloader,
    Da,
}

/// Full MtkPort-compatible Android USB port.
pub struct AndroidUsbMTKPort {
    connection: GlobalRef,
    ep_in: GlobalRef,
    ep_out: GlobalRef,
    timeout: Duration,
    connection_type: ConnectionType,
    port_name: String,
    is_open: bool,
    read_buf: VecDeque<u8>,
    temp_buf: Vec<u8>,
}

impl AndroidUsbMTKPort {
    pub fn from_java(
        env: &mut JNIEnv,
        connection: JObject,
        ep_in: JObject,
        ep_out: JObject,
        conn_type: ConnectionType,
        vid: u16,
        pid: u16,
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
            timeout: Duration::from_millis(1000),
            connection_type: conn_type,
            port_name: format!("USB {:04X}:{:04X} (android)", vid, pid),
            is_open: true,
            read_buf: VecDeque::new(),
            temp_buf: vec![0u8; 0x80000],
        })
    }

    fn bulk(
        &self,
        env: &mut JNIEnv,
        endpoint: &GlobalRef,
        buf: &mut [u8],
        length: i32,
    ) -> Result<i32> {
        let java_buf = env
            .byte_array_from_slice(&buf[..length as usize])
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
                    JValue::Int(self.timeout.as_millis() as i32),
                ],
            )
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?
            .i()
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;

        if result < 0 {
            return Err(AndroidUsbError::Transfer(format!("bulk {result}")));
        }

        if result > 0 {
            let bytes = env
                .convert_byte_array(&java_buf)
                .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;
            let n = result as usize;
            buf[..n].copy_from_slice(&bytes[..n]);
        }
        Ok(result)
    }

    // --- MtkPort surface ---

    pub fn open(&mut self) -> Result<()> {
        self.is_open = true;
        Ok(())
    }

    pub fn close(&mut self) -> Result<()> {
        self.is_open = false;
        Ok(())
    }

    pub fn reenumerate(&mut self, _vid: u16, _pid: u16) -> Result<()> {
        Err(AndroidUsbError::ReenumerateUnsupported)
    }

    pub fn read_exact(&mut self, env: &mut JNIEnv, buf: &mut [u8]) -> Result<usize> {
        if !self.is_open {
            return Err(AndroidUsbError::NotOpen);
        }
        let mut total = 0;

        if !self.read_buf.is_empty() {
            let take = buf.len().min(self.read_buf.len());
            for i in 0..take {
                buf[i] = self.read_buf.pop_front().unwrap();
            }
            total += take;
        }

        while total < buf.len() {
            let needed = buf.len() - total;
            let chunk = needed.min(self.temp_buf.len());
            match self.bulk(env, &self.ep_in, &mut self.temp_buf[..chunk], chunk as i32) {
                Ok(0) => continue,
                Ok(n) => {
                    let n = n as usize;
                    let copy = n.min(needed);
                    buf[total..total + copy].copy_from_slice(&self.temp_buf[..copy]);
                    total += copy;
                    if copy < n {
                        self.read_buf.extend(&self.temp_buf[copy..n]);
                    }
                }
                Err(AndroidUsbError::Transfer(_)) if total > 0 => {
                    // partial — push back and surface timeout-like
                    for b in buf[..total].iter().rev() {
                        self.read_buf.push_front(*b);
                    }
                    return Err(AndroidUsbError::Timeout);
                }
                Err(e) => return Err(e),
            }
        }
        Ok(total)
    }

    pub fn write_all(&mut self, env: &mut JNIEnv, buf: &[u8]) -> Result<()> {
        if !self.is_open {
            return Err(AndroidUsbError::NotOpen);
        }
        let mut written = 0;
        let mut tmp = buf.to_vec();
        while written < buf.len() {
            let n = self.bulk(
                env,
                &self.ep_out,
                &mut tmp[written..],
                (buf.len() - written) as i32,
            )?;
            if n <= 0 {
                return Err(AndroidUsbError::Timeout);
            }
            written += n as usize;
        }
        Ok(())
    }

    pub fn flush(&mut self) -> Result<()> {
        Ok(())
    }

    pub fn get_baudrate(&self) -> u32 {
        0
    }

    pub fn get_port_name(&self) -> String {
        self.port_name.clone()
    }

    pub fn set_timeout(&mut self, timeout: Duration) -> Result<()> {
        self.timeout = timeout;
        Ok(())
    }

    pub fn get_timeout(&self) -> Duration {
        self.timeout
    }

    pub fn connection_type(&self) -> ConnectionType {
        self.connection_type
    }

    pub fn set_connection_type(&mut self, t: ConnectionType) -> Result<()> {
        self.connection_type = t;
        Ok(())
    }

    pub fn ctrl_out(
        &mut self,
        env: &mut JNIEnv,
        request_type: u8,
        request: u8,
        value: u16,
        index: u16,
        data: &[u8],
    ) -> Result<()> {
        let java_buf = env
            .byte_array_from_slice(data)
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;

        let result = env
            .call_method(
                &self.connection,
                "controlTransfer",
                "(IIII[BII)I",
                &[
                    JValue::Int(request_type as i32),
                    JValue::Int(request as i32),
                    JValue::Int(value as i32),
                    JValue::Int(index as i32),
                    JValue::Object(&java_buf),
                    JValue::Int(data.len() as i32),
                    JValue::Int(self.timeout.as_millis() as i32),
                ],
            )
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?
            .i()
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;

        if result < 0 {
            return Err(AndroidUsbError::Transfer(format!("ctrl_out {result}")));
        }
        Ok(())
    }

    pub fn ctrl_in(
        &mut self,
        env: &mut JNIEnv,
        request_type: u8,
        request: u8,
        value: u16,
        index: u16,
        len: usize,
    ) -> Result<Vec<u8>> {
        let mut buf = vec![0u8; len];
        let java_buf = env
            .byte_array_from_slice(&buf)
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;

        let result = env
            .call_method(
                &self.connection,
                "controlTransfer",
                "(IIII[BII)I",
                &[
                    JValue::Int(request_type as i32),
                    JValue::Int(request as i32),
                    JValue::Int(value as i32),
                    JValue::Int(index as i32),
                    JValue::Object(&java_buf),
                    JValue::Int(len as i32),
                    JValue::Int(self.timeout.as_millis() as i32),
                ],
            )
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?
            .i()
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;

        if result < 0 {
            return Err(AndroidUsbError::Transfer(format!("ctrl_in {result}")));
        }

        let bytes = env
            .convert_byte_array(&java_buf)
            .map_err(|e| AndroidUsbError::Jni(e.to_string()))?;
        let n = result as usize;
        buf[..n].copy_from_slice(&bytes[..n]);
        buf.truncate(n);
        Ok(buf)
    }
}
