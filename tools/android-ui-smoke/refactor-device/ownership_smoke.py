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

def ui_hierarchy():
    path = '/data/local/tmp/tv-subtitle-ui.xml'
    subprocess.run([ADB, '-s', SERIAL, 'shell', 'uiautomator', 'dump', path], check=True, capture_output=True, timeout=15)
    return subprocess.run([ADB, '-s', SERIAL, 'shell', 'cat', path], check=True, capture_output=True, text=True, timeout=5).stdout


def choose_ui_text(*labels):
    import xml.etree.ElementTree as ET
    tree = ET.fromstring(ui_hierarchy())
    node = next((node for node in tree.iter('node') if node.get('text') in labels), None)
    assert node is not None, [(node.get('text'), node.get('bounds')) for node in tree.iter('node') if node.get('text')]
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    subprocess.run([ADB, '-s', SERIAL, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)], check=True)


def subtitle_options():
    if 'subtitlePhase' in cmd('state'):
        cmd('subtitleClose')
        wait('previous subtitle closed', lambda s: 'subtitlePhase' not in s)
    cmd('subtitleDialog')
    wait('subtitle fixture ready', lambda s: s.get('subtitlePhase') == 'READY')
    cmd('subtitleOpenSources')
    choose_ui_text('SubHD')
    source = wait('SubHD selected', lambda s: s.get('subtitleSource') == 'SUBHD' and s.get('subtitleCount') == 2)
    cmd('subtitleOpenPreferences')
    choose_ui_text('Cantonese first', '粤语优先', '粵語優先')
    preferred = wait('Cantonese ordered', lambda s: s.get('subtitlePreference') == 'CANTONESE')
    assert preferred['subtitleEntries'][0]['language'] == '粤语', preferred
    cmd('subtitlePick')
    state = cmd('state')
    assert state.get('subtitleSelected', '') == '', state
    ui = ui_hierarchy()
    assert any(label in ui for label in ['Open website', '打开网站', '開啟網站']), ui[-1500:]
    print(json.dumps({'source': source.get('subtitleSource'), 'preference': preferred.get('subtitlePreference'),
                      'first': preferred.get('subtitleEntries')[0], 'website_dialog': True}, ensure_ascii=False), flush=True)


def subtitle_live():
    observations = []
    for query in ['无间道', '英雄本色', '重慶森林']:
        wait('live subtitle host', lambda s: bool(s.get('activity')))
        cmd('subtitleLive', query=query)
        result = wait('live subtitle search', lambda s: s.get('subtitleQuery') == query
                      and s.get('subtitlePhase') in ('READY', 'ERROR'), timeout=50)
        assert result.get('subtitleCount', 0) > 0, result
        cmd('subtitleProbeDownload')
        download = wait('real subtitle file', lambda s: s.get('subtitleDownload') != 'loading')
        observations.append({'query': query, 'sources': result.get('subtitleSources'),
                             'unavailable': result.get('subtitleUnavailable'), 'first': result.get('subtitleEntries'),
                             'download': download.get('subtitleDownload')})
        cmd('subtitleClose')
        wait('subtitle closed', lambda s: 'subtitlePhase' not in s)
    assert any(item['download'] == 'valid' for item in observations), observations
    print(json.dumps(observations, ensure_ascii=False), flush=True)


def wallpaper_transaction():
    cmd('configWall', url=BASE + '/wallpaper.png')
    good = wait('wallpaper success', lambda s: s.get('wallConfigStatus') == 'success')
    assert good.get('wallHash') and good['wallConfigUrl'] == BASE + '/wallpaper.png', good
    failures = []
    for path in ('missing-wallpaper.png', 'invalid-wallpaper.png'):
        cmd('configWall', url=BASE + '/' + path)
        failed = wait('wallpaper failure', lambda s: s.get('wallConfigStatus', '').startswith('error:'))
        assert failed['wallHash'] == good['wallHash'] and failed['wallConfigUrl'] == good['wallConfigUrl'], failed
        failures.append(failed)
    print(json.dumps({'scenario': 'wallpaper publication', 'good': good, 'failures': failures}, ensure_ascii=False), flush=True)


