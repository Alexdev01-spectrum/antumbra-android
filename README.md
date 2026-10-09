# Antumbra Android

Native Android port of [Penumbra](https://github.com/shomykohai/penumbra).

## Status (0.2.0)

| Item | State |
|------|--------|
| USB Host + multi-interface claim + CDC | Done |
| `MtkPort` + JavaVM attach | Done |
| `PlProtocol` handshake (probe) — port stays owned | Done |
| SAF DA / image pickers | Done |
| Progress + last error + log export | Done |
| Adaptive icon | Done |
| CI workflow skeleton | Done |
| Re-enum via attach listener | Done |
| Full `Device` + DA upload + partition write | **Needs first successful `cargo ndk` + Option-port polish** |
| Unlock exploit path | Stub JNI — needs live DA session |
| Gradle wrapper jar | Properties present; run `gradle wrapper` once |
| Signed release | Config stub — add keystore |

## Build

```bash
git clone https://github.com/Alexdev01-spectrum/antumbra-android.git
cd antumbra-android

rustup default nightly
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk

export ANDROID_NDK_HOME=~/Android/Sdk/ndk/26.1.10909125

cargo ndk -t arm64-v8a -t armeabi-v7a \
  -o android/app/src/main/jniLibs \
  build --release -p antumbra-android

cd android
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
# once: gradle wrapper --gradle-version 8.9
./gradlew :app:assembleRelease
```

## License

AGPL-3.0-or-later
