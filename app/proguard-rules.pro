# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ================================
# AUDION PROGUARD RULES
# ================================

# Keep source file names and line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures for reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ================================
# ROOM DATABASE
# ================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** Companion;
}

# Keep all Room DAOs
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep database entities and their fields
-keep class com.audion.app.data.** { *; }
-keepclassmembers class com.audion.app.data.** { *; }

# ================================
# NATIVE METHODS (JNI)
# ================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep native library interfaces
-keep class com.audion.dsp.** { *; }
-keepclassmembers class com.audion.dsp.** {
    native <methods>;
}

# ================================
# GSON / JSON SERIALIZATION
# ================================
-keepattributes Signature
-keepattributes *Annotation*

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# Keep all model classes used with Gson
-keep class com.audion.app.models.** { *; }
-keepclassmembers class com.audion.app.models.** { *; }

# Prevent stripping of generic type information
-keepattributes Signature
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ================================
# VOSK / SHERPA ONNX (Speech Recognition)
# ================================
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

-keep class org.kaldi.** { *; }
-dontwarn org.kaldi.**

# ================================
# JNA (Java Native Access)
# ================================
-keep class net.java.dev.jna.** { *; }
-keepclassmembers class net.java.dev.jna.** { *; }
-dontwarn net.java.dev.jna.**

-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# ================================
# ITEXTPDF (PDF Generation)
# ================================
-keep class com.itextpdf.** { *; }
-keepclassmembers class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-dontwarn org.bouncycastle.**
-dontwarn org.spongycastle.**

# ================================
# LOTTIE ANIMATIONS
# ================================
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ================================
# MPANDROIDCHART
# ================================
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ================================
# TOURGUIDE
# ================================
-keep class tourguide.tourguide.** { *; }
-dontwarn tourguide.tourguide.**

# ================================
# ANDROID GIF DRAWABLE
# ================================
-keep class pl.droidsonroids.gif.** { *; }
-dontwarn pl.droidsonroids.gif.**

# ================================
# VIEWPAGER2
# ================================
-keep class androidx.viewpager2.** { *; }

# ================================
# REMOVE DEBUG LOGGING
# ================================
# Strip all debug, verbose, and info logs from release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Remove all log calls from custom loggers
-assumenosideeffects class * {
    void log*(...);
    void debug*(...);
}

# ================================
# ANDROID STANDARD RULES
# ================================
# Keep Activities, Services, Receivers, Providers
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep all activities in our package (including SplashActivity)
-keep class com.audion.app.** extends android.app.Activity { *; }
-keep class com.audion.app.** extends androidx.appcompat.app.AppCompatActivity { *; }

# Keep custom Views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ================================
# REFLECTION & ANNOTATIONS
# ================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ================================
# CRASHLYTICS / FIREBASE
# (Keep if you add Firebase in future)
# ================================
# -keepattributes *Annotation*
# -keep class com.crashlytics.** { *; }
# -dontwarn com.crashlytics.**

# ================================
# OPTIMIZATION FLAGS
# ================================
# Allow aggressive optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# ================================
# WARNING SUPPRESSION
# ================================
-dontwarn org.xmlpull.v1.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn kotlin.**