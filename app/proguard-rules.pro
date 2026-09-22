# R8 rules for the release build.

# --- Gson -------------------------------------------------------------------
# Gson instantiates the DTOs reflectively from field names, so their names and
# fields must survive shrinking or every API response would parse as nulls.
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.sanchit.contestpilot.data.remote.**.*Dto { *; }
-keep class com.sanchit.contestpilot.data.remote.**.*Response { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# TypeToken subclasses carry the generic type Gson needs at runtime.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- Retrofit ---------------------------------------------------------------
# Retrofit builds its implementations from the interface's annotations and
# generic return types.
-keep,allowobfuscation interface com.sanchit.contestpilot.data.remote.**
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# --- OkHttp -----------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Room -------------------------------------------------------------------
# Room's generated implementations are found by name at runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- App entry points -------------------------------------------------------
# Receivers and workers are instantiated by the system from the manifest, so R8
# cannot see the references.
-keep class com.sanchit.contestpilot.notification.ContestAlarmReceiver { <init>(); }
-keep class com.sanchit.contestpilot.notification.SystemEventReceiver { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
