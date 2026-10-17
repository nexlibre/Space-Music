"""Check actual rendered bottom tabs, content routing and saved selection."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=30)


def snapshot():
    adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/navigation.xml')
    data = adb('exec-out', 'cat', '/sdcard/navigation.xml')
    Path('device-navigation.xml').write_bytes(data)
    return ET.fromstring(data)


def tab(label, selected=None):
    for _ in range(10):
        root = snapshot()
        parents = {child: parent for parent in root.iter() for child in parent}
        nodes = []
        for child in root.iter('node'):
            if child.get('content-desc') != label:
                continue
            node = child
            # Compose exports the icon label as a child of the selectable tab.
            # The tab owns focus and selected state; UIAutomator may report its
            # clickable attribute as false even though coordinate taps work.
            while node in parents and node.get('focusable') != 'true':
                node = parents[node]
            if node.get('focusable') == 'true':
                nodes.append(node)
        if len(nodes) == 1 and (selected is None or nodes[0].get('selected') == str(selected).lower()):
            return nodes[0]
        time.sleep(1)
    raise AssertionError(f'Expected one reachable tab {label}, selected={selected}; found {nodes}')


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))


def tap(label):
    l, t, r, b = bounds(tab(label))
    adb('shell', 'input', 'tap', str((l+r)//2), str((t+b)//2))
    tab(label, True)


# Tests run after local playback regressions, with permissions already granted.
adb('shell', 'am', 'force-stop', 'org.nexlibre.spacemusic')
adb('shell', 'am', 'start', '-W', '-n', 'org.nexlibre.spacemusic/com.music.bitchord.MainActivity')
for node in snapshot().iter('node'):
    if node.get('text', '').startswith('What is new in Space Music '):
        for button in snapshot().iter('node'):
            if button.get('text') == 'Back':
                l, t, r, b = bounds(button)
                adb('shell', 'input', 'tap', str((l+r)//2), str((t+b)//2))
                break
        break
labels = ['Songs', 'Albums', 'Artists', 'Playlists']
rects = [bounds(tab(label)) for label in labels]
assert all(a[2] <= b[0] for a, b in zip(rects, rects[1:])), rects
assert max(r[1] for r in rects) - min(r[1] for r in rects) < 12, rects
height = int(re.findall(rb'\d+x(\d+)', adb('shell', 'wm', 'size'))[-1])
assert all(r[1] > height * .65 and r[3] <= height for r in rects), (height, rects)
for label in labels:
    tap(label)
    Path('device-navigation-' + label.lower() + '.png').write_bytes(adb('exec-out', 'screencap', '-p'))
root = snapshot()
assert any('Create playlist' in (n.get('text'), n.get('content-desc')) for n in root.iter('node')), 'Playlists did not open'
# A configuration change must preserve selection in the hoisted saveable state.
adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
time.sleep(2)
tab('Playlists', True)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
tap('Songs')
print('PASS: bottom order, single tabs, routing, selected accessibility state and recreation')

# Seed isolated emulator fixtures through root, for debug and optimized builds.
# Store tests above use separate filenames; these fixtures exercise the rendered UI.
import json
package = 'org.nexlibre.spacemusic'
base = f'/data/user/0/{package}/files'
fixture = [dict(id=k, name='Sort ' + k, songs=[
    dict(id='z', title='ZZZ sort fixture', artist='AAA artist', uri='file:///z.mp3'),
    dict(id='a', title='AAA sort fixture', artist='ZZZ artist', uri='file:///a.mp3'),
    dict(id='b', title='BBB sort fixture', artist='BBB artist', uri='file:///b.mp3'),
]) for k in ['Alpha', 'Beta', 'Gamma']]
adb('shell', 'am', 'force-stop', package)
subprocess.run(['adb', 'shell', f'cat > {base}/star-device-playlists.json'],
               input=json.dumps(fixture).encode(), check=True, timeout=30)
uid = adb('shell', 'stat', '-c', '%u', base).decode().strip()
adb('shell', 'chown', f'{uid}:{uid}', f'{base}/star-device-playlists.json')
adb('shell', 'restorecon', '-F', f'{base}/star-device-playlists.json')
adb('shell', 'am', 'start', '-W', '-n', package + '/com.music.bitchord.MainActivity')
tap('Playlists')


def node_named(label):
    for _ in range(10):
        found = [n for n in snapshot().iter('node') if label in (n.get('text'), n.get('content-desc'))]
        if len(found) == 1:
            return found[0]
        time.sleep(.4)
    raise AssertionError(f'Expected one control {label}; found {len(found)}')


def click_named(label):
    l, t, r, b = bounds(node_named(label))
    adb('shell', 'input', 'tap', str((l+r)//2), str((t+b)//2))


def assert_order(expected):
    for _ in range(10):
        rows = sorted((bounds(n)[1], n.get('text')) for n in snapshot().iter('node')
                      if n.get('text', '').endswith('sort fixture'))
        if [title[:3] for _, title in rows] == expected:
            return rows
        time.sleep(.4)
    raise AssertionError((expected, rows))


for name, criterion, expected in [
    ('Alpha', 'Alphabetical (A to Z)', ['AAA', 'BBB', 'ZZZ']),
    ('Beta', 'Artist', ['ZZZ', 'BBB', 'AAA']),
    ('Gamma', 'Recently added to this playlist', ['BBB', 'AAA', 'ZZZ']),
]:
    click_named('Sort ' + name)
    click_named('Sort this playlist')
    click_named(criterion)
    rows = assert_order(expected)
    density = int(re.findall(rb'\d+', adb('shell', 'wm', 'density'))[-1])
    assert rows[0][0] < 200 * density / 160, ('Excessive header height', rows[0], density)
    root = snapshot()
    assert not any(n.get('text') == 'Search this folder' for n in root.iter('node'))
    Path('device-playlist-sort-' + name.lower() + '.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('shell', 'input', 'keyevent', '4')

# A full process restart must keep all three independently saved criteria.
adb('shell', 'am', 'force-stop', package)
adb('shell', 'am', 'start', '-W', '-n', package + '/com.music.bitchord.MainActivity')
tap('Playlists')
click_named('Sort Alpha')
assert_order(['AAA', 'BBB', 'ZZZ'])
# Remove the FIRST VISIBLE song; its original storage index is deliberately different.
menus = sorted([n for n in snapshot().iter('node') if n.get('content-desc') == 'Song options'],
               key=lambda n: bounds(n)[1])
assert len(menus) == 3
l, t, r, b = bounds(menus[0])
adb('shell', 'input', 'tap', str((l+r)//2), str((t+b)//2))
click_named('Remove')
assert_order(['BBB', 'ZZZ'])
adb('shell', 'input', 'keyevent', '4')
click_named('Sort Beta')
assert_order(['ZZZ', 'BBB', 'AAA'])
adb('shell', 'input', 'keyevent', '4')
click_named('Sort Gamma')
assert_order(['BBB', 'AAA', 'ZZZ'])
print('PASS: compact playlist header, independent sorting, cold-start persistence and sorted deletion')

# The compact dock must keep accessible touch targets on every tested density.
density = int(re.findall(rb'\d+', adb('shell', 'wm', 'density'))[-1])
for label in labels:
    l, t, r, b = bounds(tab(label))
    assert (b - t) * 160 / density >= 48, ('Tab touch target too small', label, [l, t, r, b])

# Settings identifies this application and keeps the inherited legal notices
# reachable without a network connection, rather than advertising upstream sites.
adb('shell', 'input', 'keyevent', '4')
click_named('Settings')
size = list(map(int, re.findall(rb'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1]))
for _ in range(35):
    root = snapshot()
    buttons = [n for n in root.iter('node') if n.get('text') == 'Licenses and open source']
    if len(buttons) == 1:
        # UIAutomator also reports nodes obscured by the floating dock.
        # Continue scrolling until the button is visibly above its touch guard.
        safe_bottom = bounds(tab('Songs'))[1] - 24 * density / 160
        if bounds(buttons[0])[3] < safe_bottom:
            break
    adb('shell', 'input', 'swipe', str(size[0] // 2), str(int(size[1] * .60)),
        str(size[0] // 2), str(int(size[1] * .25)), '250')
else:
    raise AssertionError('Licenses and open source is not reachable in Settings')
footer = '\n'.join(n.get('text', '') for n in root.iter('node'))
assert 'Space Music 2.4.0' in footer, footer
assert 'Local music player' in footer, footer
assert 'Discord' not in footer and 'Website' not in footer, footer
Path('device-settings-identity.png').write_bytes(adb('exec-out', 'screencap', '-p'))
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
try:
    click_named('Licenses and open source')
    for _ in range(6):
        text = '\n'.join(n.get('text', '') for n in snapshot().iter('node'))
        if 'GNU GENERAL PUBLIC LICENSE' in text:
            break
        adb('shell', 'input', 'swipe', str(size[0] // 2), str(int(size[1] * .60)),
            str(size[0] // 2), str(int(size[1] * .25)), '250')
    # Put the expanded notice above the dock for an actual visual review,
    # rather than capturing only the first lines passing behind the glass.
    for _ in range(2):
        adb('shell', 'input', 'swipe', str(size[0] // 2), str(int(size[1] * .60)),
            str(size[0] // 2), str(int(size[1] * .25)), '250')
    Path('device-settings-licenses-offline.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    assert 'GNU GENERAL PUBLIC LICENSE' in text, 'Bundled GPL license was not rendered offline'
    assert 'BitChord — kushagrasinghx' in text and 'Star Music — nexlibre' in text, 'Missing source attribution'
    Path('device-settings-licenses-offline.png').write_bytes(adb('exec-out', 'screencap', '-p'))
finally:
    adb('shell', 'svc', 'wifi', 'enable')
    adb('shell', 'svc', 'data', 'enable')
print('PASS: accessible compact dock, Space Music identity and bundled license rendered offline')
