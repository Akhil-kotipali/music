# ProGuard rules for AudioIndexer

# App Data models & Room Entities
-keep class com.example.audioindexer.data.model.** { *; }
-keep class com.example.audioindexer.data.dao.** { *; }
-keep class com.example.audioindexer.data.db.** { *; }
-keep class com.example.audioindexer.playback.** { *; }
-keep class com.example.audioindexer.service.** { *; }
-keep class com.example.audioindexer.util.** { *; }

# Room SQLite runtime
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.**

# OkHttp & JSON parsing
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
