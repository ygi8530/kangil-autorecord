#!/usr/bin/env bash
# V1/V2: device info + public (exported) interfaces of Samsung Voice Recorder on the watch.
# Read-only: inspects package metadata only. Usage: scripts/verify_samsung_recorder.sh [adb-serial]
set -u
# SDK: $ANDROID_HOME, else Linux default, else Windows default (Git Bash)
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
[ -d "$SDK" ] || SDK="$HOME/AppData/Local/Android/Sdk"
ADB=("$SDK/platform-tools/adb" ${1:+-s "$1"})
AAPT2=$(ls -d "$SDK"/build-tools/*/aapt2* | tail -1)
OUT=docs/raw; mkdir -p "$OUT"

echo "== V1 device"
for p in ro.product.model ro.build.version.release ro.build.version.sdk ro.build.display.id ro.build.version.oneui; do
  echo "$p=$("${ADB[@]}" shell getprop $p)"
done

echo "== V2 candidate packages"
"${ADB[@]}" shell pm list packages | grep -i -E "voice|record|sound" | tee "$OUT/packages.txt"

for pkg in $(sed 's/package://' "$OUT/packages.txt"); do
  echo "== $pkg"
  "${ADB[@]}" shell dumpsys package "$pkg" > "$OUT/dumpsys_$pkg.txt"
  grep -E "versionName|targetSdk" "$OUT/dumpsys_$pkg.txt" | head -3
  apk=$("${ADB[@]}" shell pm path "$pkg" | head -1 | sed 's/package://' | tr -d '\r')
  [ -n "$apk" ] && "${ADB[@]}" pull "$apk" "$OUT/$pkg.apk" >/dev/null && \
    "$AAPT2" dump xmltree --file AndroidManifest.xml "$OUT/$pkg.apk" > "$OUT/manifest_$pkg.txt" 2>&1
  unzip -l "$OUT/$pkg.apk" 2>/dev/null | grep -i -E "shortcuts|actions|capabilit" || true
done

echo "== handlers of standard RECORD_SOUND intent"
"${ADB[@]}" shell cmd package query-activities -a android.provider.MediaStore.RECORD_SOUND
