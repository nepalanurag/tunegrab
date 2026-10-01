# TuneGrab release shrink rules.
#
# R8's default Android rules plus the consumer rules shipped inside the
# Play Billing / Play Integrity / AdMob AARs already keep everything those
# SDKs need. The rules below cover our own code.

# Keep native entry points (ffmpeg / yt-dlp JNI bridges live in :library).
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Billing callbacks are invoked via listener interfaces — keep the
# signatures R8 might otherwise strip.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Our anti-tamper classes must survive shrinking (they are referenced, but
# be explicit — a missing LicenseGuard would silently unlock everything).
-keep class com.tunegrab.app.LicenseGuard { *; }
-keep class com.tunegrab.app.TamperCheck { *; }
-keep class com.tunegrab.app.IntegrityManager { *; }
-keep class com.tunegrab.app.BillingManager { *; }

# yt-dlp engine classes are loaded reflectively in a few places.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

# Apache Commons Compress instantiates zip extra-field handlers via
# Class.newInstance() inside ExtraFieldUtils.<clinit>. R8 strips their
# no-arg constructors (reflection is invisible to it), so every
# `new ZipFile(...)` in YoutubeDL.initPython dies with
# "X is not a concrete class" -> ExceptionInInitializerError on release
# builds. Keep the whole library (and commons-io, used on the same path).
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**

# Serializable enums persisted by name in SharedPreferences.
-keepclassmembers enum com.tunegrab.app.* {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging from release builds (keeps APK lean, hides internals).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Media3 playback stack: keep it whole. R8 was stripping something the
# MediaSessionService needs at startup, which crashed release builds on
# launch (the unshrunk debug build opened fine).
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep enum androidx.media3.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
-dontwarn androidx.media3.**
-dontwarn com.google.common.util.concurrent.**

# jaudiotagger (Settings -> "Clean up song info"): tag I/O uses runtime
# type lookups across its own packages, so keep the whole library.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
