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

# ONNX Runtime — keep all classes
-keep class ai.onnxruntime.** { *; }
-keep enum ai.onnxruntime.** { *; }
-keep interface ai.onnxruntime.** { *; }

# Сохраняем все классы ONNX, даже если не видно использования
-keepclassmembers class ai.onnxruntime.** {
    *;
}

# Не удалять поля и методы, используемые через JNI
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Сохраняем все OnnxValue и TensorInfo
-keep class ai.onnxruntime.OnnxValue { *; }
-keep class ai.onnxruntime.TensorInfo { *; }
-keep class ai.onnxruntime.OrtSession { *; }
-keep class ai.onnxruntime.OrtEnvironment { *; }