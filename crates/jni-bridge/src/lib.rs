//! JNI surface for the Antumbra Android app.
//!
//! Package: com.penumbra.antumbra
//! Class:   Native

use android_usb::{AndroidUsbMTKPort, ConnectionType};
use jni::objects::{JClass, JObject};
use jni::sys::{jbyte, jint, jlong};
use jni::JNIEnv;
use std::sync::Mutex;

// Simple handle table — production code should use a better map.
static HANDLES: Mutex<Vec<Option<AndroidUsbMTKPort>>> = Mutex::new(Vec::new());

fn insert_handle(port: AndroidUsbMTKPort) -> jlong {
    let mut guards = HANDLES.lock().unwrap();
    for (i, slot) in guards.iter_mut().enumerate() {
        if slot.is_none() {
            *slot = Some(port);
            return i as jlong;
        }
    }
    guards.push(Some(port));
    (guards.len() - 1) as jlong
}

fn with_handle<F, R>(handle: jlong, f: F) -> Option<R>
where
    F: FnOnce(&mut AndroidUsbMTKPort) -> R,
{
    let mut guards = HANDLES.lock().unwrap();
    let idx = handle as usize;
    guards.get_mut(idx).and_then(|s| s.as_mut()).map(f)
}

#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_init(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("AntumbraNative"),
    );
    log::info!("antumbra native library loaded");
}

/// Open a device that the Kotlin side has already claimed.
/// Returns an opaque handle (jlong) or -1 on failure.
#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_openDevice(
    mut env: JNIEnv,
    _class: JClass,
    connection: JObject, // UsbDeviceConnection
    ep_in: JObject,      // UsbEndpoint
    ep_out: JObject,     // UsbEndpoint
    conn_type: jint,
) -> jlong {
    let ct = match conn_type {
        0 => ConnectionType::Brom,
        1 => ConnectionType::Preloader,
        2 => ConnectionType::Da,
        _ => ConnectionType::Preloader,
    };

    match AndroidUsbMTKPort::from_java(&mut env, connection, ep_in, ep_out, ct) {
        Ok(port) => {
            let h = insert_handle(port);
            log::info!("opened Android USB port, handle={h}");
            h
        }
        Err(e) => {
            log::error!("openDevice failed: {e}");
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
    let mut guards = HANDLES.lock().unwrap();
    let idx = handle as usize;
    if let Some(slot) = guards.get_mut(idx) {
        *slot = None;
        log::info!("closed handle {handle}");
    }
}

/// Simple smoke-test: write a few bytes and read response.
/// Real flash / unlock APIs will be added once penumbra-mtk is wired.
#[no_mangle]
pub extern "system" fn Java_com_penumbra_antumbra_Native_smokeTest(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    with_handle(handle, |port| {
        // placeholder — real handshake lives in penumbra-mtk Device
        log::info!("smokeTest on handle {handle}, type={:?}", port.connection_type());
        0
    })
    .unwrap_or(-1)
}
