-keepattributes Signature
-keepattributes *Annotation*
-keep class com.kismet.mobile.model.** { *; }
-keep class com.kismet.mobile.api.WigleUploadResult { *; }
-keep class retrofit2.** { *; }
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn sun.security.x509.**
-dontwarn java.awt.**
-dontwarn javax.security.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class net.schmizz.** { *; }
-dontwarn net.schmizz.**
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**
