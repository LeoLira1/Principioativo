# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class br.com.camda.principioativo.**$$serializer { *; }
-keepclassmembers class br.com.camda.principioativo.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# Tink (security-crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
