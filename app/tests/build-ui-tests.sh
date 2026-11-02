#!/bin/sh
set -eu
: "${TEST_BUILD:?Set a separate UI verification build directory}"
SOURCE=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
python3 - "$SOURCE" "$TEST_BUILD" <<'PY'
from pathlib import Path
import sys
source,destination=map(lambda p:Path(p).resolve(),sys.argv[1:])
if destination==source or source in destination.parents:raise SystemExit("UI test build must be outside production source")
PY
mkdir -p "$TEST_BUILD"
# Production source stays untouched. The test identity has its own data and launcher entry.
if [ -e "$TEST_BUILD/source" ] && [ ! -f "$TEST_BUILD/.filmlog-ui-build" ]; then
 echo "Refusing to replace a directory not created by this UI test builder" >&2
 exit 1
fi
touch "$TEST_BUILD/.filmlog-ui-build"
rm -rf "$TEST_BUILD/source"
cp -R "$SOURCE" "$TEST_BUILD/source"
cp "$SOURCE/tests/ContactSheetInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/ContactSheetInstrumentation.java"
cp "$SOURCE/tests/RollWizardInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/RollWizardInstrumentation.java"
cp "$SOURCE/tests/LocatedRecordInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/LocatedRecordInstrumentation.java"
cp "$SOURCE/tests/HomeLocationInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/HomeLocationInstrumentation.java"
cp "$SOURCE/tests/ToolsUiInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/ToolsUiInstrumentation.java"
cp "$SOURCE/tests/CameraEditorInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/CameraEditorInstrumentation.java"
cp "$SOURCE/tests/RollFlowUiInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/RollFlowUiInstrumentation.java"
cp "$SOURCE/tests/PrimaryNavigationInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/PrimaryNavigationInstrumentation.java"
cp "$SOURCE/tests/RollSectionsInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/RollSectionsInstrumentation.java"
cp "$SOURCE/tests/PendingRecoveryInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/PendingRecoveryInstrumentation.java"
cp "$SOURCE/tests/ExposureEditorInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/ExposureEditorInstrumentation.java"
cp "$SOURCE/tests/RollSearchInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/RollSearchInstrumentation.java"
cp "$SOURCE/tests/LibraryPagesInstrumentation.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/LibraryPagesInstrumentation.java"
for test in RollFixesInstrumentation WidgetFrostInstrumentation WidgetResponsiveInstrumentation SharePosterPerformanceInstrumentation SharePosterInstrumentation SharePosterOutputInstrumentation SharePosterUiInstrumentation MeterModesInstrumentation AlignmentInstrumentation WidgetHalfFrameInstrumentation PerformanceInstrumentation PerformanceRegressionInstrumentation ReferenceSaveInstrumentation NavigationFeedbackInstrumentation; do
 cp "$SOURCE/tests/$test.java" "$TEST_BUILD/source/src/cn/sixnine/filmlog/$test.java"
done
python3 - "$TEST_BUILD/source" <<'PY'
from pathlib import Path
import sys
source=Path(sys.argv[1]);p=source/'AndroidManifest.xml';text=p.read_text().replace('package="cn.sixnine.filmlog"','package="cn.sixnine.filmlog.ui.tests"').replace('android:name=".','android:name="cn.sixnine.filmlog.').replace('android:label="胶片随记"','android:debuggable="true" android:label="胶片随记·界面核验"').replace('</manifest>','<instrumentation android:name="cn.sixnine.filmlog.ContactSheetInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>');text=text.replace('</application>', '<activity android:name="cn.sixnine.filmlog.RollWizardInstrumentation$Host" android:exported="false"/></application>').replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.RollWizardInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>');text=text.replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.HomeLocationInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>');text=text.replace('</application>', '<activity android:name="cn.sixnine.filmlog.ToolsUiInstrumentation$Host" android:exported="false"/></application>').replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.ToolsUiInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>');p.write_text(text)
p=source/'AndroidManifest.xml';p.write_text(p.read_text().replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.CameraEditorInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>'))
p=source/'AndroidManifest.xml';p.write_text(p.read_text().replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.RollFlowUiInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>'))
p=source/'AndroidManifest.xml';p.write_text(p.read_text().replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.PrimaryNavigationInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>'))
p=source/'AndroidManifest.xml';p.write_text(p.read_text().replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.ExposureEditorInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>'))
p=source/'AndroidManifest.xml';p.write_text(p.read_text().replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.PendingRecoveryInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>'))
p=source/'AndroidManifest.xml';p.write_text(p.read_text().replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.RollSectionsInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>'))
p=source/'AndroidManifest.xml';p.write_text(p.read_text().replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.RollSearchInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>'))
p=source/'AndroidManifest.xml';p.write_text(p.read_text().replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.LibraryPagesInstrumentation" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>'))
p=source/'AndroidManifest.xml'
for name in ['RollFixesInstrumentation','WidgetFrostInstrumentation','WidgetResponsiveInstrumentation','SharePosterPerformanceInstrumentation','SharePosterInstrumentation','SharePosterOutputInstrumentation','SharePosterUiInstrumentation','MeterModesInstrumentation','AlignmentInstrumentation','WidgetHalfFrameInstrumentation','PerformanceInstrumentation','PerformanceRegressionInstrumentation','ReferenceSaveInstrumentation','NavigationFeedbackInstrumentation']:
 p.write_text(p.read_text().replace('</manifest>', '<instrumentation android:name="cn.sixnine.filmlog.'+name+'" android:targetPackage="cn.sixnine.filmlog.ui.tests"/></manifest>'))
p.write_text(p.read_text().replace('</application>', '<provider android:name="cn.sixnine.filmlog.SharePosterOutputInstrumentation$Provider" android:authorities="cn.sixnine.filmlog.ui.tests.poster.documents" android:exported="true" android:permission="android.permission.MANAGE_DOCUMENTS" android:grantUriPermissions="true"><intent-filter><action android:name="android.content.action.DOCUMENTS_PROVIDER"/></intent-filter></provider></application>'))
p.write_text(p.read_text().replace('</application>', '<activity android:name="cn.sixnine.filmlog.MeterModesInstrumentation$Host" android:exported="false"/><activity android:name="cn.sixnine.filmlog.ReferenceSaveInstrumentation$Host" android:exported="false"/></application>'))
p=source/'build.sh';p.write_text(p.read_text().replace('"$BUILD_TOOLS/aapt2" link','"$BUILD_TOOLS/aapt2" link --custom-package cn.sixnine.filmlog'))
PY
BUILD_DIR="$TEST_BUILD/build" sh "$TEST_BUILD/source/build.sh"
