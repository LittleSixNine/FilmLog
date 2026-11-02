#!/bin/sh
set -eu
: "${KEYSTORE_PASSWORD:?Set the signing password in the environment}"
: "${JAVA_HOME:?}" "${BUILD_TOOLS:?}" "${ANDROID_JAR:?}" "${KEYSTORE:?}" "${TEST_BUILD:?}"
SOURCE=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$TEST_BUILD/classes" "$TEST_BUILD/dex" "$TEST_BUILD/generated"
cat > "$TEST_BUILD/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="cn.sixnine.filmlog.tests"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="FilmLog isolated tests"/><instrumentation android:name="cn.sixnine.filmlog.StoreInstrumentation" android:targetPackage="cn.sixnine.filmlog.tests"/></manifest>
XML
"$BUILD_TOOLS/aapt2" compile --dir "$SOURCE/res" -o "$TEST_BUILD/resources.zip"
"$BUILD_TOOLS/aapt2" link --custom-package cn.sixnine.filmlog --java "$TEST_BUILD/generated" -o "$TEST_BUILD/unsigned.apk" -I "$ANDROID_JAR" --manifest "$TEST_BUILD/AndroidManifest.xml" -A "$SOURCE/assets" "$TEST_BUILD/resources.zip"
find "$SOURCE/src" "$TEST_BUILD/generated" -name '*.java' > "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/StoreInstrumentation.java" >> "$TEST_BUILD/sources.txt"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR:$SOURCE/libs/*" -d "$TEST_BUILD/classes" @"$TEST_BUILD/sources.txt"
"$JAVA_HOME/bin/jar" cf "$TEST_BUILD/classes.jar" -C "$TEST_BUILD/classes" .
"$JAVA_HOME/bin/java" -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$ANDROID_JAR" --output "$TEST_BUILD/dex" "$TEST_BUILD/classes.jar" "$SOURCE"/libs/*.jar
"$JAVA_HOME/bin/jar" uf "$TEST_BUILD/unsigned.apk" -C "$TEST_BUILD/dex" classes.dex
"$BUILD_TOOLS/zipalign" -f 4 "$TEST_BUILD/unsigned.apk" "$TEST_BUILD/aligned.apk"
"$JAVA_HOME/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" sign --ks "$KEYSTORE" --ks-pass env:KEYSTORE_PASSWORD --out "$TEST_BUILD/tests.apk" "$TEST_BUILD/aligned.apk"
