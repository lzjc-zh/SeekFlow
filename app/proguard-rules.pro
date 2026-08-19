# DeepSeek Balance - ProGuard Rules
-keep class com.deepseek.balance.data.api.** { *; }
-keep class com.deepseek.balance.data.db.** { *; }

# Gson 完整保护
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature
-keepattributes *Annotation*
# 保留所有带 @SerializedName 注解的字段
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# 保留 API 数据模型
-keep class com.deepseek.lzjc.data.api.** { *; }
