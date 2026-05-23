# Modules And Features

Use this file to quickly map a user request to the module, package, or feature owner that should be inspected first.

## Gradle Modules

| Module | Primary responsibility | Important areas |
| --- | --- | --- |
| `app` | Final Android application: UI, config management, playback orchestration, local server, database, settings, history, favorites, downloads. | `app/src/main`, `app/src/leanback`, `app/src/mobile`, `app/src/java`, `app/src/python`. |
| `catvod` | Shared CatVod crawler contracts, network utilities, path/preferences helpers, crawler base classes. | `com.github.catvod.crawler`, `net`, `bean`, `utils`. |
| `quickjs` | JavaScript crawler runtime and JS bridge for `.js` site APIs. | `crawler.Loader`, `crawler.Spider`, `method`, `utils`, bundled JS libs. |
| `chaquo` | Python crawler runtime and Python-to-Java bridge for `.py` site APIs. | `com.fongmi.chaquo.Loader`, `Spider`, `chaquo/src/main/python`. |
| `hook` | PackageManager and handler hook support used by live/core package identity paths. | `Hook`, `PackageManager`, `Handler`. |
| `tvbus` | TVBus native engine wrapper. | `TVCore`, `Listener`, native libs. |
| `zlive` | ZLive native core bridge via JNA. | `ZLive.INSTANCE`, `OnLiveStart`, `OnLiveStop`. |
| `thunder` | Xunlei/Thunder download SDK wrapper for thunder, magnet, BT, emule, FTP, and local playable URLs. | `XLTaskHelper`, `XLDownloadManager`, `XLLoader`, `parameter`. |
| `youtube` | Vendored YouTube downloader/parser/extractor/cipher implementation. | `YoutubeDownloader`, `ParserImpl`, `ExtractorImpl`, `downloader`, `model`. |
| `jianpian` | JianPian P2P native wrapper and local HTTP daemon. | `P2PClass`, `libjpa.so`. |
| `forcetech` | ForceTV/MITV/PxP native service wrappers for `p2p` through `p9p` schemes. | `Util`, `PxPService`, `MainActivity`, native libs. |
| `ijkplayer` | IJK player Java wrapper, JNI libs, and render views. | `IjkMediaPlayer`, `IjkVideoView`, render view classes, ffmpeg API. |

## App Source Sets

| Source set | Function |
| --- | --- |
| `app/src/main` | Shared app logic: `App`, `Setting`, config parsing, beans, database, shared UI dialogs/widgets, player, server, utils, web assets. |
| `app/src/leanback` | Android TV interface: landscape activities, Leanback presenters, focus/key handling, boot receiver, TV DLNA renderer service. |
| `app/src/mobile` | Phone/tablet interface: main navigation, detail pages, touch dialogs, scan, PiP, playback service, media button receiver, mobile DLNA cast service. |
| `app/src/java` | Java flavor implementation of `PyLoader`: no-op Python crawler support. |
| `app/src/python` | Python flavor implementation of `PyLoader`: real Chaquopy-backed crawler support. |
| `app/src/{x86,arm64_v8a,armeabi_v7a}` | ABI-specific assets. |

## Shared App Packages

| Package | Responsibility |
| --- | --- |
| `api` | Config decoding, VOD/live parsing, EPG parsing, and loader dispatch. |
| `api/config` | `VodConfig`, `LiveConfig`, `WallConfig`; config load, merge, cache, current home selection, proxy/header/rule collection. |
| `api/loader` | `BaseLoader`, `JarLoader`, `JsLoader`, flavor-specific `PyLoader`; runtime selection and crawler lifecycle. |
| `bean` | Data model layer for config, VOD, live, channel, EPG, parse, rule, URL, history, favorites, downloads, subtitles, danmaku, style, and DB entities. |
| `db` and `db/dao` | Room database, migrations, backup/restore, and DAO access for persistent app state. |
| `event` | EventBus messages for refresh, player state, cast, scan, server, action, and errors. |
| `impl` | Callback interfaces and small adapter implementations used by dialogs, config, parse, live, proxy, language, backup, danmaku, and sessions. |
| `gson` | JSON adapters/deserializers for app-specific parsing. |
| `model` | ViewModels for VOD and live business flows: `SiteViewModel`, `LiveViewModel`. |
| `player` | Playback selection, source parsing, protocol extraction, Media3 integration, IJK utility, subtitles, danmaku, and volume processing. |
| `receiver` | Shared broadcast receiver for playback action intents. |
| `server` | Embedded NanoHTTPD server and request processors for local action/cache/media/parse/proxy endpoints. |
| `ui` | Shared UI components and dialogs used by both modes, including player/subtitle/track dialogs and custom widgets. |
| `utils` | Cross-cutting helpers for files, URLs, networking, scan/QR, notification, language, traffic, webview, images, threading, ad filtering, and resources. |
| `viewmodel` | Subtitle-specific ViewModel and loader callbacks. |

## User-Facing Feature Map

