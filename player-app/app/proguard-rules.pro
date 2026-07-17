# Keep Socket.IO classes
-keep class io.socket.** { *; }
-keep class org.json.** { *; }

# Keep Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Keep model classes
-keep class com.pokernight.player.data.model.** { *; }
