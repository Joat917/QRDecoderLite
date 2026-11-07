-keep class com.google.mlkit.vision.barcode.** { public *; }
-keep class com.google.mlkit.vision.common.** { public *; }
-keep class com.google.mlkit.common.sdkinternal.** { public *; }
-keep class com.google.mlkit.common.internal.** { public *; }
-keepclassmembers class org.opencv.** {
    <fields>;
}

#-keepnames class org.opencv.** { *; }
#-keep class org.opencv.** { *; }

# ── 混淆警告 ──
# 来自 build/outputs/mapping/<variant>/missing_rules.txt
-dontwarn javax.lang.model.element.Modifier
