# Real R8 obfuscation & shrinking rules for Prism Solver release builds.
# Keeps reflection-accessed models, Room, Firestore, Gson, ML Kit, and JS bridges
# while obfuscating and shrinking internal app code and unused classes.

# Keep AndroidX @Keep annotated models (Firestore LiveClassRoom, Telemetry, etc.)
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep Firestore, Room, and Gson serialized models & entities
-keep class com.omnisolve.overlay.model.** { *; }
-keep class com.omnisolve.overlay.data.local.** { *; }
-keep class com.omnisolve.overlay.data.SolvedQuestionRecord { *; }
-keep class com.omnisolve.overlay.data.LiveClassRoom* { *; }
-keep class com.omnisolve.overlay.data.LiveRoom* { *; }
-keep class com.omnisolve.overlay.data.UserActivityProfile { *; }
-keep class com.omnisolve.overlay.data.UserRequestLog { *; }
-keep class com.omnisolve.overlay.api.GoogleForm* { *; }
-keep class com.omnisolve.overlay.ui.theme.RemoteThemeSpec* { *; }

# Keep Android manifest components & ViewBindings
-keep class com.omnisolve.overlay.MainActivity { *; }
-keep class com.omnisolve.overlay.LoginActivity { *; }
-keep class com.omnisolve.overlay.GoogleFormsWebActivity { *; }
-keep class com.omnisolve.overlay.service.OverlayService { *; }
-keep class com.omnisolve.overlay.service.PrismQuickSettingsTileService { *; }
-keep class com.omnisolve.overlay.service.FormsNotificationActionReceiver { *; }
-keep class com.omnisolve.overlay.service.PrismEngagementAlarmReceiver { *; }
-keep class com.omnisolve.overlay.databinding.** { *; }

# AndroidX Room & Startup
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.startup.** { *; }
-dontwarn androidx.startup.**

# WebView JS Bridges (CRITICAL for AndroidBridge on release builds)
-keepattributes JavascriptInterface,RuntimeVisibleAnnotations,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Firebase AI Logic, Firestore, Auth, App Check, & Kotlinx Serialization
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.firebase.ai.** { *; }
-dontwarn com.google.firebase.ai.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keepattributes *Annotation*, InnerClasses, Signature
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
-dontwarn sun.misc.**

# ML Kit Text Recognition & ZXing QR
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Kotlin Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Preserve stack trace source files & line numbers for Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose debug logs only; retain PRISM_PERF info logs for real-device diagnostics
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
}
