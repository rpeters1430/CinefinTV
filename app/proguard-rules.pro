-keep class org.jellyfin.** { *; }
-keep class com.rpeters.cinefintv.data.model.** { *; }
-dontwarn org.slf4j.**

# Compose animation tooling is only used by Android Studio, not at runtime
-dontwarn androidx.compose.animation.tooling.**

# sun.misc.Unsafe is a JVM internal not present on Android
-dontwarn sun.misc.Unsafe

# androidx.window.extensions and sidecar are optional OEM-provided implementations
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
