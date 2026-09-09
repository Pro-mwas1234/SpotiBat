-keep class com.mwask.bat.bridge.SpotifyBridge { *; }
-keep class com.mwask.bat.webview.SpotifyWebViewClient { *; }
-keep class com.mwask.bat.webview.SpotifyWebChromeClient { *; }
-keep class com.mwask.bat.webview.injections.** { *; }
-keep class com.mwask.bat.webview.helpers.** { *; }
-keep class com.mwask.bat.service.MediaNotificationService { *; }
-keep class com.mwask.bat.proxy.LocalProxyManager { *; }
-keep class com.mwask.bat.ui.SplashActivity { *; }
-keep class com.mwask.bat.ui.MainActivity { *; }
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

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.protos.** { *; }
-keep class ** extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class ** extends com.google.protobuf.GeneratedMessage { *; }
-keepclassmembers class ** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class ** extends com.google.protobuf.GeneratedMessage {
    <fields>;
}

# NewPipe / Rhino - java.beans.* not available on Android on device
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.script.**
# do not add -keep class org.mozilla.javascript.** - it pulls in classes
# referencing java.beans.* which breaks ART install-time verification
