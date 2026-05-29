# Add project specific ProGuard rules here.

# SLF4J
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Kotlin / coroutines（勿 keep 整包 kotlin.**，否则 R8 无法裁剪）
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose：仅保留 Composable 入口，勿 keep 整个 androidx.compose.**
-keep @androidx.compose.runtime.Composable class *
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn androidx.compose.**

# Ktor / Coil / ExoPlayer：允许裁剪，仅抑制警告
-dontwarn io.ktor.**
-dontwarn coil3.**
-dontwarn com.google.android.exoplayer2.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# Navigation
-keepclassmembers class * implements androidx.navigation.NavHost {
    public *;
}

# Release 移除 Log.v/d/i（保留 w/e）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# kotlinx.serialization
-keep,includedescriptorclasses class com.neko.music.data.api.**$$serializer { *; }
-keepclassmembers class com.neko.music.data.api.** {
    *** Companion;
}
-keepclasseswithmembers class com.neko.music.data.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# 应用模型（按需保留，避免接口字段被混淆）
-keep class com.neko.music.data.model.** { *; }
