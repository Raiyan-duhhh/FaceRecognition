# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Protect data models from R8 obfuscation to ensure Firebase and Room serialization works
-keep class com.example.facerecognition.data.entity.Staff { *; }
-keep class com.example.facerecognition.data.entity.FaceVector { *; }
-keep class com.example.facerecognition.data.entity.AttendanceLog { *; }
-keep class com.example.facerecognition.data.local.OfflineAttendanceLog { *; }