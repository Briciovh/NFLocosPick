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

# ── Retrofit / Gson ──────────────────────────────────────────────────────────
# Retrofit and OkHttp ship their own consumer ProGuard rules; Gson needs help
# from the app because it reflects over our own DTOs at runtime.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# ESPN response DTOs are (de)serialized by Gson via reflection — keep field
# names so JSON keys still match after obfuscation.
-keep class com.softeen.nflocospicks.data.remote.espn.** { <fields>; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer