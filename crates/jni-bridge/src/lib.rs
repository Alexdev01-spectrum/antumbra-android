//! JNI surface — com.penumbra.antumbra.Native

use android_usb::{AndroidUsbMTKPort, ConnectionType};
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;
use std::sync::Mutex;

static HANDLES: Mutex<Vec<Option<AndroidUsbMTKPort>>> = Mutex::new(Vec::new());

fn insert(port: AndroidUsbMTKPort) -> jlong {
    let mut g = HANDLES.lock().unwrap();
    for (i, s) in g.iter_mut().enumerate() {
        if s.is_none() {
            *s = Some(port);
            return i as jlong;
        }
    }
    g.push(Some(port));
    (g.len() - 1) as jlong
}

fn with<F, R>(handle: jlong, f: F) -> Option<R>
where
    F: FnOnce(&mut AndroidUsbMTKPort) -> R,
{
    let mut g = HANDLES.lock().unwrap();
    g.get_mut(handle as usize).and_then(|s| s.as_mut()).map(f)
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_init(_env: JNIEnv, _class: JClass) {
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
    _class: JClass,
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
        Ok(p) => {
            let h = insert(p);
            log::info!("open handle={h} vid={vid:04x} pid={pid:04x}");
            h
        }
        Err(e) => {
            log::error!("open failed: {e}");
            -1
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_closeDevice(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    let mut g = HANDLES.lock().unwrap();
    if let Some(s) = g.get_mut(handle as usize) {
        *s = None;
        log::info!("closed {handle}");
    }
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_getPortName(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let name = with(handle, |p| p.get_port_name()).unwrap_or_else(|| "".into());
    env.new_string(name)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_getConnectionType(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    with(handle, |p| match p.connection_type() {
        ConnectionType::Brom => 0,
        ConnectionType::Preloader => 1,
        ConnectionType::Da => 2,
    })
    .unwrap_or(-1)
}

/// Placeholder for real handshake once penumbra-mtk Device is wired.
/// Returns 0 on success of a basic bulk round-trip probe.
#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_probe(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    with(handle, |port| {
        log::info!("probe {}", port.get_port_name());
        // Real init() from DeviceBuilder goes here after submodule link.
        0
    })
    .unwrap_or(-1)
}

// Future: flashPartition, readbackPartition, unlock, relock, rpmbRead, rpmbWrite
// will take file descriptors / byte arrays and drive penumbra_mtk::Device.
