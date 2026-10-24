"""Run ownership round trips against the opt-in debug Receiver and local fixture_server.py.

The app must already be in the foreground. JSON output is an observation, not a
substitute for checking the actual sender, PiP controls or hardware decoding.
"""
import json
import os
import pathlib
import shutil
import re
import shlex
import subprocess
import sys
import time

ADB = os.environ.get('ADB') or shutil.which('adb') or str(pathlib.Path(os.environ.get('ANDROID_HOME', pathlib.Path.home() / 'Library/Android/sdk')) / 'platform-tools/adb')
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
PACKAGE = os.environ.get('TV_SMOKE_PACKAGE', 'com.fongmi.android.tv.codextest.tv')
RECEIVER = PACKAGE + '/com.fongmi.android.tv.test.RefactorReceiver'
BASE = 'http://127.0.0.1:18765'

def cmd(command, **extras):
    args = ['am', 'broadcast', '-n', RECEIVER, '--es', 'cmd', command]
    for key, value in extras.items():
        flag = '--ez' if isinstance(value, bool) else ('--el' if key == 'position' else '--ei') if isinstance(value, int) else '--ef' if isinstance(value, float) else '--es'
        args += [flag, key, str(value).lower() if isinstance(value, bool) else str(value)]
    result = subprocess.run([ADB, '-s', SERIAL, 'shell', shlex.join(args)], capture_output=True, text=True, timeout=12)
    match = re.search(r'data="(\{.*\})"', result.stdout)
    if not match: raise RuntimeError(result.stdout[-1200:] + result.stderr[-500:])
    state = json.loads(match[1])
    if 'error' in state: raise RuntimeError(state)
    return state

def wait(label, predicate, timeout=20):
    until = time.monotonic() + timeout
    last = None
    while time.monotonic() < until:
        last = cmd('state')
        if predicate(last): return last
        time.sleep(.5)
    raise AssertionError(label + ': ' + json.dumps(last, ensure_ascii=False))

def video(state, activity='VideoActivity', playing=True):
    return state.get('activity') == activity and state.get('playing') == playing and state.get('videoWidth', 0) > 0 and state.get('phase') in ('READY', 'PAUSED')

def load_live_config():
    # A cold app can still be submitting its startup configuration. If that
    # generation supersedes our first request, explicitly submit the fixture again.
    for attempt in range(2):
        cmd('configLive', url=BASE + '/live.m3u')
        try:
            wait('live config', lambda s: s.get('liveConfigStatus') == 'success', timeout=5 if attempt == 0 else 20)
            return
        except AssertionError:
            if attempt: raise

def vod_cast(paused, engine=2):
    cmd('open', url=BASE + '/fixture.mp4?case=' + ('paused' if paused else 'playing'))
    initial = wait('initial VOD', video)
    if initial.get('engine') != engine:
        cmd('player', value=engine)
        wait('selected engine', lambda s: video(s) and s.get('engine') == engine)
    cmd('speed', value=2.0)
    cmd('mediaSeek', position=30_000)
    wait('seek', lambda s: video(s) and s.get('position', 0) >= 29_000)
    if paused:
        cmd('mediaPause')
        wait('user pause', lambda s: video(s, playing=False) and s['phase'] == 'PAUSED')
    before = cmd('state')
    cmd('openCast', url=BASE + '/fixture.mp4?cast=1')
    cast = wait('cast', lambda s: video(s, 'CastActivity'))
    cmd('finish')
    after = wait('VOD restored', lambda s: video(s, playing=not paused) and s.get('position', 0) >= 29_000)
    assert abs(after['speed'] - 2.0) < .01, after
    assert before['position'] - 1000 <= after['position'] <= before['position'] + 12_000, (before, after)
    print(json.dumps({'scenario': 'paused VOD -> cast -> return' if paused else 'playing VOD -> cast -> return',
                      'before': before, 'cast': cast, 'after': after}, ensure_ascii=False), flush=True)
    cmd('finish')

def vod_live(paused):
    load_live_config()
    cmd('open', url=BASE + '/fixture.mp4?mobile=' + str(paused))
    wait('initial mobile VOD', video)
    cmd('speed', value=2.0)
    cmd('mediaSeek', position=30_000)
    wait('mobile seek', lambda s: video(s) and s.get('position', 0) >= 29_000)
    if paused:
        cmd('mediaPause')
        wait('mobile user pause', lambda s: video(s, playing=False))
    before = cmd('state')
    cmd('openLive')
    live = wait('mobile live takeover', lambda s: video(s, 'LiveActivity'))
    cmd('finish')
    after = wait('mobile VOD return', lambda s: video(s, playing=not paused) and s.get('position', 0) >= 29_000)
    assert abs(after['speed'] - 2.0) < .01, after
    assert before['position'] - 1000 <= after['position'] <= before['position'] + 12_000, (before, after)
    print(json.dumps({'scenario': 'mobile VOD -> live -> return; paused=' + str(paused),
                      'before': before, 'live': live, 'after': after}, ensure_ascii=False), flush=True)
    cmd('finish')

def live_cast():
    load_live_config()
    cmd('openLive')
    before = wait('live playback', lambda s: video(s, 'LiveActivity'))
    cmd('openCast', url=BASE + '/fixture.mp4?cast=live')
    cast = wait('cast over live', lambda s: video(s, 'CastActivity'))
    cmd('finish')
    after = wait('live restored', lambda s: video(s, 'LiveActivity'))
    print(json.dumps({'scenario': 'live -> cast -> return', 'before': before, 'cast': cast, 'after': after}, ensure_ascii=False), flush=True)
    cmd('finish')

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['paused', 'playing', 'live', 'ijk', 'mobile-paused', 'mobile-playing', 'state'])
    mode = parser.parse_args().mode
    if mode == 'paused': vod_cast(True)
    elif mode == 'playing': vod_cast(False)
    elif mode == 'live': live_cast()
    elif mode == 'ijk': vod_cast(True, 1)
    elif mode == 'mobile-paused': vod_live(True)
    elif mode == 'mobile-playing': vod_live(False)
    elif mode == 'state': print(json.dumps(cmd('state'), ensure_ascii=False))
