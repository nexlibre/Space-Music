"""Verify first-open notes, acknowledgement and offline downloaded-note persistence."""
import re
import sys
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
PACKAGE = 'org.nexlibre.spacemusic'

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=40)

def start():
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE+'/com.music.bitchord.MainActivity')

def tree():
    adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/notes.xml')
    raw = adb('exec-out', 'cat', '/sdcard/notes.xml')
    Path('device-notes-ui.xml').write_text(raw)
    return ET.fromstring(raw)

def find(text):
    for _ in range(12):
        for n in tree().iter('node'):
            if text in n.get('text', ''): return n
        time.sleep(1)
    raise AssertionError('Missing new-version text: '+text)

def dismiss(expected_code=8):
    b = find('Back')
    l,t,r,b = map(int, re.findall(r'\d+', b.get('bounds')))
    adb('shell', 'input', 'tap', str((l+r)//2), str((t+b)//2))
    # ADB returns before Compose dispatches the input. Wait for the actual UI
    # dismissal and completed preference write before killing its process.
    for _ in range(12):
        visible = any('What is new in Space Music' in n.get('text','') for n in tree().iter('node'))
        try:
            raw = adb('exec-out', 'cat', '/data/user/0/'+PACKAGE+'/shared_prefs/space_updates.xml')
            Path('device-notes-preferences.xml').write_text(raw)
            saved = any(e.tag == 'int' and e.get('name') == 'seen_version' and e.get('value') == str(expected_code) for e in ET.fromstring(raw))
        except (subprocess.CalledProcessError, ET.ParseError):
            saved = False
        if not visible and saved: return
        time.sleep(0.5)
    raise AssertionError('Acknowledgement did not close the dialog and persist seen_version')

if len(sys.argv) > 1 and sys.argv[1] == 'previous':
    start()
    find('What is new in Space Music 2.3.0')
    dismiss(7)
    raise SystemExit(0)

start()
find('What is new in Space Music 2.4.0')
find('Three spatial modes.')
Path('device-whatsnew.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p'], timeout=30))
dismiss()
start()
assert not any('What is new in Space Music' in n.get('text','') for n in tree().iter('node'))
# Simulate the notes written by the completed download before installation.
adb('shell','am','force-stop',PACKAGE)
path = '/data/user/0/'+PACKAGE+'/shared_prefs/space_updates.xml'
xml = '<map><int name="seen_version" value="7"/><string name="pending_version">2.4.0</string><string name="pending_notes">Downloaded release notes fixture</string></map>'
subprocess.run(['adb','shell','tee',path], input=xml.encode(), stdout=subprocess.DEVNULL, check=True, timeout=30)
owner=adb('shell','stat','-c','%u:%g','/data/user/0/'+PACKAGE).strip()
assert re.fullmatch(r'\d+:\d+',owner)
adb('shell','chown',owner,path)
adb('shell','chmod','600',path)
adb('shell','rm','-f',path+'.bak')
start()
find('Downloaded release notes fixture')
dismiss()
start()
assert not any('What is new in Space Music' in n.get('text','') for n in tree().iter('node'))
print('PASS: first-open stable notes, once-per-version acknowledgement and offline installed release notes')
