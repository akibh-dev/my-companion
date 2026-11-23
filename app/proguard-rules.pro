# R8 rules for the release build (minify + resource shrinking; Docs/23-release-1.0-plan.md).
#
# AndroidX, Compose, Hilt, Room, Media3, OkHttp, coroutines and Readium ship their own consumer rules,
# so only libraries that load classes by name (JNI, reflection, scripting) need rules here. The app's
# own JSON uses org.json, which is part of Android, so nothing of ours is reflected on.

# Readable stack traces in the app's log and crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ONNX Runtime (voice transliteration): the native library creates and calls these Java classes by name.
-keep class ai.onnxruntime.** { *; }

# NewPipeExtractor (YouTube player): Rhino runs YouTube's player JavaScript and reflects on its own
# classes; the "time ago" parsers are looked up by name per language.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.schabi.newpipe.extractor.services.youtube.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# PDF pages (library reader): PdfiumCore's native code calls back into these classes.
-keep class com.shockwave.pdfium.** { *; }
