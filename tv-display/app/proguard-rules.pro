# Add project specific ProGuard rules here.

# Socket.IO
-keep class io.socket.** { *; }
-keep class io.socket.engineio.** { *; }
-keepclassmembers class * {
    @io.socket.annotations.* <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.pokernight.tvdisplay.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
