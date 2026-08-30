-keep class com.project.lol.bridge.SpotifyBridge { *; }
-keep class com.project.lol.webview.SpotifyWebViewClient { *; }
-keep class com.project.lol.webview.SpotifyWebChromeClient { *; }
-keep class com.project.lol.webview.injections.** { *; }
-keep class com.project.lol.webview.helpers.** { *; }
-keep class com.project.lol.service.MediaNotificationService { *; }
-keep class com.project.lol.proxy.LocalProxyManager { *; }
-keep class com.project.lol.ui.SplashActivity { *; }
-keep class com.project.lol.ui.MainActivity { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.concurrent.GuardedBy
-keepclassmembers enum * { *; }
-keepclassmembers class * implements java.io.Serializable { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepattributes *Annotation*,JavascriptInterface,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Clear all Log.* to gain performance
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Obfuscation
-repackageclasses