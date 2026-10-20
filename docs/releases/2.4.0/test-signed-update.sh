#!/usr/bin/env bash
set -euo pipefail
trap 'timeout 15s adb logcat -d > device-full-log.txt 2>&1 || true; timeout 15s adb logcat -d -b crash > device-crash-log.txt 2>&1 || true' EXIT
# adbd can close its transport while restarting as root; verify readiness.
for attempt in 1 2 3; do
  timeout 30s adb root || true
  timeout 30s adb wait-for-device
  if [ "$(timeout 15s adb shell id -u | tr -d '\r')" = 0 ]; then break; fi
done
test "$(timeout 15s adb shell id -u | tr -d '\r')" = 0
timeout 180s adb install -g --no-incremental previous/Space-Music-2.3.0.apk
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$sdk" -ge 33 ]; then
  adb shell pm grant org.nexlibre.spacemusic android.permission.READ_MEDIA_AUDIO
  adb shell appops set org.nexlibre.spacemusic READ_MEDIA_AUDIO allow
else
  adb shell pm grant org.nexlibre.spacemusic android.permission.READ_EXTERNAL_STORAGE
fi
adb shell dumpsys package org.nexlibre.spacemusic > device-package-fixture.txt
adb shell appops get org.nexlibre.spacemusic >> device-package-fixture.txt
python3 - <<'PY_FIXTURE'
import base64
from pathlib import Path
encoded = Path('docs/releases/1.0.1/upgrade-tone.flac.b64').read_bytes()
for suffix in ['A', 'B']:
    Path('SpaceUpgradeTone' + suffix + '.flac').write_bytes(base64.b64decode(encoded))
PY_FIXTURE
adb shell mkdir -p /storage/emulated/0/Music
for suffix in A B; do
  adb push "SpaceUpgradeTone$suffix.flac" "/storage/emulated/0/Music/SpaceUpgradeTone$suffix.flac"
done
python3 - <<'PY_MEDIASTORE'
import subprocess
from pathlib import Path

def shell(*args):
    output = subprocess.check_output(['adb', 'shell', *args], text=True, timeout=30)
    if 'Error while accessing' in output or 'Exception:' in output:
        raise RuntimeError(output)
    return output

sdk = int(shell('getprop', 'ro.build.version.sdk').strip())
collection = 'content://media/' + ('external_primary' if sdk >= 29 else 'external') + '/audio/media'
for suffix in ['A', 'B']:
    track = 'SpaceUpgradeTone' + suffix
    values = ['_data:s:/storage/emulated/0/Music/' + track + '.flac', '_display_name:s:' + track + '.flac',
              'title:s:' + track, 'mime_type:s:audio/flac', 'duration:l:31000', 'is_music:i:1',
              'is_alarm:i:0', 'is_notification:i:0', 'is_ringtone:i:0', 'artist:s:Test', 'album:s:SpaceUpgrade']
    if sdk >= 29: values += ['is_podcast:i:0', 'is_pending:i:0']
    if sdk >= 30: values += ['is_trashed:i:0']
    arguments = ['content', 'insert', '--uri', collection]
    for value in values: arguments += ['--bind', value]
    shell(*arguments)
projection = '_id:title:duration:is_music:is_alarm:is_notification:is_ringtone:_data'
if sdk >= 29: projection += ':is_podcast:is_pending'
if sdk >= 30: projection += ':is_trashed'
rows = shell('content', 'query', '--uri', collection, '--projection', projection)
Path('device-media-fixture.txt').write_text(rows)
assert 'SpaceUpgradeToneA' in rows and 'SpaceUpgradeToneB' in rows, rows
if sdk >= 29: assert 'is_pending=0' in rows and 'is_podcast=0' in rows, rows
if sdk >= 30: assert 'is_trashed=0' in rows, rows
print(rows)
PY_MEDIASTORE
python3 docs/releases/2.4.0/verify-whatsnew.py previous
python3 docs/releases/2.3.0/verify-upgrade-playlist.py seed
adb shell svc wifi disable
adb shell svc data disable
adb install -r --no-incremental dist/Space-Music-2.4.0.apk
# Android 12 delivers package-replacement broadcasts after adb install returns.
# Let PackageManager finish its own force-stop before starting the upgraded app.
sleep 3
python3 docs/releases/2.4.0/verify-whatsnew.py
python3 docs/releases/2.3.0/verify-upgrade-playlist.py verify
python3 docs/releases/2.3.0/verify-upgrade-playlist.py capture_dark
python3 docs/releases/2.3.0/verify-upgrade-playlist.py capture_light
python3 docs/releases/2.3.0/verify-upgrade-playlist.py capture_glass

TEST_APP_APK=dist/Space-Music-2.4.0.apk TEST_INSTRUMENTATION_APK=dist/test-runner.apk bash scripts/test-device.sh

