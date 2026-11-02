#!/bin/sh
set -eu
: "${JAVA_HOME:?Set JAVA_HOME to a JDK directory}"
: "${BUILD_TOOLS:?Set BUILD_TOOLS to Android Build Tools 35.0.0 directory}"
: "${ANDROID_JAR:?Set ANDROID_JAR to Android 35 android.jar}"
: "${KEYSTORE:?Set KEYSTORE to an existing signing keystore}"
: "${KEYSTORE_PASSWORD:?Set the signing password in the environment}"
[ -f "$KEYSTORE" ] || { echo "Signing keystore is missing; refusing to create a replacement." >&2; exit 1; }
SOURCE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_DIR=${BUILD_DIR:-"$SOURCE/build"}
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/generated" "$BUILD_DIR/test"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -d "$BUILD_DIR/test" "$SOURCE/src/cn/sixnine/filmlog/RecordRules.java" "$SOURCE/tests/RecordRulesTest.java" "$SOURCE/src/cn/sixnine/filmlog/MemoTags.java" "$SOURCE/tests/MemoTagsTest.java"
"$JAVA_HOME/bin/java" -cp "$BUILD_DIR/test" RecordRulesTest
"$JAVA_HOME/bin/java" -cp "$BUILD_DIR/test" cn.sixnine.filmlog.MemoTagsTest
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -d "$BUILD_DIR/test" "$SOURCE/src/cn/sixnine/filmlog/PhotoMath.java" "$SOURCE/src/cn/sixnine/filmlog/ExposureChoices.java" "$SOURCE/tests/ExposureChoicesTest.java" "$SOURCE/src/cn/sixnine/filmlog/MeterGeometry.java" "$SOURCE/src/cn/sixnine/filmlog/TimerState.java" "$SOURCE/tests/PhotoMathTest.java" "$SOURCE/tests/MeterGeometryTest.java" "$SOURCE/tests/TimerStateTest.java"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -cp "$BUILD_DIR/test" -d "$BUILD_DIR/test" "$SOURCE/src/cn/sixnine/filmlog/MeterReading.java" "$SOURCE/tests/MeterReadingTest.java"
for test in ExposureChoicesTest PhotoMathTest MeterGeometryTest TimerStateTest MeterReadingTest; do
 "$JAVA_HOME/bin/java" -cp "$BUILD_DIR/test" "cn.sixnine.filmlog.$test"
done
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -d "$BUILD_DIR/test" "$SOURCE/src/cn/sixnine/filmlog/SharePosterLayout.java" "$SOURCE/tests/SharePosterLayoutTest.java"
"$JAVA_HOME/bin/java" -cp "$BUILD_DIR/test" cn.sixnine.filmlog.SharePosterLayoutTest
find "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/generated" -type f \( -name '*.class' -o -name '*.dex' -o -name '*.java' \) -delete
"$BUILD_TOOLS/aapt2" compile --dir "$SOURCE/res" -o "$BUILD_DIR/resources.zip"
"$BUILD_TOOLS/aapt2" link -o "$BUILD_DIR/unsigned.apk" -I "$ANDROID_JAR" --manifest "$SOURCE/AndroidManifest.xml" -A "$SOURCE/assets" --java "$BUILD_DIR/generated" "$BUILD_DIR/resources.zip"
find "$SOURCE/src" "$BUILD_DIR/generated" -name '*.java' > "$BUILD_DIR/sources.txt"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR:$SOURCE/libs/*" -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
"$JAVA_HOME/bin/jar" cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
"$JAVA_HOME/bin/java" -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar" "$SOURCE"/libs/*.jar
"$JAVA_HOME/bin/jar" uf "$BUILD_DIR/unsigned.apk" -C "$BUILD_DIR/dex" classes.dex
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"

"$JAVA_HOME/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" sign --ks "$KEYSTORE" --ks-pass env:KEYSTORE_PASSWORD --out "$BUILD_DIR/FilmLog-0.1.39.apk" "$BUILD_DIR/aligned.apk"
"$JAVA_HOME/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" verify --verbose "$BUILD_DIR/FilmLog-0.1.39.apk"
"$BUILD_TOOLS/zipalign" -c 4 "$BUILD_DIR/FilmLog-0.1.39.apk"
