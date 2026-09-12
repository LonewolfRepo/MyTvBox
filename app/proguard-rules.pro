# ============================================================================
# GENERAL
# ============================================================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ============================================================================
# RETROFIT & OKHTTP
# ============================================================================
# Retrofit does reflection on interfaces and annotations.
-keep,allowobfuscation,allowshrinking interface * extends retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ============================================================================
# KOTLINX SERIALIZATION
# ============================================================================
# Keep the generated serializers and the @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep your specific app models (Replace com.itv.blockbuster with your package if different)
-keep,includedescriptorclasses class com.itv.blockbuster.**$$serializer { *; }
-keepclassmembers class com.itv.blockbuster.** {
    *** Companion;
}
-keepclasseswithmembers class com.itv.blockbuster.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================================================
# COROUTINES
# ============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
