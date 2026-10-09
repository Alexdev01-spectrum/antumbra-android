//! JNI — stable session: port always owned; probe via PlProtocol; DA init builds Device ephemerally.

use android_usb::{set_vm, AndroidUsbMTKPort};
use jni::objects::{JByteArray, JClass, JObject, JString};
use jni::sys::{jbyteArray, jint, jlong, jstring, JNI_VERSION_1_6};
use jni::{JNIEnv, JavaVM};
use penumbra_mtk::port::ConnectionType;
use penumbra_mtk::preloader::PlProtocol;
use penumbra_mtk::{DeviceBuilder, MtkPort};
use std::sync::Mutex;

struct Session {
    port: AndroidUsbMTKPort,
    initialized: bool,
    hw_code: u32,
    mode: String,
    /// Last progress 0..100 for UI polling
    progress: u32,
    last_error: String,
}

static SESSIONS: Mutex<Vec<Option<Session>>> = Mutex::new(Vec::new());

fn insert(s: Session) -> jlong {
    let mut g = SESSIONS.lock().unwrap();
    for (i, slot) in g.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(s);
            return i as jlong;
        }
    }
    g.push(Some(s));
    (g.len() - 1) as jlong
}

fn with_session<F, R>(handle: jlong, f: F) -> Option<R>
where
    F: FnOnce(&mut Session) -> R,
{
    let mut g = SESSIONS.lock().unwrap();
    g.get_mut(handle as usize).and_then(|s| s.as_mut()).map(f)
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: JavaVM, _: *mut std::ffi::c_void) -> jint {
    set_vm(vm);
    JNI_VERSION_1_6 as jint
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_init(env: JNIEnv, _: JClass) {
    if let Ok(vm) = env.get_java_vm() {
        set_vm(vm);
    }
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("AntumbraNative"),
    );
    log::info!("native ready");
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_openDevice(
    mut env: JNIEnv,
    _: JClass,
    connection: JObject,
    ep_in: JObject,
    ep_out: JObject,
    conn_type: jint,
    vid: jint,
    pid: jint,
) -> jlong {
    let ct = match conn_type {
        0 => ConnectionType::Brom,
        2 => ConnectionType::Da,
        _ => ConnectionType::Preloader,
    };
    match AndroidUsbMTKPort::from_java(
        &mut env,
        connection,
        ep_in,
        ep_out,
        ct,
        vid as u16,
        pid as u16,
    ) {
        Ok(mut port) => {
            let _ = port.open();
            let mode = match ct {
                ConnectionType::Brom => "BROM",
                ConnectionType::Preloader => "Preloader",
                ConnectionType::Da => "DA",
            }
            .to_string();
            let h = insert(Session {
                port,
                initialized: false,
                hw_code: 0,
                mode,
                progress: 0,
                last_error: String::new(),
            });
            log::info!("open handle={h}");
            h
        }
        Err(e) => {
            log::error!("open: {e}");
            -1
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_closeDevice(_: JNIEnv, _: JClass, handle: jlong) {
    let mut g = SESSIONS.lock().unwrap();
    if let Some(slot) = g.get_mut(handle as usize) {
        if let Some(mut s) = slot.take() {
            let _ = s.port.close();
        }
        log::info!("closed {handle}");
    }
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_getPortName(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
) -> jstring {
    let name = with_session(handle, |s| s.port.get_port_name()).unwrap_or_default();
    env.new_string(name)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_getConnectionType(
    _: JNIEnv,
    _: JClass,
    handle: jlong,
) -> jint {
    with_session(handle, |s| match s.port.connection_type() {
        ConnectionType::Brom => 0,
        ConnectionType::Preloader => 1,
        ConnectionType::Da => 2,
    })
    .unwrap_or(-1)
}

/// Handshake via PlProtocol — port stays owned by session.
#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_probe(
    _: JNIEnv,
    _: JClass,
    handle: jlong,
) -> jint {
    with_session(handle, |session| {
        let mut pl = PlProtocol::new(&mut session.port);
        match pl.handshake() {
            Ok(()) => {
                let hw = pl.get_hw_code().unwrap_or(0);
                session.hw_code = hw;
                session.initialized = true;
                session.last_error.clear();
                log::info!("probe OK hw=0x{hw:04X}");
                0
            }
            Err(e) => {
                session.last_error = format!("{e}");
                log::error!("probe: {e}");
                -3
            }
        }
    })
    .unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_getHwCode(_: JNIEnv, _: JClass, h: jlong) -> jint {
    with_session(h, |s| s.hw_code as jint).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_isInitialized(_: JNIEnv, _: JClass, h: jlong) -> jint {
    with_session(h, |s| if s.initialized { 1 } else { 0 }).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_getProgress(_: JNIEnv, _: JClass, h: jlong) -> jint {
    with_session(h, |s| s.progress as jint).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_getLastError(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
) -> jstring {
    let msg = with_session(handle, |s| s.last_error.clone()).unwrap_or_default();
    env.new_string(msg)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// Build Device with DA bytes, run init + upload_da path when available.
/// Returns 0 on success.
#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_initWithDa(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    da: jbyteArray,
) -> jint {
    let da_arr = unsafe { JByteArray::from_raw(da) };
    let da_bytes: Vec<u8> = match env.convert_byte_array(&da_arr) {
        Ok(b) => b,
        Err(e) => {
            log::error!("da bytes: {e}");
            return -2;
        }
    };

    with_session(handle, |session| {
        session.progress = 5;
        // Port must move into Device — take it, build, init, upload, then we cannot get port back.
        // Strategy: only use Device for this call; on success mark session and leave port in a
        // "consumed" state by replacing with a note. For continued ops user re-opens USB.
        // Better long-term: keep Device in session (self-ref). For reliability of probe+flash:
        // take port, run full op, on any error session is dead (caller close+reopen).

        let port = std::mem::replace(
            &mut session.port,
            // Create a minimal dead port? We can't. Use unsafe take:
            // Actually use ptr::read + forget pattern carefully.
            session.port, // placeholder invalid — fixed below with Option
        );
        let _ = port; // silence — real code uses Option<AndroidUsbMTKPort>

        // Correct pattern with Option — refactor session.port to Option
        session.last_error = "initWithDa: use initWithDaV2 after Option port refactor".into();
        session.progress = 0;
        -5
    })
    .unwrap_or(-1)
}

/// Progress-aware flash entry (DA + image). Currently records intent and returns.
/// Full write lands when Option-port session is in place and Device APIs are confirmed.
#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_flash(
    mut env: JNIEnv,
    _: JClass,
    handle: jlong,
    da: jbyteArray,
    image: jbyteArray,
) -> jint {
    let da_arr = unsafe { JByteArray::from_raw(da) };
    let img_arr = unsafe { JByteArray::from_raw(image) };
    let da_len = env.get_array_length(&da_arr).unwrap_or(0);
    let img_len = env.get_array_length(&img_arr).unwrap_or(0);
    log::info!("flash request da={da_len} img={img_len} handle={handle}");

    with_session(handle, |session| {
        if !session.initialized {
            session.last_error = "probe first".into();
            return -1;
        }
        session.progress = 10;
        // DeviceBuilder::new(port).with_da_data(&da).build()?.init()?;
        // device.upload_da()?; write partitions from image/scatter
        session.last_error =
            "flash: DA upload + partition write pending Option<port> session + Device API".into();
        session.progress = 0;
        -5
    })
    .unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_unlock(
    _: JNIEnv,
    _: JClass,
    handle: jlong,
) -> jint {
    with_session(handle, |session| {
        if !session.initialized {
            session.last_error = "probe first".into();
            return -1;
        }
        session.last_error =
            "unlock: needs DA session + exploit path from penumbra_mtk".into();
        -5
    })
    .unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_relock(
    _: JNIEnv,
    _: JClass,
    handle: jlong,
) -> jint {
    with_session(handle, |session| {
        session.last_error = "relock: needs DA session".into();
        -5
    })
    .unwrap_or(-1)
}
