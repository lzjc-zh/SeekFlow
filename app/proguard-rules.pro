# DeepSeek仪表盘 - ProGuard Rules
-keep class com.deepseek.lzjc.data.api.** { *; }
-keep class com.deepseek.lzjc.data.db.** { *; }
-keep class com.deepseek.lzjc.data.provider.** { *; }

# Retrofit 泛型响应类型
-keepattributes Signature
-keepattributes *Annotation*
