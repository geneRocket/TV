# Workflows

## Before Editing

Run focused searches rather than broad reads:

```bash
rg -n "SymbolOrMethod" app/src/main app/src/leanback app/src/mobile
rg -n "api|csp_|spider|proxy" app/src/main/java/com/fongmi/android/tv quickjs chaquo catvod
./gradlew :app:sourceSets
```

Check `git status --short` before editing. The worktree may contain user changes; do not revert unrelated files.

## Build And Verification

Use a narrow variant that matches the changed surface:

```bash
./gradlew :app:assembleLeanbackJavaArm64_v8aDebug
./gradlew :app:assembleMobileJavaArm64_v8aDebug
./gradlew :app:assembleLeanbackPythonArm64_v8aDebug
./gradlew :app:lintLeanbackJavaArm64_v8aDebug
```

Use Java variants for most app, UI, DB, and playback work unless the change touches Python crawler support. Use Python variants when changing `app/src/python`, `chaquo`, Python spider bridges, or Python-only behavior. Run `--dry-run` if a task name is uncertain.

Expect Gradle to warn that `variant.getMergeAssets()` is obsolete in `app/build.gradle`; this is existing project behavior.

## UI Changes

Identify the target mode first:

- Leanback TV: edit `app/src/leanback`, favor focus-safe layouts, key handlers, presenters, and landscape assumptions.
- Mobile: edit `app/src/mobile`, favor touch layouts, bottom navigation, PiP, playback service, and portrait/flexible orientation behavior.
- Shared resource or behavior: edit `app/src/main` only when both modes truly need the same change.

For new screens, follow the local pattern:

- Activity/fragment extends the mode-specific `ui/base/BaseActivity` or `BaseFragment`.
- Inflate via ViewBinding.
- Register EventBus only through the existing base class behavior unless a local class already does more.
- Keep all UI updates on the main thread via LiveData observers or `App.post`.

After layout changes, build the affected variant. If both modes share resources or model behavior, build one leanback and one mobile variant.

## Config Loading Changes

For VOD config behavior, start in `VodConfig.java`. For live config behavior, start in `LiveConfig.java`. Then trace into:

- `Decoder` for config text acquisition and encoded config support.
- `bean` classes such as `Config`, `Site`, `Live`, `Parse`, `Rule`, `Header`, and `Proxy`.
- `AppDatabase`/DAOs if loaded config is persisted.
- README and `other/sample` if the public config schema changes.

Preserve cache fallback behavior: remote load failures often intentionally fall back to cached JSON before posting callback errors.

## Crawler And Loader Changes

Treat crawler runtimes as three separate paths behind `BaseLoader`:

- Jar/CSP: `JarLoader`, `catvod`, external spider jars, `csp_` API names.
- JavaScript: `JsLoader`, `quickjs/src/main/java`, `quickjs/src/main/assets/js/lib`.
- Python: flavor-specific `PyLoader`, `chaquo/src/main/python`, and Chaquopy dependency setup.

When changing loader keys or lifecycle behavior:

- Preserve `site:` and `live:` key distinction.
- Preserve `api + ext` hashing in `BaseLoader`.
- Destroy cached spiders on clear.
- Keep `recent` behavior working for proxy and parser calls.
- Test both Java and Python variants if touching `BaseLoader` signatures.

## Playback And Extractor Changes

Start with the owner of the behavior:

- URL parse/fetch selection: `Source` and `ParseJob`.
- Player selection and constants: `Players`, `Setting`, and player UI classes.
- Media3 data sources/cache/renderers: `player/exo`.
- Protocol extraction: `player/extractor`.
- IJK player view/render details: `ijkplayer`.
- Native/P2P SDK wrappers: module matching the extractor, such as `tvbus`, `zlive`, `forcetech`, `jianpian`, or `thunder`.

Playback calls often run asynchronously and must tolerate cancellation, timeouts, and activity changes. Prefer existing timeout constants in `Constant.java`.

## Room Database Changes

When adding or changing an entity field:

1. Update the entity and DAO as needed.
2. Increment `AppDatabase.VERSION`.
3. Add a new `Migration` block and register it in `create`.
4. Regenerate or update schema JSON under `app/schemas`.
5. Build a variant to run Room annotation processing.

The project uses `allowMainThreadQueries` and static caches in some entities. Avoid introducing extra background assumptions without checking callers.

## Python Runtime Changes

Python flavor builds use:

- `app/src/python/java/com/fongmi/android/tv/api/loader/PyLoader.java`
- `chaquo/src/main/java/com/fongmi/chaquo/Loader.java`
- `chaquo/src/main/java/com/fongmi/chaquo/Spider.java`
- `chaquo/src/main/python/app.py`
- `chaquo/src/main/python/base/spider.py`
- `chaquo/src/main/python/runner.py`
- `chaquo/src/main/python/trigger.py`

Keep backward compatibility for spider method arity. The Python bridge already checks whether `searchContent` and `liveContent` accept newer argument counts.

## Sample Config And README Changes

When changing user-facing config fields:

- Update parsing in `VodConfig`, `LiveConfig`, or related beans.
- Update `README.md` field tables.
- Update at least one matching sample in `other/sample/vod` or `other/sample/live`.
- Keep JSON valid and minimal.

## Common Search Shortcuts

```bash
rg -n "siteKey|rawSiteKey|scope" app/src/main/java/com/fongmi/android/tv
rg -n "Spider|SpiderNull|proxyInvoke|parseJar" app/src/main/java/com/fongmi/android/tv/api/loader quickjs chaquo catvod
rg -n "RefreshEvent|EventBus|MutableLiveData" app/src/main app/src/leanback app/src/mobile
rg -n "@Database|@Entity|Migration|VERSION" app/src/main/java/com/fongmi/android/tv
rg -n "127.0.0.1|Nano|Server|process" app/src/main/java/com/fongmi/android/tv/server README.md
```
