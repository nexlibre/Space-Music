"""Capture actual contextual spatial controls in light/dark themes and large font."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
PACKAGE = 'org.nexlibre.spacemusic'

def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=40)

def tree():
    adb('shell','uiautomator','dump','--compressed','/sdcard/spatial.xml')
    raw = adb('exec-out','cat','/sdcard/spatial.xml')
    Path('device-spatial-ui.xml').write_bytes(raw)
    return ET.fromstring(raw)

def find(label):
    for _ in range(8):
        for n in tree().iter('node'):
            if any(v == label or v.startswith(label + '\n') for v in (n.get('text',''), n.get('content-desc',''))):
                return n
        time.sleep(.5)
    raise AssertionError('Missing spatial UI: ' + label)

def tap(label):
    n = find(label)
    l,t,r,b = map(int,re.findall(r'\d+', n.get('bounds')))
    adb('shell','input','tap',str((l+r)//2),str((t+b)//2))
    time.sleep(.3)

def expand_sheet():
    handle=find('Drag handle')
    l,t,r,b=map(int,re.findall(r'\d+',handle.get('bounds')))
    y=(t+b)//2
    if y > 80:
        adb('shell','input','swipe',str((l+r)//2),str(y),str((l+r)//2),'40','450')
        time.sleep(.4)

def assert_selected(mode):
    root=tree(); parents={c:p for p in root.iter() for c in p}
    for n in root.iter('node'):
        if n.get('text') == mode:
            while n in parents:
                if n.get('checked') == 'true' or n.get('selected') == 'true':
                    return
                n=parents[n]
    raise AssertionError('Spatial mode did not become selected: '+mode)

def capture(name):
    Path('device-spatial-' + name + '.png').write_bytes(adb('exec-out','screencap','-p'))

size = list(map(int,re.findall(rb'(\d+)x(\d+)',adb('shell','wm','size'))[-1]))
for theme, font in [('DARK', '1.0'), ('LIGHT', '1.0'), ('DARK', '1.5')]:
    adb('shell','am','force-stop',PACKAGE)
    path = '/data/user/0/' + PACKAGE + '/shared_prefs/bitchord_settings.xml'
    preferences = ET.fromstring(adb('exec-out','cat',path))
    for tag,name,value in [('string','theme_mode',theme), ('boolean','spatial_audio','true')]:
        entry = next((e for e in preferences if e.get('name') == name), None)
        if entry is None: entry = ET.SubElement(preferences,tag,{'name':name})
        if tag == 'string': entry.text=value
        else: entry.set('value',value)
    subprocess.run(['adb','shell','tee',path],input=ET.tostring(preferences),stdout=subprocess.DEVNULL,check=True,timeout=30)
    owner=adb('shell','stat','-c','%u:%g','/data/user/0/'+PACKAGE).decode().strip()
    assert re.fullmatch(r'\d+:\d+',owner)
    adb('shell','chown',owner,path); adb('shell','rm','-f',path+'.bak')
    adb('shell','settings','put','system','font_scale',font)
    adb('shell','am','start','-W','-f','0x10008000','-n',PACKAGE+'/com.music.bitchord.MainActivity')
    # Scope navigation to the actual Settings screen. Outgoing library content
    # may remain in the accessibility tree during a transition; never type into
    # an unscoped editor or use Back to dismiss its keyboard.
    time.sleep(1)
    for _ in range(12):
        root=tree()
        if any(n.get('text') == 'Settings' and n.get('class') == 'android.widget.TextView' for n in root.iter('node')):
            break
        buttons=[n for n in root.iter('node') if n.get('content-desc') == 'Settings']
        if buttons:
            l,t,r,b=map(int,re.findall(r'\d+',buttons[0].get('bounds')))
            adb('shell','input','tap',str((l+r)//2),str((t+b)//2))
        time.sleep(.4)
    else:
        capture('settings-not-open')
        raise AssertionError('Settings screen did not open')
    for _ in range(24):
        root=tree()
        rows=[n for n in root.iter('node') if n.get('text','').split('\n')[0] == 'Spatial audio']
        reachable=[]
        for n in rows:
            l,t,r,b=map(int,re.findall(r'\d+',n.get('bounds')))
            if t > 80 and b < size[1]*.82 and r > l:
                reachable.append(n)
        if reachable:
            l,t,r,b=map(int,re.findall(r'\d+',reachable[0].get('bounds')))
            adb('shell','input','tap',str((l+r)//2),str((t+b)//2))
            time.sleep(.4)
            break
        # Pointer-down stays below the search field, so a scroll cannot become
        # editor focus or gesture typing on the software keyboard.
        adb('shell','input','swipe',str(size[0]//2),str(int(size[1]*.70)),str(size[0]//2),str(int(size[1]*.32)),'300')
    else:
        capture('spatial-row-not-reachable')
        raise AssertionError('Spatial audio row is not reachable by scrolling')
    # Expand through the native handle, without dragging a card or slider.
    expand_sheet()
    label=theme.lower()+'-'+font
    for mode in ['Espacial original','Audio 8D','Espacial avanzado']:
        expand_sheet()
        tap(mode)
        expand_sheet()
        assert_selected(mode)
        text='\n'.join(n.get('text','') for n in tree().iter('node'))
        assert 'Conserva el sonido espacial actual.' in text
        if mode == 'Espacial original':
            assert 'Velocidad' not in text and 'Distancia virtual' not in text
        capture(label+'-'+mode.replace(' ','-'))
    adb('shell','input','keyevent','4')
adb('shell','settings','put','system','font_scale','1.0')
print('PASS: three real selectable spatial modes in dark/light and large font; screenshots retained')
