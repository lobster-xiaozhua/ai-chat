-keepattributes *Annotation*, Signature, Serializable
-keepclassmembers class com.example.aichat.data.remote.dto.** { *; }

-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

-keep interface com.example.aichat.data.remote.OpenAiApiService { *; }

-assumenosideeffects class okhttp3.logging.HttpLoggingInterceptor {
    public void setLevel(okhttp3.logging.HttpLoggingInterceptor$Level);
}

-keep class com.example.aichat.data.local.db.** { *; }
-keepattributes *Annotation*
