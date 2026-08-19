# DeepSeek仪表盘 - ProGuard Rules
-keep class com.deepseek.lzjc.data.api.** { *; }
-keep class com.deepseek.lzjc.data.db.** { *; }
-keep class com.deepseek.lzjc.data.provider.** { *; }

# Retrofit 泛型响应类型
-keepattributes Signature
-keepattributes *Annotation*

# Gson TypeToken - 保留泛型签名，R8 混淆时不能擦除
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# 保留 ProviderStore 中的 TypeToken 泛型
-keep class com.deepseek.lzjc.data.provider.ProviderStore { *; }

# DataStore
-keep class androidx.datastore.** { *; }
