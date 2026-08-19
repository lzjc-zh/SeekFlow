# DeepSeek Balance - ProGuard Rules
-keep class com.deepseek.balance.data.api.** { *; }
-keep class com.deepseek.balance.data.db.** { *; }

# Gson TypeToken - R8 混淆保护
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature
