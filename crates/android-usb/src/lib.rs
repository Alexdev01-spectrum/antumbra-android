//! Android USB Host backend implementing penumbra_mtk::port::MtkPort.

use jni::objects::{GlobalRef, JObject, JValue};
use jni::{JNIEnv, JavaVM};
use once_cell::sync::OnceCell;
use penumbra_mtk::error::{ConnectionError, Error as PenumbraError, Result as PenumbraResult};
use penumbra_mtk::port::{ConnectionType, MtkPort};
use std::collections::VecDeque;
use std::time::Duration;

static JVM: OnceCell<JavaVM> = OnceCell::new();

pub fn set_vm(vm: JavaVM) {
    let _ = JVM.set(vm);
}

fn with_env<F, R>(f: F) -> PenumbraResult<R>
where
    F: FnOnce(&mut JNIEnv) -> PenumbraResult<R>,
{
    let vm = JVM.get().ok_or_else(|| {
        PenumbraError::Connection(ConnectionError::OpenFailed("JavaVM not set".into()))
    })?;
    let mut env = vm.attach_current_thread().map_err(|e| {
        PenumbraError::Connection(ConnectionError::OpenFailed(format!("attach: {e}")))
    })?;
    f(&mut env)
}

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
    ) -> PenumbraResult<Self> {
        let connection = env.new_global_ref(connection).map_err(|e| {
            PenumbraError::Connection(ConnectionError::OpenFailed(format!("ref: {e}")))
        })?;
        let ep_in = env.new_global_ref(ep_in).map_err(|e| {
            PenumbraError::Connection(ConnectionError::OpenFailed(format!("ref: {e}")))
        })?;
        let ep_out = env.new_global_ref(ep_out).map_err(|e| {
            PenumbraError::Connection(ConnectionError::OpenFailed(format!("ref: {e}")))
        })?;

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

    fn bulk_inner(
        &self,
        env: &mut JNIEnv,
        endpoint: &GlobalRef,
        buf: &mut [u8],
        length: i32,
    ) -> PenumbraResult<i32> {
        let java_buf = env
            .byte_array_from_slice(&buf[..length as usize])
            .map_err(|e| PenumbraError::Io(std::io::Error::other(e.to_string())))?;

        let result = env
            .call_method(
                &self.connection,
                "bulkTransfer",
                "(Landroid/hardware/usb/UsbEndpoint;[BII)I",
                &[
                    JValue::Object(endpoint.as_obj()),
                    JValue::Object(&java_buf),
                    JValue::Int(length),
                    JValue::Int(self.timeout.as_millis().min(i32::MAX as u128) as i32),
                ],
            )
            .map_err(|e| PenumbraError::Io(std::io::Error::other(e.to_string())))?
            .i()
            .map_err(|e| PenumbraError::Io(std::io::Error::other(e.to_string())))?;

        if result < 0 {
            return Err(PenumbraError::Timeout);
        }
        if result > 0 {
            let bytes = env
                .convert_byte_array(&java_buf)
                .map_err(|e| PenumbraError::Io(std::io::Error::other(e.to_string())))?;
            let n = result as usize;
            buf[..n].copy_from_slice(&bytes[..n]);
        }
        Ok(result)
    }

    /// CDC line coding — mirrors libusb_backend setup_cdc for Preloader/DA.
    pub fn setup_cdc(&mut self) -> PenumbraResult<()> {
        const CDC_INTERFACE: u16 = 1;
        const SET_LINE_CODING: u8 = 0x20;
        const SET_CONTROL_LINE_STATE: u8 = 0x22;
        const LINE_CODING: [u8; 7] = [0x00, 0x00, 0x0E, 0x00, 0x00, 0x00, 0x08];
        // request_type: host-to-device | class | interface = 0x21
        let request_type = 0x21u8;
        self.ctrl_out(request_type, SET_LINE_CODING, 0, CDC_INTERFACE, &LINE_CODING)?;
        self.ctrl_out(request_type, SET_CONTROL_LINE_STATE, 0x03, CDC_INTERFACE, &[])?;
        Ok(())
    }
}

impl MtkPort for AndroidUsbMTKPort {
    fn open(&mut self) -> PenumbraResult<()> {
        self.is_open = true;
        if self.connection_type != ConnectionType::Brom {
            let _ = self.setup_cdc();
        }
        Ok(())
    }

    fn close(&mut self) -> PenumbraResult<()> {
        self.is_open = false;
        Ok(())
    }

