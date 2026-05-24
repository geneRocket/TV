# Project Map

## Root

This is a multi-module Android Gradle project for the FongMi TV streaming app. The root `build.gradle` pins Android Gradle Plugin `8.7.3`, Kotlin Android plugin `2.1.10`, Chaquopy `14.0.2`, Java 11 compilation, and shared dependency versions for Gson, Jsoup, Media3, and OkHttp. The Gradle wrapper points at Gradle `8.9`.

The root `settings.gradle` includes:

- `:app`: final Android application.
- `:catvod`: CatVod crawler API, shared crawler models, OkHttp helpers, preferences/path utilities.
- `:quickjs`: JavaScript crawler runtime using QuickJS wrapper plus bundled JS libs in `quickjs/src/main/assets/js/lib`.
- `:chaquo`: Python crawler runtime using Chaquopy and Python files under `chaquo/src/main/python`.
- `:hook`: package manager hooks used by live core/package identity paths.
- `:tvbus`, `:zlive`, `:jianpian`, `:forcetech`, `:thunder`: native or SDK-backed protocol/download/P2P wrappers.
- `:youtube`: vendored/forked YouTube downloader and parser code.
- `:ijkplayer`: IJK player wrapper, JNI libs, and render views.

`other/sample` contains reference VOD/live config JSON. `app/libs` contains local AAR dependencies such as danmaku and DLNA. `app/schemas` contains Room schema exports.

## App Build

The `app` module has three flavor dimensions:

- `mode`: `leanback` for Android TV, `mobile` for touch UI.
- `api`: `java` excludes Python crawler runtime, `python` includes the Chaquopy-backed `:chaquo` dependency.
- `abi`: `x86`, `arm64_v8a`, `armeabi_v7a`.

Output APK names are generated as `${mode}-${api}-${abi}.apk`. Release builds enable minify and resource shrinking. `compileSdk` is `35`, `minSdk` is `23`, and `targetSdk` is intentionally `28`.

Python builds depend on `chaquo/build.gradle`, which is configured for Python `3.8` via `/Users/wuwenjun/.pyenv/shims/python3.8` and installs `lxml`, `ujson`, `pyquery`, `requests`, `jsonpath`, `pycryptodome`, and `beautifulsoup4`.

## Source Sets

- `app/src/main`: shared manifest, application class, common business logic, shared resources, web assets, player/config/server/db code.
- `app/src/leanback`: Android TV manifest, activities, fragments, dialogs, presenters, adapters, remote-control custom views, TV resources.
- `app/src/mobile`: mobile manifest, activities, fragments, dialogs, adapters, services, notification/media button support, mobile resources.
- `app/src/java`: no-op `com.fongmi.android.tv.api.loader.PyLoader` for Java flavor builds.
- `app/src/python`: real `PyLoader` bridge for Python flavor builds.
- `app/src/{x86,arm64_v8a,armeabi_v7a}/assets`: ABI-specific assets.

Because `leanback` and `mobile` define classes with the same packages and names, always edit the source set matching the target mode. Shared package names do not mean shared source files.

## Application Startup

`App.java` is the application class declared by the main manifest. It:

- Initializes CatVod via `Init.set(base)` in `attachBaseContext`.
- Creates app-wide executor and main-thread `Handler`.
- Initializes notifications, language, Logger, OkHttp proxy, and DoH.
- Sets custom crash activity handling.
- Tracks the current foreground `Activity`.
- Overrides `getPackageManager` and `getPackageName` when live core hooks are active.

Most background work should use `App.execute` and main-thread work should use `App.post` unless an existing ViewModel or module-specific executor already owns the task.

## Config And Crawler Flow

`VodConfig` and `LiveConfig` load config URLs or cached JSON, merge multiple config sources, parse `sites`, `lives`, `parses`, `headers`, `proxy`, `rules`, and `ads`, then expose active home entries. They also clear loader caches when configs change.

`BaseLoader` chooses the crawler runtime:

- API containing `.py`: `PyLoader`.
- API containing `.js`: `JsLoader`.
- API starting with `csp_`: `JarLoader`.
- Anything else: `SpiderNull`.

`JarLoader` downloads or resolves jar assets, creates `DexClassLoader`, calls `com.github.catvod.spider.Init.init(Context)` when present, caches `Spider` instances, and delegates jar `Proxy` and parser methods.

`JsLoader` uses `quickjs` module `Loader` and caches `Spider` instances by site/live key.

`PyLoader` is source-set dependent. Java flavor builds use a no-op implementation. Python flavor builds use `com.fongmi.chaquo.Loader`, set `spider.siteKey`, and call `spider.init`.

The app scopes site keys with config ids in `VodConfig.siteKey`, so avoid stripping prefixes unless calling `VodConfig.rawSiteKey` deliberately.

## UI

Leanback entry activity is `app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java`. Mobile entry activity is `app/src/mobile/java/com/fongmi/android/tv/ui/activity/MainActivity.java`.

Both modes provide their own `ui/base/BaseActivity`, fragments, adapters, dialogs, and custom views. ViewBinding is enabled. TV UI uses Leanback presenters and focus/key handling. Mobile UI uses Material components, bottom navigation, touch-first fragments, PiP/playback service pieces, and QR scan support.

Shared common UI classes under `app/src/main/java/com/fongmi/android/tv/ui` are limited; check source-set files first before assuming a behavior is shared.

## Playback And Local Server

Playback code sits under `app/src/main/java/com/fongmi/android/tv/player`:

- `Source`: URL normalization, fetch/parse orchestration, and extractor handoff.
- `ParseJob`: parse task coordination.
- `Players`: player selection constants and control.
- `player/exo`: Media3 ExoPlayer helpers, cache, media source factory, OkHttp data source.
- `player/extractor`: protocol/source extractors such as `Force`, `JianPian`, `Proxy`, `Push`, `Strm`, `TVBus`, `Thunder`, `Video`, `Youtube`, and `ZLive`.
- `player/danmu` and subtitle view models: danmaku/subtitle integrations.

The local server lives in `app/src/main/java/com/fongmi/android/tv/server`. README documents common endpoints on `127.0.0.1:9978`, including action refresh, cache get/set/delete, subtitle push, and danmaku push.

## Database

`AppDatabase` is a Room database named `tv`, currently version `31`. Entities include `Keep`, `Site`, `Live`, `Track`, `Config`, `Device`, `History`, and `Download`. Migrations from version `11` through `31` are defined directly in `AppDatabase.java`, and schema exports live in `app/schemas/com.fongmi.android.tv.db.AppDatabase`.

DAOs live under `app/src/main/java/com/fongmi/android/tv/db/dao`. Some entities have static caches; `AppDatabase.reset()` clears relevant caches.

## Config Samples

`README.md` documents VOD fields, live fields, style shapes, proxy rule format, and local server endpoints. Sample configs:

- `other/sample/vod/online.json`
- `other/sample/vod/offline.json`
- `other/sample/live/online.json`
- `other/sample/live/offline.json`
- `other/sample/live/tvbus.json`

When changing config semantics, update code, sample JSON, and README together.
