---
name: fongmi-tv-project
description: Project-specific guidance for the FongMi TV Android repository. Use when working in ~/code/TV or answering questions about its Android Gradle modules, leanback/mobile source sets, Java/Python build flavors, CatVod jar/js/py crawler loaders, playback and extractor flow, Room database schema, local server endpoints, native libraries, or safe build and verification commands.
---

# FongMi TV Project

## Overview

Use this skill as the onboarding map for the FongMi TV Android app. It keeps the project-specific structure, build variants, crawler runtimes, and verification habits close at hand before editing code.

## Start Here

1. Confirm the requested surface: app shell, leanback TV UI, mobile UI, Vod/Live config parsing, crawler loader, playback/extractor, database, native wrapper, or sample config.
2. Read `references/modules-and-features.md` when you need a module/function breakdown or want to locate the owner of a feature.
3. Read `references/project-map.md` when you need source-set layout, architecture landmarks, or data-flow details.
4. Read `references/workflows.md` before building, changing a flavor-specific UI, touching crawlers/loaders, changing Room entities, or editing playback behavior.
5. Prefer `rg` over broad file browsing. Ignore generated output directories such as `.gradle/`, module `build/`, `app/leanback*`, `.idea/`, `.DS_Store`, and `local.properties`.

## Project Shape

- Main app module: `app`.
- Included Gradle modules: `hook`, `tvbus`, `zlive`, `catvod`, `chaquo`, `quickjs`, `thunder`, `youtube`, `jianpian`, `forcetech`, `ijkplayer`.
- App flavor dimensions: `mode` (`leanback`, `mobile`), `api` (`java`, `python`), and `abi` (`x86`, `arm64_v8a`, `armeabi_v7a`).
- Common app code lives in `app/src/main/java/com/fongmi/android/tv`.
- TV-specific UI lives in `app/src/leanback`; phone/tablet UI lives in `app/src/mobile`.
- `app/src/java` contains the no-op `PyLoader`; `app/src/python` contains the real Chaquopy-backed `PyLoader`.

## High-Value Landmarks

- `app/src/main/java/com/fongmi/android/tv/App.java`: application init, CatVod init, OkHttp proxy/DoH, logger, crash handling, active activity tracking.
- `app/src/main/java/com/fongmi/android/tv/api/config/VodConfig.java` and `LiveConfig.java`: load, cache, merge, and parse VOD/live config data.
- `app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java`: dispatch crawlers by API type: `.py` to `PyLoader`, `.js` to `JsLoader`, `csp_` to `JarLoader`.
- `app/src/main/java/com/fongmi/android/tv/model/SiteViewModel.java` and `LiveViewModel.java`: async VOD/live calls, LiveData updates, timeout/cancellation behavior.
- `app/src/main/java/com/fongmi/android/tv/player`: Media3/IJK playback orchestration, parsing, cache, extractors, subtitles, danmaku.
- `app/src/main/java/com/fongmi/android/tv/server`: local NanoHTTPD server and README-documented `/action`, `/cache`, `/proxy`, `/parse`, and media endpoints.
- `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java`: Room database, schema version, migrations, backup/restore.

## Verification

Use exact variant tasks when possible:

```bash
./gradlew :app:assembleLeanbackJavaArm64_v8aDebug
./gradlew :app:assembleMobileJavaArm64_v8aDebug
./gradlew :app:assembleLeanbackPythonArm64_v8aDebug
./gradlew :app:lintLeanbackJavaArm64_v8aDebug
```

Run `./gradlew :app:tasks --all` or `./gradlew :app:sourceSets` when unsure about a variant or source-set merge. Python variants route through `:chaquo` and the configured Python 3.8 path in `chaquo/build.gradle`.

## Editing Rules Of Thumb

- Keep source-set boundaries clean: change shared behavior in `main`; change remote-control/TV layout in `leanback`; change touch/mobile behavior in `mobile`.
- Use existing helpers first: `App.execute`, `App.post`, `ThreadPools`, `OkHttp`, `UrlUtil`, `Notify`, `ResUtil`, `Prefers`, and EventBus patterns are already pervasive.
- If changing Room entities, update `AppDatabase.VERSION`, migrations, and `app/schemas`.
- If touching crawler dispatch, preserve cache keys and lifecycle cleanup in `BaseLoader`, `JarLoader`, `JsLoader`, and `PyLoader`.
- If editing playback or parsing, check whether the change belongs in `Source`, `ParseJob`, `Players`, `player/exo`, or a protocol extractor.
- Do not edit vendored/minified libraries, bundled native `.so` files, or local AARs unless the task explicitly requires it.

## References

- `references/modules-and-features.md`: Gradle module responsibilities, app package responsibilities, user-facing feature map, and key data flows.
- `references/project-map.md`: module map, source-set map, architecture landmarks, config and database notes.
- `references/workflows.md`: practical workflows for builds, UI changes, crawler/runtime work, playback changes, Room migrations, and sample config changes.