def subtitle_screen():
    wait('subtitle foreground host', lambda s: bool(s.get('activity')))
    cmd('subtitleDialog')
    first = wait('subtitle page one', lambda s: s.get('subtitlePhase') == 'READY'
                 and s.get('subtitleCount') == 1 and s.get('subtitleNext'))
    assert not first['subtitlePrevious'], first
    cmd('subtitleNext')
    failed = wait('subtitle page failure', lambda s: s.get('subtitlePhase') == 'ERROR')
    assert failed['subtitlePage'] == 1 and failed['subtitleCount'] == 1, failed
    assert not failed['subtitleNext'] and not failed['subtitlePrevious'], failed
    cmd('subtitleRetry')
    wait('subtitle retry', lambda s: s.get('subtitlePhase') == 'READY' and s.get('subtitleNext'))
    cmd('subtitlePick')
    files = wait('subtitle files', lambda s: s.get('subtitlePhase') == 'READY' and s.get('subtitleScreen') == 'FILES')
    assert files['subtitleCount'] == 1 and not files['subtitleNext'] and not files['subtitlePrevious'], files
    cmd('subtitlePick')
    selected = wait('subtitle selected', lambda s: s.get('subtitleSelected') == 'https://fixture.test/movie.srt')
    print(json.dumps({'scenario': 'subtitle screen state', 'first': first, 'failed': failed,
                      'files': files, 'selected': selected}, ensure_ascii=False), flush=True)


def configuration_transactions():
    cmd('clearLive')
    wait('clear live', lambda s: s.get('liveSources') == 0)
    cmd('config', url=BASE + '/combined-config.json')
    linked = wait('combined config', lambda s: s.get('vodConfigStatus') == 'success'
                  and s.get('liveConfigUrl') == s.get('vodConfigUrl')
                  and 'Inherited Fixture' in s.get('liveNames', []))
    cmd('configLiveBatch', urls='|'.join(BASE + '/' + path for path in ['live.m3u', 'live-config.json', 'missing-config.json']))
    mixed = wait('mixed batch', lambda s: s.get('liveConfigStatus') == 'success' and s.get('liveSources') == 2)
    assert 'JSON Fixture' in mixed.get('liveNames', []), mixed
    cmd('openLive')
    first = wait('text batch source', lambda s: video(s, 'LiveActivity'))
    cmd('liveHome', value=1)
    second = wait('JSON batch source', lambda s: video(s, 'LiveActivity')
                  and s.get('sourceGeneration', 0) > first.get('sourceGeneration', 0))
    print(json.dumps({'scenario': 'combined activation and mixed live batch', 'linked': linked,
                      'mixed': mixed, 'before': first, 'after': second}, ensure_ascii=False), flush=True)
    cmd('finish')


def page_boundaries():
    cmd('config', url=BASE + '/config.json')
    configured = wait('VOD config', lambda s: s.get('vodConfigStatus') == 'success')
    cmd('config', url=BASE + '/missing-config.json')
    failed = wait('failed config', lambda s: s.get('vodConfigStatus', '').startswith('error:'))
    assert failed['vodConfigUrl'] == configured['vodConfigUrl'], (configured, failed)
    assert failed['vodSites'] == configured['vodSites'] > 0, (configured, failed)
    cmd('open', key='fixture', url='fixture')
    wait('page detail', lambda s: s.get('activity') == 'VideoActivity' and bool(s.get('selectedFlag')))
    cmd('flag', value=1)
    wait('working route', lambda s: video(s) and s.get('selectedFlag') == 'Working')
    # Saved history may already point at episode 2; force a distinct transition.
    cmd('episode', value=0)
    first = wait('first episode', lambda s: video(s) and s.get('selectedEpisodeIndex') == 0)
    cmd('episode', value=1)
    second = wait('second episode', lambda s: video(s) and s.get('selectedEpisodeIndex') == 1)
    assert first['historyKey'] == second['historyKey'], (first, second)
    print(json.dumps({'scenario': 'config fallback and route/episode selection', 'configured': configured,
                      'failed': failed, 'before': first, 'after': second}, ensure_ascii=False), flush=True)
    cmd('finish')


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

