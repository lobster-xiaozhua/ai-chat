# ========== 通用 ==========
-keepattributes *Annotation*, Signature, Serializable, InnerClasses, EnclosingMethod
-dontwarn **
-verbose

# ========== kotlinx.serialization ==========
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.example.aichat.data.remote.dto.** { *; }

# ========== Room ==========
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.example.aichat.data.local.db.** { *; }
-keep class com.example.aichat.data.model.** { *; }

# ========== Hilt / Dagger ==========
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * { @javax.inject.Inject <init>(...); }

# ========== OkHttp ==========
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ========== kotlinx.coroutines ==========
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile **;
}

# ========== AndroidX Compose ==========
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ========== DataStore ==========
-keep class androidx.datastore.** { *; }

# ========== encryptedprefs ==========
-keep class dev.spght.encryptedprefs.** { *; }
-keep class com.google.crypto.tink.** { *; }

# ========== 移除日志（Release） ==========
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-assumenosideeffects class okhttp3.logging.HttpLoggingInterceptor {
    public void setLevel(okhttp3.logging.HttpLoggingInterceptor$Level);
}
