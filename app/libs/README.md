# 固定的照片元数据依赖

用于统一 Android 8 及以上的 EXIF 读写行为。APK 打包下列 JAR；构建不联网。

- AndroidX ExifInterface 1.4.2：Google Maven 官方 AAR 中的 classes.jar；Apache-2.0。
- AndroidX annotation-jvm 1.8.1：Google Maven；注解依赖。
- JSpecify 1.0.0：Maven Central；注解依赖。

官方下载：
https://dl.google.com/dl/android/maven2/androidx/exifinterface/exifinterface/1.4.2/exifinterface-1.4.2.aar
https://dl.google.com/dl/android/maven2/androidx/annotation/annotation-jvm/1.8.1/annotation-jvm-1.8.1.jar
https://repo.maven.apache.org/maven2/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar

SHA-256：

9aab326d9492800991854360ac248f493ce7f7c3183519309b78ace9e240f6f6  annotation-jvm-1.8.1.jar
7901cad6eb4f77b2c5fe605adac27b90126bfd9336205098499f831091790224  exifinterface-1.4.2.jar
1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab  jspecify-1.0.0.jar
