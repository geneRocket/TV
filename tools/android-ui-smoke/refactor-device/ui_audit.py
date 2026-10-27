"""Native dialog layout/lifecycle checks on an explicitly selected isolated test device."""
import os
import re
import time
import subprocess
import xml.etree.ElementTree as ET
import ownership_smoke as smoke

if not os.environ.get('ANDROID_SERIAL'):
    raise SystemExit('Set ANDROID_SERIAL to the isolated test emulator')


def adb(*args):
    return subprocess.run([smoke.ADB, '-s', smoke.SERIAL, *args], check=True, capture_output=True, text=True)


def back():
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(.4)


def toolbar():
    for full in (False, True):
        smoke.cmd('uiSubtitleToolbar', full=full)
        time.sleep(.4)
        tree = ET.fromstring(smoke.ui_hierarchy())
        parents = {child: parent for parent in tree.iter() for child in parent}
        first = next(n for n in tree.iter('node') if n.get('resource-id', '').endswith('/large'))
        root = parents[first]
        bounds = list(map(int, re.findall(r'\d+', root.get('bounds'))))
        for name in ('large', 'small', 'up', 'down', 'reset', 'search'):
            node = next(n for n in root.iter('node') if n.get('resource-id', '').endswith('/' + name))
            b = list(map(int, re.findall(r'\d+', node.get('bounds'))))
            assert bounds[0] <= b[0] < b[2] <= bounds[2] and bounds[1] <= b[1] < b[3] <= bounds[3], (name, bounds, b)
            assert node.get('content-desc'), name
        if smoke.PACKAGE.endswith('.tv'):
            for _ in range(6):
                adb('shell', 'input', 'keyevent', '22')
            nodes = ET.fromstring(smoke.ui_hierarchy()).iter('node')
            assert any(n.get('resource-id', '').endswith('/search') and n.get('focused') == 'true' for n in nodes), 'Search not reachable by D-pad'
        print('PASS toolbar full=' + str(full) + ' ' + str(bounds), flush=True)
        back()


def mobile_dialogs():
    smoke.cmd('uiTimer')
    assert smoke.cmd('state')['uiTimerAttached']
    tree = ET.fromstring(smoke.ui_hierarchy())
    assert any(n.get('resource-id', '').endswith('/time1') for n in tree.iter('node')), 'Timer not visible yet'
    back()
    smoke.wait('timer callback released after Back', lambda value: not value['uiTimerAttached'], timeout=5)
    print('PASS timer Back removes callback', flush=True)
    expected = ['120 - 81', '80 - 41', '40 - 1']
    smoke.cmd('uiEpisodeGrid')
    smoke.ui_hierarchy()
    assert smoke.cmd('state')['uiEpisodeTitles'] == expected
    smoke.cmd('uiRebuildDialog')
    time.sleep(.5)
    assert smoke.cmd('state')['uiEpisodeTitles'] == expected, 'Duplicate tabs after view rebuild'
    smoke.cmd('uiRecreateActivity')
    time.sleep(2)
    assert smoke.cmd('state')['uiEpisodeTitles'] == expected, 'Episode state lost after activity recreation'
    print('PASS reverse page labels, view rebuild and activity recreation', flush=True)
    back()


if __name__ == '__main__':
    toolbar()
    if smoke.PACKAGE.endswith('.mobile'):
        mobile_dialogs()
