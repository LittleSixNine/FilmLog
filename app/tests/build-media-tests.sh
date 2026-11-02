#!/bin/sh
set -eu
: "${KEYSTORE_PASSWORD:?Set the signing password in the environment}"
: "${JAVA_HOME:?}" "${BUILD_TOOLS:?}" "${ANDROID_JAR:?}" "${KEYSTORE:?}" "${TEST_BUILD:?}"
SOURCE=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$TEST_BUILD/classes" "$TEST_BUILD/dex" "$TEST_BUILD/generated"
find "$TEST_BUILD/classes" "$TEST_BUILD/dex" -type f \( -name '*.class' -o -name '*.dex' \) -delete
cat > "$TEST_BUILD/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="cn.sixnine.filmlog.media.tests"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><uses-permission android:name="android.permission.POST_NOTIFICATIONS"/><application android:name="cn.sixnine.filmlog.FilmApplication" android:debuggable="true" android:label="FilmLog isolated tests"><receiver android:name="cn.sixnine.filmlog.ReminderReceiver" android:exported="false"/></application><instrumentation android:name="cn.sixnine.filmlog.MediaInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.RecoveryInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.ReminderInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.WorkspaceInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.LegacyRecordsInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.ImportPreviewInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.CatalogInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.WidgetInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.ContactSheetAccessibilityInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.ReferencePhotosInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.FilmStockInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.TimerRecoveryInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.ExposurePresetsInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.PresetArtworkInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.StatsInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.ProjectInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.CsvDraftInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.OriginalCsvInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.PresentationInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.RollUiInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.LocatedRecordInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.CameraLogoInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.HomeCInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/><instrumentation android:name="cn.sixnine.filmlog.RollLifecycleInstrumentation" android:targetPackage="cn.sixnine.filmlog.media.tests"/></manifest>
XML
"$BUILD_TOOLS/aapt2" compile --dir "$SOURCE/res" -o "$TEST_BUILD/resources.zip"
"$BUILD_TOOLS/aapt2" link --custom-package cn.sixnine.filmlog --java "$TEST_BUILD/generated" -o "$TEST_BUILD/unsigned.apk" -I "$ANDROID_JAR" --manifest "$TEST_BUILD/AndroidManifest.xml" -A "$SOURCE/assets" -A "$SOURCE/tests/fixtures" "$TEST_BUILD/resources.zip"
find "$SOURCE/src" "$TEST_BUILD/generated" -name '*.java' > "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/MediaInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/TimerRecoveryInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/RecoveryInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/ReminderInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/WorkspaceInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/LegacyRecordsInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/ImportPreviewInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/CatalogInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/ProjectInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/WidgetInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/ContactSheetAccessibilityInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/ReferencePhotosInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/FilmStockInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/ExposurePresetsInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/PresetArtworkInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/StatsInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/CsvDraftInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/OriginalCsvInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/PresentationInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/RollUiInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/LocatedRecordInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/CameraLogoInstrumentation.java" "$SOURCE/tests/HomeCInstrumentation.java" >> "$TEST_BUILD/sources.txt"
printf '%s\n' "$SOURCE/tests/RollLifecycleInstrumentation.java" >> "$TEST_BUILD/sources.txt"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR:$SOURCE/libs/*" -d "$TEST_BUILD/classes" @"$TEST_BUILD/sources.txt"
"$JAVA_HOME/bin/jar" cf "$TEST_BUILD/classes.jar" -C "$TEST_BUILD/classes" .
"$JAVA_HOME/bin/java" -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$ANDROID_JAR" --output "$TEST_BUILD/dex" "$TEST_BUILD/classes.jar" "$SOURCE"/libs/*.jar
"$JAVA_HOME/bin/jar" uf "$TEST_BUILD/unsigned.apk" -C "$TEST_BUILD/dex" classes.dex
"$BUILD_TOOLS/zipalign" -f 4 "$TEST_BUILD/unsigned.apk" "$TEST_BUILD/aligned.apk"
"$JAVA_HOME/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" sign --ks "$KEYSTORE" --ks-pass env:KEYSTORE_PASSWORD --out "$TEST_BUILD/tests.apk" "$TEST_BUILD/aligned.apk"
