# Antumbra Android

Native Android port of [Penumbra / Antumbra](https://github.com/shomykohai/penumbra).

- **Transport:** `AndroidUsbMTKPort` implements `penumbra_mtk::port::MtkPort` via USB Host + JNI
- **Core:** `penumbra-mtk` from upstream git (exploits + localslakeyring, no host USB backends)
- **UI:** dark void + amber, Home / Devices / Flash / Unlock

## Build (your machine — needs NDK)

```bash
git clone https://github.com/Alexdev01-spectrum/antumbra-android.git
cd antumbra-android

# Nightly may be required (upstream uses edition 2024 / trait_alias)
rustup default nightly
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk

# Point cargo-ndk at your NDK (example)
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/26.1.10909125

cargo ndk -t arm64-v8a -t armeabi-v7a \
  -o android/app/src/main/jniLibs \
  build --release -p antumbra-android

cd android
# create local.properties with sdk.dir=...
./gradlew :app:assembleRelease
```

APK: `android/app/build/outputs/apk/release/app-release-unsigned.apk`

Sign with your keystore before install.

## What works in code now

| Layer | Status |
|-------|--------|
| USB Host claim + bulk/ctrl | Implemented |
| `MtkPort` on Android | Implemented (JavaVM attach) |
| `DeviceBuilder` + `Device::init` | Wired in `Native.probe` |
| Flash / unlock JNI | UI ready; DA upload + write next |
| Aesthetic Compose UI | Done |

## Note on submodule

Upstream is pulled as a **Cargo git dependency**, not a git submodule. Same source, simpler for Android CI. If you prefer a submodule:

```bash
git submodule add https://github.com/shomykohai/penumbra.git upstream
# then switch Cargo.toml path = "../../upstream/core"
```

## License

AGPL-3.0-or-later