def danmaku_speed():
    """Exercise renderer with TV KeyEvents or mobile speed handlers, without a physical remote."""
    cmd('open', url=BASE + '/fixture.mp4?danmaku=' + str(time.time_ns()))
    initial = wait('VOD for danmaku', video)
    engine = int(os.environ.get('TV_SMOKE_ENGINE', '2'))
    if initial.get('engine') != engine:
        cmd('player', value=engine)
        wait('danmaku engine', lambda s: video(s) and s.get('engine') == engine)
    if PACKAGE.endswith('.tv'):
        cmd('click', path='video')
        wait('fullscreen danmaku', lambda s: s.get('fullscreen'))
    cmd('speed', value=1.25)
    cmd('danmakuFixture')
    wait('native comments', lambda s: s.get('danmakuReady') and bool(s.get('danmakuComments')))
    observations = []
    for command, rate in [('state', 1.25), ('speedHold', 3), ('speedHold', 5), ('speedRelease', 1.25)]:
        before = cmd('state')
        event = {'speedHold': 'speedKeyHold', 'speedRelease': 'speedKeyRelease'}.get(command, command) if PACKAGE.endswith('.tv') else command
        cmd(event)
        wait('speed handler', lambda s: abs(s.get('speed', 0) - rate) < .01 and video(s))
        samples = []
        for _ in range(6):
            time.sleep(.25)
            state = cmd('state')
            assert state['danmakuReady'] and not state['danmakuPaused'], state
            assert abs(state['danmakuScroll'] - 1.8) < .01, state
            samples.append(state)
        assert all(b['danmakuTime'] >= a['danmakuTime'] for a, b in zip(samples, samples[1:])), samples
        state = samples[-1]
        assert abs(state['position'] - state['danmakuTime']) < 1000, state
        assert any(s.get('danmakuComments') for s in samples), samples
        old = {c['index']: c for c in before.get('danmakuComments', [])}
        common = [c for c in samples[0].get('danmakuComments', []) if c['index'] in old]
        if command != 'state':
            assert common, ('comments cleared during speed change', before, samples[0])
            assert all(c['left'] <= old[c['index']]['left'] + 5 for c in common), common
        observations.append({'rate': rate, 'video': state['position'], 'clock': state['danmakuTime'],
                             'render': [c['time'] for c in state.get('danmakuComments', [])[:1]],
                             'retained': len(common), 'comments': len(state.get('danmakuComments', []))})
    cmd('mediaPause')
    wait('danmaku paused', lambda s: video(s, playing=False) and s.get('danmakuPaused'))
    time.sleep(.25)
    paused = cmd('state')
    time.sleep(.5)
    assert cmd('state')['danmakuTime'] == paused['danmakuTime'], paused
    cmd('mediaSeek', position=60_000)
    wait('paused seek', lambda s: not s.get('playing') and abs(s.get('danmakuTime', 0) - 60_000) < 500)
    cmd('mediaPlay')
    wait('danmaku resume', lambda s: video(s) and bool(s.get('danmakuComments')) and s['danmakuTime'] >= 60_000)
    print(json.dumps({'engine': engine, 'rates': observations, 'pause_seek_resume': True}, ensure_ascii=False), flush=True)
    cmd('finish')


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
    parser.add_argument('mode', choices=['paused', 'playing', 'live', 'ijk', 'mobile-paused', 'mobile-playing', 'page', 'config', 'subtitle-screen', 'subtitle-options', 'subtitle-live', 'danmaku-speed', 'wallpaper', 'state'])
    mode = parser.parse_args().mode
    if mode == 'paused': vod_cast(True)
    elif mode == 'playing': vod_cast(False)
    elif mode == 'live': live_cast()
    elif mode == 'ijk': vod_cast(True, 1)
    elif mode == 'mobile-paused': vod_live(True)
    elif mode == 'mobile-playing': vod_live(False)
    elif mode == 'page': page_boundaries()
    elif mode == 'config': configuration_transactions()
    elif mode == 'subtitle-screen': subtitle_screen()
    elif mode == 'subtitle-live': subtitle_live()
    elif mode == 'subtitle-options': subtitle_options()
    elif mode == 'danmaku-speed': danmaku_speed()
    elif mode == 'wallpaper': wallpaper_transaction()
    elif mode == 'state': print(json.dumps(cmd('state'), ensure_ascii=False))
