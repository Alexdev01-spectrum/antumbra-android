# Antumbra — keep JNI
-keep class com.penumbra.antumbra.Native { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
