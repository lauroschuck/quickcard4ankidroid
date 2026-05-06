# --- 1. Global Controls ---
# Signature is critical for Gson to understand generic types like List<String>
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# --- 2. Annotation-Driven Rule code to keep ---
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# --- 3. JavaScript Interface ---
# Do not touch so that WebView can still call it
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- 4. Library Specifics ---

# Gson: Standard narrow rules
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer
-keep public class * implements com.google.gson.TypeAdapter

# Handlebars: Just ignore its warnings; shrinking is handled by @Keep on your Input models
-dontwarn com.github.jknack.handlebars.**

# Lombok (if any remains in the bytecode)
-dontwarn lombok.**
