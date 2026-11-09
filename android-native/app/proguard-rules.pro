# Réduction prudente : on supprime seulement le code inutilisé, sans renommer ni optimiser.
-dontobfuscate
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Application
-keep class ma.carnet.inspection.natif.** { *; }

# Bibliothèques appelées par réflexion ou par du code natif
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-dontwarn javax.annotation.**
-dontwarn com.google.re2j.**
-dontwarn com.google.auto.value.**
-dontwarn org.checkerframework.**
-keepclasseswithmembernames class * { native <methods>; }
