# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep class names of all classes for easy debugging (and fix navigation route checking)
-keepnames class dev.pschmitt.netboxandchill.** { *; }

# kotlinx.serialization: keep serializers for our DTOs (they're reflectively looked up)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class dev.pschmitt.netboxandchill.data.api.dto.** {
    *** Companion;
}
-keepclasseswithmembers class dev.pschmitt.netboxandchill.data.api.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# These classes are from okhttp and are not used in Android
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.*
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Google Tink (pulled in by androidx.security.crypto for EncryptedSharedPreferences) references
# these build-time-only annotation packages; none of them are needed at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.joda.time.**
