# Antumbra Android

1:1 native Android port of [Penumbra / Antumbra](https://github.com/shomykohai/penumbra) — MediaTek flash, unlock, readback tool.

Dark, minimal, high-contrast UI. USB Host transport. Full feature surface once upstream core is linked.

## Screens

- **Home** — device status, connection state, quick actions
- **Devices** — live MTK device list with VID/PID, permission + claim
- **Flash** — scatter / image picker, partition select, progress
- **Readback** — partition dump to SAF
- **Unlock / Relock** 
- **Advanced** — RPMB, memory R/W, logs

## Build

```bash
# 1. Upstream core (submodule)
git submodule add https://github.com/shomykohai/penumbra.git upstream
git submodule update --init

# 2. Rust targets + cargo-ndk
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk

# 3. Native libs
cargo ndk -t arm64-v8a -t armeabi-v7a \
  -o android/app/src/main/jniLibs \
  build --release -p antumbra-android

# 4. APK
cd android && ./gradlew :app:assembleRelease
```

NDK r26+, SDK 35, minSdk 26.

## Architecture

```
Compose UI  →  ViewModel  →  Native.kt (JNI)
                                    ↓
                          libantumbra.so
                    AndroidUsbMTKPort : MtkPort
                                    ↓
                         UsbDeviceConnection
```

## License

AGPL-3.0-or-later (inherits upstream).
