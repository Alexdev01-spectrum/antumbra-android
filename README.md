# Antumbra Android

Native Android APK port of [Penumbra / Antumbra](https://github.com/shomykohai/penumbra) — MediaTek flash / unlock / readback tool.

Uses Android USB Host API + a new `AndroidUsbMTKPort` backend that implements the existing `MtkPort` trait. UI is Jetpack Compose.

## Status

Scaffolding only. Core logic still lives in upstream `penumbra-mtk`. This repo holds:

- Android USB Host transport (`android_usb_backend.rs`)
- JNI / UniFFI surface
- Compose UI shell + USB permission flow
- `cargo-ndk` + Gradle build pipeline

## Requirements

- Android NDK r26+
- Rust nightly (or stable once upstream drops edition 2024 requirements)
- `cargo-ndk`
- Android Studio / SDK 34+

## Quick build

```bash
# clone upstream core as submodule or path dependency
git submodule update --init

rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk

# build native libs
cargo ndk -t arm64-v8a -t armeabi-v7a \
  -o android/app/src/main/jniLibs \
  build --release -p antumbra-android --features android-usb

# APK
cd android && ./gradlew :app:assembleRelease
```

## License

AGPL-3.0-or-later (same as upstream Penumbra).