| Feature | Main owner | Supporting areas |
| --- | --- | --- |
| App startup and global settings | `App.java`, `Setting.java` | `Prefers`, `LanguageUtil`, `Notify`, `OkHttp`, `LiveConfig` hook path. |
| VOD config management | `VodConfig`, `Config` bean, config dialogs/adapters | `Decoder`, `Depot`, `Site`, `Parse`, `Rule`, README/sample VOD JSON. |
| Live config management | `LiveConfig`, `LiveParser`, live dialogs/adapters | `Live`, `Group`, `Channel`, `Core`, README/sample live JSON. |
| VOD home/category/detail/search | `SiteViewModel`, `Site`, `Result`, `Vod` | Mode-specific `VodFragment`, `HomeActivity`/`MainActivity`, adapters, holders. |
| Playback URL resolution | `Source`, `ParseJob`, `SiteViewModel`, `LiveViewModel` | `Parse`, `Url`, `Flag`, `UrlUtil`, parser callbacks. |
| Video playback | `Players`, `player/exo`, `ijkplayer`, mode-specific `VideoActivity` | `Setting`, `Track`, `TrackDialog`, subtitle/danmaku UI. |
| Live playback and EPG | `LiveViewModel`, `EpgParser`, `LiveParser`, `Channel`, `Epg` | `LiveActivity`, `Group/Channel` adapters, catchup fields. |
| Spider/crawler execution | `BaseLoader`, `JarLoader`, `JsLoader`, `PyLoader` | `catvod`, `quickjs`, `chaquo`, external jar/js/py APIs. |
| Local proxy/server endpoints | `Server`, `Nano`, `server/process` | README endpoints, crawler `proxy`, player media/proxy paths. |
| Favorites, history, keep, collect | `Keep`, `History`, DAOs | mode-specific `KeepActivity`, `HistoryActivity`, `CollectActivity`, adapters. |
| Downloads and offline media | `Download` bean, `thunder`, mobile `Downloader` utility | `FolderActivity`, `XLTaskHelper`, torrent/magnet handling. |
| External protocols and P2P | `player/extractor` | `tvbus`, `zlive`, `jianpian`, `forcetech`, `thunder`, `youtube`. |
| DLNA/cast/receive/transmit | mode-specific cast services/dialogs | local server, `CastEvent`, `CastVideo`, DLNA local AARs. |
| Subtitles and danmaku | `SubtitleViewModel`, subtitle dialogs, `player/danmu` | README subtitle/danmaku push endpoints, player UI. |
| Backup/restore/sync | `AppDatabase`, backup dialogs, config/device DAOs | `FileUtil`, `Path`, `Prefers`, mobile sync/transmit dialogs. |
| Web sniffing and parse UI | `Sniffer`, `CustomWebView`, `WebDialog`, `Tbs` | parse configs, `ParseJob`, X5 webview dialog on leanback. |
| Language, wall, display, player settings | `Setting`, mode-specific setting screens/dialogs | `RefreshEvent`, `ResUtil`, `FileUtil`, `LanguageUtil`. |

## Main Data Flows

### VOD Config To Playback

1. User selects or loads config through mode-specific config UI.
2. `VodConfig.load` resolves JSON with `Decoder`, merges multiple configs if needed, caches successful config data, and builds `Site`, `Parse`, `Rule`, header, proxy, and flag lists.
3. UI asks `SiteViewModel` for home, category, detail, search, or player data.
4. `SiteViewModel` chooses direct API calls for normal sites or `Site.recent().spider()` for crawler-backed type `3`.
5. `BaseLoader` routes crawler-backed sites to jar, JS, or Python runtime based on the site API.
6. Detail data becomes `Vod` and `Flag` data; playback calls return `Result`/`Url` data.
7. `Source` and `ParseJob` normalize, parse, sniff, or proxy the final URL.
8. `Players` and the selected player implementation start playback in `VideoActivity`.

### Live Config To Channel Playback

1. `LiveConfig.load` resolves JSON or text live sources, parses `lives`, channels, EPG, headers, proxy, rules, and ads.
2. UI displays groups/channels in `LiveActivity`.
3. `LiveViewModel.getLive` starts `LiveParser`, validates channels, and posts live data.
4. `LiveViewModel.getEpg` fetches EPG by date/time zone and selects the current program.
5. `LiveViewModel.getUrl` resolves catchup or live URL through `Source`.
6. Player stack starts the normalized stream URL.

### Crawler Runtime Dispatch

1. Config `api` value determines runtime.
2. `.py` uses Python flavor `PyLoader`; Java flavor returns `SpiderNull`.
3. `.js` uses `quickjs` `Loader` and JS assets.
4. `csp_` uses `JarLoader` with `DexClassLoader`.
5. Crawler instances are cached by site/live scoped keys and destroyed when configs are cleared.

### Local Server And Proxy

1. `Server`/`Nano` receives local requests, often from player, WebView, crawler proxy methods, README endpoints, or external clients.
2. `server/process` dispatches to `Action`, `Cache`, `Local`, `Media`, `Parse`, or `Proxy`.
3. `BaseLoader.proxyLocal` delegates crawler-specific proxy calls when params target a site or runtime.
4. Cache endpoints store short strings for crawler scripts and app flows.

## Where To Start By Request Type

| Request mentions | Start here |
| --- | --- |
| TV layout, remote keys, launcher, boot | `app/src/leanback`. |
| Mobile layout, scan, PiP, bottom nav, media notification | `app/src/mobile`. |
| Source/config JSON, sites, lives, parses, headers, proxy, rules | `VodConfig`, `LiveConfig`, related `bean` classes, README samples. |
| Spider jar, `csp_`, JS spider, Python spider | `BaseLoader`, runtime-specific loader, matching external module. |
| Stream URL cannot play or parse | `Source`, `ParseJob`, `player/extractor`, `SiteViewModel` or `LiveViewModel`. |
| History, favorites, keep, config persistence | `AppDatabase`, DAO, entity bean. |
| Subtitle search/load | `SubtitleViewModel`, subtitle dialogs, player subtitle handling. |
| Embedded HTTP endpoint | `server/Nano.java`, `server/Server.java`, `server/process`. |
| Native protocol wrapper | Matching module: `tvbus`, `zlive`, `jianpian`, `forcetech`, `thunder`, `ijkplayer`. |
