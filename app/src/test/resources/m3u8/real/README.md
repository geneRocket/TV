# Real M3U8 regression fixtures

These files are unmodified final media playlists captured through the Android app on 2026-07-29 and 2026-07-30. They are stored locally so ad-filter tests do not depend on remote CDN availability.

- `chimi-*.m3u8`: all 19 reachable station playlists captured for `痴迷`.
- `qunti-*.m3u8`: 11 reachable station playlists for `群体`. The captured set includes frame-verified repeated casino-ad Pods and an `adjump` playlist, along with sources where every media segment must be preserved.
- `spirited-away-*.m3u8`: two normal long-form playlists for `千与千寻`, retained as false-positive controls.
- `xuan-an-*.m3u8`: two episode playlists captured through actual App playback for `悬案`.
- `station-*.m3u8`: three additional real station playlists used during ad-filter investigation.
- Unreachable and zero-byte station responses are intentionally excluded because they are not valid media playlists.

`M3u8AdFilterTest.filtersCapturedRealMediaPlaylistsWithoutRegressions` records the exact input and expected output segment counts. Do not regenerate or normalize these files when updating expectations; add a new captured fixture when a new real structure is discovered.
