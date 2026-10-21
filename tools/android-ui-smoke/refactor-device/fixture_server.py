"""Local-only deterministic VOD fixture server; no external media or application data."""
import argparse
import http.server
import pathlib
import re
import threading
import json
from urllib.parse import urlsplit, parse_qs


class Fixtures(http.server.BaseHTTPRequestHandler):
    counts = {}
    lock = threading.Lock()
    media = None

    def do_GET(self):
        path = self.path.split('?', 1)[0]
        base = 'http://127.0.0.1:18765'
        if path in ('/config.json', '/api'):
            query = parse_qs(urlsplit(self.path).query)
            if path == '/config.json':
                payload = {'sites': [{'key': 'fixture', 'name': 'Fixture', 'type': 4, 'api': base + '/api', 'searchable': 1, 'changeable': 1}]}
            elif 'play' in query:
                payload = {'parse': 0, 'url': base + ('/missing.mp4' if query.get('flag', [''])[0] == 'Broken' else '/fixture.mp4')}
            else:
                payload = {'list': [{'vod_id': 'fixture', 'vod_name': 'Playback fixture', 'vod_play_from': 'Broken$$$Working', 'vod_play_url': 'Episode 1$bad#Episode 2$bad2$$$Episode 1$good#Episode 2$good2'}]}
            data = json.dumps(payload).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        with self.lock:
            count = self.counts[path] = self.counts.get(path, 0) + 1
        if path == '/fail-once.mp4' and count == 1:
            self.send_error(404, 'Intentional first-request failure')
            return
        if path == '/subtitle.srt':
            data = b'1\n00:00:00,000 --> 00:03:00,000\nPlayback regression fixture\n'
            self.send_response(200)
            self.send_header('Content-Type', 'application/x-subrip')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if path not in ('/fixture.mp4', '/fail-once.mp4'):
            self.send_error(404)
            return
        size = self.media.stat().st_size
        start, end = 0, size - 1
        match = re.match(r'bytes=(\d+)-(\d*)', self.headers.get('Range', ''))
        if match:
            start = int(match[1])
            if match[2]:
                end = min(int(match[2]), end)
        if start > end:
            self.send_response(416)
            self.end_headers()
            return
        self.send_response(206 if match else 200)
        self.send_header('Content-Type', 'video/mp4')
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('Content-Length', str(end - start + 1))
        if match:
            self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
        self.end_headers()
        try:
            with self.media.open('rb') as source:
                source.seek(start)
                remaining = end - start + 1
                while remaining:
                    data = source.read(min(remaining, 65536))
                    if not data:
                        break
                    self.wfile.write(data)
                    remaining -= len(data)
        except (BrokenPipeError, ConnectionResetError):
            pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--media', required=True, type=pathlib.Path)
    parser.add_argument('--port', type=int, default=18765)
    options = parser.parse_args()
    Fixtures.media = options.media
    http.server.ThreadingHTTPServer(('127.0.0.1', options.port), Fixtures).serve_forever()
