# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class io.nekohasekai.libbox.** { *; }
-keep class go.** { *; }
-dontwarn io.nekohasekai.libbox.**
-dontwarn go.**

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class com.example.model.** { *; }
-keepclassmembers class com.example.data.** { *; }

# OkHttp optional JVM TLS platform checks
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Firebase AI Logic & App Check release safety
-keep class com.google.firebase.** { *; }
-keep class com.google.android.recaptcha.** { *; }
-dontwarn com.google.android.recaptcha.**
-keep class com.example.ai.** { *; }

