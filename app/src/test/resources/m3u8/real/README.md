# Real M3U8 regression fixtures

These files are unmodified final media playlists captured through the Android app on 2026-07-29 and 2026-07-30. They are stored locally so ad-filter tests do not depend on remote CDN availability.

- `chimi-*.m3u8`: all 19 reachable station playlists captured for `痴迷`; the repeated 9-segment Pod in `chimi-18` was verified from its first, middle, preceding, and following media frames as a casino ad between continuous main-content scenes.
- `odyssey-*.m3u8`: all 13 direct M3U8 station playlists captured from the App detail page for `奥德赛`; `odyssey-07` contains two frame-verified casino-ad Pods.
- `qunti-*.m3u8`: 11 reachable station playlists for `群体`. The captured set includes frame-verified repeated casino-ad Pods and an `adjump` playlist, along with sources where every media segment must be preserved.
- `spirited-away-*.m3u8`: two normal long-form playlists for `千与千寻`, retained as false-positive controls.
- `xuan-an-*.m3u8`: two episode playlists captured through actual App playback for `悬案`.
- `station-*.m3u8`: three additional real station playlists used during ad-filter investigation.
- Unreachable and zero-byte station responses are intentionally excluded because they are not valid media playlists.

`M3u8AdFilterTest.filtersCapturedRealMediaPlaylistsWithoutRegressions` records the exact input and expected output segment counts. Do not regenerate or normalize these files when updating expectations; add a new captured fixture when a new real structure is discovered.

Exact repeated short Pods bounded by discontinuities are retained as strong structural evidence. `odyssey-07` and `chimi-18` additionally have semantic assertions for the removed Pod URI and preserved neighboring main-content segments; fixtures whose output is unchanged act as false-positive controls.
