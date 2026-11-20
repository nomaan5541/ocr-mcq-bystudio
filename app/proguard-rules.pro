# Add rules that OEM-specific and library code won't be stripped
-keep class com.omnisolve.overlay.** { *; }

# AndroidX WorkManager, Room & Startup
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.startup.** { *; }
-dontwarn androidx.startup.**

# Google Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# WebView JS Bridges (CRITICAL for AndroidBridge on release builds)
-keepattributes JavascriptInterface,RuntimeVisibleAnnotations,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Firebase AI Logic, App Check, & Kotlinx Serialization
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.firebase.ai.** { *; }
-dontwarn com.google.firebase.ai.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.google.firebase.ai.**$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Kotlin Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Keep accessibility service metadata
-keep class com.omnisolve.overlay.service.AutoClickAccessibilityService { *; }
-keep class com.omnisolve.overlay.service.OverlayService { *; }

# Samsung, OPPO, Realme, OnePlus, Xiaomi OEM compatibility
# Some OEMs use reflection to read app metadata
-keep class android.** { *; }
-dontwarn android.**
-keepattributes SourceFile,LineNumberTable

# Strip verbose logs only; retain PRISM_PERF info logs for real-device diagnostics
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
}
