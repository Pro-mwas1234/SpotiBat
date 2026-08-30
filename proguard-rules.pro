# Firebase Analytics
-keepclassmembers class * extends android.app.Application {
    public <init>();
}
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Crashlytics
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

-keep class com.google.firebase.sessions.** { *; }
-keep class com.google.firebase.perf.** { *; }
-keep class com.google.firebase.encoders.** { *; }
-keep class com.google.protos.** { *; }
-keep class ** extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class ** extends com.google.protobuf.GeneratedMessage { *; }
-keepclassmembers class ** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class ** extends com.google.protobuf.GeneratedMessage {
    <fields>;
}

# NewPipe / Rhino (org.mozilla.javascript)
# Rhino references java.beans.* which is not available on Android
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.script.**