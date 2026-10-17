#!/usr/bin/env bash
set -euo pipefail
trap 'timeout 10s adb logcat -d -b crash > device-crash-log.txt 2>&1 || true; timeout 10s adb logcat -d > device-full-log.txt 2>&1 || true; free -h; sudo dmesg | tail -60' EXIT
apk=${TEST_APP_APK:-$(find built -name '*prod*x86_64*debug.apk' -o -name '*prod*universal*debug.apk' | head -1)}
if [ -z "$apk" ]; then apk=$(find built -path '*/prod/debug/*' -name '*universal*.apk' | head -1); fi
test_apk=${TEST_INSTRUMENTATION_APK:-$(find built -path '*/androidTest/prod/debug/*' -name '*.apk' | head -1)}
test -n "$apk"
test -n "$test_apk"
# google_apis test images allow root. API 26's shell user cannot toggle Wi-Fi;
# svc otherwise throws SecurityException before instrumentation even starts.
for attempt in 1 2 3; do
  timeout 30s adb root || true
  timeout 30s adb wait-for-device
  if [ "$(timeout 15s adb shell id -u | tr -d '\r')" = 0 ]; then break; fi
done
test "$(timeout 15s adb shell id -u | tr -d '\r')" = 0
timeout 180s adb install -r --no-incremental "$apk"
timeout 180s adb install -r --no-incremental "$test_apk"
adb shell svc wifi disable
adb shell svc data disable
timeout 180s adb shell am instrument -w -r -e class com.music.bitchord.DevicePlaylistStoreTest,com.music.bitchord.OfflinePlaylistPlaybackTest,com.music.bitchord.ExampleInstrumentedTest,com.music.bitchord.LocalPlaybackServiceTest,com.music.bitchord.LocalLyricsStoreTest,com.music.bitchord.SpatialPreferencesTest,com.music.bitchord.SpatialRuntimeTest org.nexlibre.spacemusic.test/androidx.test.runner.AndroidJUnitRunner | tee device-tests.txt
grep -q 'OK (' device-tests.txt
! grep -q 'FAILURES\|INSTRUMENTATION_FAILED\|shortMsg=' device-tests.txt
timeout 15s adb pull /sdcard/Android/data/org.nexlibre.spacemusic/files/playback-integration.png device-playback.png
timeout 15s adb shell svc wifi enable
timeout 15s adb shell svc data enable
(timeout 90s adb logcat -v threadtime > device-startup-log.txt 2>&1) &
startup_logger=$!
trap 'kill "$startup_logger" 2>/dev/null || true; timeout 10s adb logcat -d -b crash > device-crash-log.txt 2>&1 || true; timeout 10s adb logcat -d > device-full-log.txt 2>&1 || true; free -h; sudo dmesg | tail -60' EXIT
timeout 60s adb shell am start -W -n org.nexlibre.spacemusic/com.music.bitchord.MainActivity
sleep 12
timeout 15s adb logcat -d -b crash > device-crash-log.txt
! grep -q 'FATAL EXCEPTION' device-crash-log.txt
timeout 15s adb shell pidof org.nexlibre.spacemusic

# Repeat process creation rather than only recreating an Activity in one process.
for attempt in $(seq 1 20); do
  timeout 15s adb shell am force-stop org.nexlibre.spacemusic
  timeout 60s adb shell am start -W -n org.nexlibre.spacemusic/com.music.bitchord.MainActivity
  sleep 1
  timeout 15s adb shell pidof org.nexlibre.spacemusic
done
timeout 15s adb logcat -d -b crash > device-crash-log.txt
! grep -q 'FATAL EXCEPTION' device-crash-log.txt

python3 scripts/test-library-navigation.py

python3 scripts/test-spatial-ui.py