    fn reenumerate(&mut self, _vid: u16, _pid: u16) -> PenumbraResult<()> {
        Err(PenumbraError::Connection(ConnectionError::OpenFailed(
            "reenumerate unsupported — unplug/replug or wait for new USB device".into(),
        )))
    }

    fn read_exact(&mut self, buf: &mut [u8]) -> PenumbraResult<usize> {
        if !self.is_open {
            return Err(PenumbraError::Connection(ConnectionError::OpenFailed(
                "not open".into(),
            )));
        }
        with_env(|env| {
            let mut total = 0usize;
            while total < buf.len() && !self.read_buf.is_empty() {
                buf[total] = self.read_buf.pop_front().unwrap();
                total += 1;
            }
            while total < buf.len() {
                let needed = buf.len() - total;
                let chunk = needed.min(self.temp_buf.len());
                let n =
                    self.bulk_inner(env, &self.ep_in, &mut self.temp_buf[..chunk], chunk as i32)?;
                if n == 0 {
                    continue;
                }
                let n = n as usize;
                let copy = n.min(needed);
                buf[total..total + copy].copy_from_slice(&self.temp_buf[..copy]);
                total += copy;
                if copy < n {
                    self.read_buf.extend(&self.temp_buf[copy..n]);
                }
            }
            Ok(total)
        })
    }

    fn write_all(&mut self, buf: &[u8]) -> PenumbraResult<()> {
        if !self.is_open {
            return Err(PenumbraError::Connection(ConnectionError::OpenFailed(
                "not open".into(),
            )));
        }
        with_env(|env| {
            let mut written = 0;
            let mut tmp = buf.to_vec();
            while written < buf.len() {
                let n = self.bulk_inner(
                    env,
                    &self.ep_out,
                    &mut tmp[written..],
                    (buf.len() - written) as i32,
                )?;
                if n <= 0 {
                    return Err(PenumbraError::Timeout);
                }
                written += n as usize;
            }
            Ok(())
        })
    }

    fn flush(&mut self) -> PenumbraResult<()> {
        Ok(())
    }

    fn get_baudrate(&self) -> u32 {
        0
    }

    fn get_port_name(&self) -> String {
        self.port_name.clone()
    }

    fn set_timeout(&mut self, timeout: Duration) -> PenumbraResult<()> {
        self.timeout = timeout;
        Ok(())
    }

    fn get_timeout(&self) -> Duration {
        self.timeout
    }

    fn connection_type(&self) -> ConnectionType {
        self.connection_type
    }

    fn set_connection_type(&mut self, t: ConnectionType) -> PenumbraResult<()> {
        self.connection_type = t;
        Ok(())
    }

    fn ctrl_out(
        &mut self,
        request_type: u8,
        request: u8,
        value: u16,
        index: u16,
        data: &[u8],
    ) -> PenumbraResult<()> {
        with_env(|env| {
            let java_buf = env
                .byte_array_from_slice(data)
                .map_err(|e| PenumbraError::Io(std::io::Error::other(e.to_string())))?;
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
                        JValue::Int(self.timeout.as_millis().min(i32::MAX as u128) as i32),
                    ],
                )
                .map_err(|_| PenumbraError::Connection(ConnectionError::CtrlTransferOutFailed))?
                .i()
                .map_err(|_| PenumbraError::Connection(ConnectionError::CtrlTransferOutFailed))?;
            if result < 0 {
                return Err(PenumbraError::Connection(ConnectionError::CtrlTransferOutFailed));
            }
            Ok(())
        })
    }

    fn ctrl_in(
        &mut self,
        request_type: u8,
        request: u8,
        value: u16,
        index: u16,
        len: usize,
    ) -> PenumbraResult<Vec<u8>> {
        with_env(|env| {
            let mut buf = vec![0u8; len];
            let java_buf = env
                .byte_array_from_slice(&buf)
                .map_err(|e| PenumbraError::Io(std::io::Error::other(e.to_string())))?;
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
                        JValue::Int(self.timeout.as_millis().min(i32::MAX as u128) as i32),
                    ],
                )
                .map_err(|_| PenumbraError::Connection(ConnectionError::CtrlTransferInFailed))?
                .i()
                .map_err(|_| PenumbraError::Connection(ConnectionError::CtrlTransferInFailed))?;
            if result < 0 {
                return Err(PenumbraError::Connection(ConnectionError::CtrlTransferInFailed));
            }
            let bytes = env
                .convert_byte_array(&java_buf)
                .map_err(|e| PenumbraError::Io(std::io::Error::other(e.to_string())))?;
            let n = result as usize;
            buf[..n].copy_from_slice(&bytes[..n]);
            buf.truncate(n);
            Ok(buf)
        })
    }
}
