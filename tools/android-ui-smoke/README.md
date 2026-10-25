# Android 手势释放回归检查

字幕扩展验证（隔离包，应用需先进入前台，TV 启动 HomeActivity、手机启动 MainActivity）：

```sh
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py subtitle-screen
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py subtitle-options
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py subtitle-live
# 手机使用 TV_SMOKE_PACKAGE=com.fongmi.android.tv.codextest.mobile
```

`subtitle-options` 实际点击来源和语言菜单，检查粤语排序及网站二维码；结束后保留弹窗供目检。`subtitle-live` 查询三部港片，报告各站结果并下载直接字幕检查时间轴，依赖公网而非稳定回归夹具。网站验证下载仍需人工操作。这三项无需本地媒体服务。遇到系统首次全屏提示先按实际界面关闭。本轮使用 API34 Google APIs 临时只读 AVD，未关闭 Play Protect。结果与限制见 [字幕搜索扩展](/Users/wuwenjun/code/TV/docs/subtitle-search-expansion-2026-10-25.md)。

## 播放所有权与恢复验收

按下文“隔离的实际播放验收包”构建/安装测试包、启动对应首页与本地 fixture_server.py，并完成 `adb reverse tcp:18765 tcp:18765` 后运行：

```sh
export ANDROID_SERIAL=emulator-5554
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py paused
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py playing
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py live
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py ijk
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py page
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py config

# 切换到已安装的手机隔离包，并先启动其 MainActivity。
export TV_SMOKE_PACKAGE=com.fongmi.android.tv.codextest.mobile
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py mobile-paused
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py mobile-playing
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py page
python3 tools/android-ui-smoke/refactor-device/ownership_smoke.py config
```

脚本仅使用隔离包和本地视频。验证目标包括用户暂停、2倍速、30秒附近进度、引擎/页面切换和返回后的播放状态，并输出JSON。启动阶段若另一配置加载覆盖了测试配置加载，先等待首页初始化结束再重跑；不要将测试 Receiver 的广播返回等同于异步配置完成。手机测试在Android TV模拟器上只验证手机页面逻辑，不代表手机硬件/PiP验收。结束后卸载隔离包、移除reverse并重新构建普通APK。

本轮结构与证据见 [架构重构记录](../../docs/playback-architecture-refactor-2026-10-24.md)。

后续配置、存储、Presenter与串行执行边界见[边界重构记录](../../docs/architecture-boundaries-2026-10-25.md)。`page`模式用本地配置验证加载失败时保留旧配置，并先定位Working路线第1集，再切第2集，避免历史已选中第2集导致没有实际切集。

`config`模式验证点播内嵌直播的关联发布，再混合加载文本、JSON及失效直播地址，确认有效源保留且两个源均能实际播放。

`subtitle-screen`模式通过隔离包注入 Provider 数据，实际点击字幕弹窗按钮和列表，验证翻页失败保留列表、重试、文件列表分页禁用及选择回调。HTTP/站点解析由 JVM 测试独立验证。

`wallpaper`模式需要 fixture server 和 reverse 18765，加载仓库图标作为本地图片，再验证 HTTP 失败和图片解码失败均保留当前配置 URL 与图片散列。后续边界见[字幕与壁纸重构记录](../../docs/architecture-subtitle-wallpaper-2026-10-25.md)。

`GestureDetectorSmoke.java` 使用 Android 真实消息循环验证：调用手势处理器的 release 后，排队的长按与单击确认回调不再执行。TV 额外检查投屏临时倍速、点播亮度/音量的结束回调只执行一次。

处理器由反射创建，注入真实 GestureDetector 和回调计数器；不创建应用 Activity、不安装 APK、不接触应用数据。测试覆盖 release 与 Android 消息队列的配合，不能代替实际播放、遥控器焦点和触摸操作验收。

## 2026-10-20 修复与验证

- 根因：清理业务标志没有取消 GestureDetector 内部排队的消息。退出/锁定后，长按和单击确认仍可能触发旧页面的回调。
- 手机点播、直播与 TV 点播、直播、投屏的 release 统一发送 ACTION_CANCEL，取消系统手势识别器的排队消息。
- TV 投屏释放时结束临时倍速；TV 点播释放时结束亮度、音量提示，重复释放不重复通知。
- TV 直播在 onPause 调用 release，同时取消未完成的数字选台任务；该页面接线经过静态追踪和构建验证，未做数字选台的真实遥控器测试。
- arm64 API 34 模拟器：旧代码手机 4 项、TV 9 项失败；修复后全部通过。
- TV 682 项、手机 664 项 Java 单测通过，0 失败/错误/跳过；对应 Debug APK 构建成功。
- TV lint 0 错误、184 条警告；手机 lint 0 错误、296 条警告；git diff --check 通过。
- 没有运行实际视频播放、全屏切换或焦点验收；未修改 APK 压缩配置或删减功能。

## 运行

先构建对应 Debug APK，设置 JDK 17+ 与 `ANDROID_HOME`，在仓库根目录执行。下例使用 Android SDK 36 与 Build Tools 36.0.0，以及已连接的 arm64 设备/模拟器。

```sh
work_dir="$(mktemp -d)"
mkdir -p "$work_dir/classes" "$work_dir/dex"
javac -source 8 -target 8 \
  -classpath "$ANDROID_HOME/platforms/android-36/android.jar" \
  -d "$work_dir/classes" tools/android-ui-smoke/GestureDetectorSmoke.java
"$ANDROID_HOME/build-tools/36.0.0/d8" --min-api 24 \
  --lib "$ANDROID_HOME/platforms/android-36/android.jar" \
  --output "$work_dir/dex" "$work_dir"/classes/*.class
adb shell mkdir -p /data/local/tmp/tv-gesture-timers
adb push "$work_dir/dex/classes.dex" /data/local/tmp/tv-gesture-timers/classes.dex

# TV：预期 9 项通过。
adb push app/build/outputs/apk/leanbackJavaArm64_v8a/debug/leanback-java-arm64_v8a.apk /data/local/tmp/tv-gesture-timers/app.apk
adb shell 'CLASSPATH=/data/local/tmp/tv-gesture-timers/classes.dex:/data/local/tmp/tv-gesture-timers/app.apk app_process /system/bin GestureDetectorSmoke tv'

# 手机：预期 4 项通过。
adb push app/build/outputs/apk/mobileJavaArm64_v8a/debug/mobile-java-arm64_v8a.apk /data/local/tmp/tv-gesture-timers/app.apk
adb shell 'CLASSPATH=/data/local/tmp/tv-gesture-timers/classes.dex:/data/local/tmp/tv-gesture-timers/app.apk app_process /system/bin GestureDetectorSmoke mobile'

# 清理设备上的测试副本。
adb shell rm /data/local/tmp/tv-gesture-timers/app.apk /data/local/tmp/tv-gesture-timers/classes.dex
adb shell rmdir /data/local/tmp/tv-gesture-timers
```

`delta=0` 表示回调次数符合预期，进程退出码 0 表示全部通过。仓库外的 `work_dir` 仅包含本次编译产生的测试文件，可自行清理。

## 点播控制边界诊断（2026-10-21）

`PlaybackBoundaryAudit.java` 调用实际 APK 中的 Controller、Flow、Coordinator、Session 和 Exo 适配方法，用替身控制外部播放器/会话。检查字幕 reload 的暂停意图、字幕会话先挂接后 prepare、Exo PREPARING seek 快照，以及实际 CustomSeekView / SessionCallback 的 seek、暂停、播放、取消拖动和旧 owner 拦截，共 9 项。

它不创建应用 Activity、不安装 APK、不读取应用配置或真实媒体。默认模式输出诊断结果；加 `--expect-fixed` 后，发现缺陷即返回 1。历史基线 `4e70e8ba8` 使用当时三项探针，两端均 `checks=3 defects=3`；2026-10-22 重构后的两端普通 APK 均 `checks=9 defects=0`。新增 Flow API 的当前探针不能直接套在旧基线上运行。

使用前述 Android SDK/JDK 环境，在仓库根目录执行：

```sh
audit_dir="$(mktemp -d)"
mkdir -p "$audit_dir/classes" "$audit_dir/dex"
javac -source 8 -target 8 \
  -classpath "$ANDROID_HOME/platforms/android-36/android.jar" \
  -d "$audit_dir/classes" tools/android-ui-smoke/PlaybackBoundaryAudit.java
"$ANDROID_HOME/build-tools/36.0.0/d8" --min-api 24 \
  --lib "$ANDROID_HOME/platforms/android-36/android.jar" \
  --output "$audit_dir/dex" "$audit_dir/classes/PlaybackBoundaryAudit.class"
adb shell mkdir -p /data/local/tmp/tv-boundary-audit
adb push "$audit_dir/dex/classes.dex" /data/local/tmp/tv-boundary-audit/audit.dex

# 可换成 mobile 对应 APK，分别检查两端。
adb push app/build/outputs/apk/leanbackJavaArm64_v8a/debug/leanback-java-arm64_v8a.apk /data/local/tmp/tv-boundary-audit/app.apk
adb shell 'CLASSPATH=/data/local/tmp/tv-boundary-audit/audit.dex:/data/local/tmp/tv-boundary-audit/app.apk app_process /system/bin PlaybackBoundaryAudit --expect-fixed'

# 即使上一步返回 1，也应清理设备测试副本。
adb shell rm /data/local/tmp/tv-boundary-audit/audit.dex /data/local/tmp/tv-boundary-audit/app.apk
adb shell rmdir /data/local/tmp/tv-boundary-audit
```

这些是控制边界的逻辑检查；真实视频验证另见 [实施验收记录](/Users/wuwenjun/code/TV/docs/vod-refactor-validation-2026-10-22.md)。历史分析见 [审查报告](/Users/wuwenjun/code/TV/docs/videoactivity-refactor-review-2026-10-21.md)。

## 隔离的实际播放验收包

`refactor-device/init.gradle` 仅在显式 `-I` 使用时将测试 Receiver 加入 debug 源集，分别使用 `com.fongmi.android.tv.codextest.tv` / `com.fongmi.android.tv.codextest.mobile`。安装这两个包，不覆盖原应用；不要把测试 Receiver 加到正常 Manifest。测试包改写 QuickJS Provider authority 以允许并存，因此此验收不覆盖该 Provider 的真实 URI 行为。

```sh
./gradlew -I tools/android-ui-smoke/refactor-device/init.gradle \
  :app:assembleLeanbackJavaArm64_v8aDebug :app:assembleMobileJavaArm64_v8aDebug
python3 tools/android-ui-smoke/refactor-device/fixture_server.py --media /absolute/path/fixture.mp4
adb -s DEVICE reverse tcp:18765 tcp:18765
# 安装对应测试 APK，先启动 TV HomeActivity 或手机 MainActivity。
adb -s DEVICE shell am broadcast \
  -n com.fongmi.android.tv.codextest.mobile/com.fongmi.android.tv.test.RefactorReceiver \
  --es cmd open --es url http://127.0.0.1:18765/fixture.mp4
```

Receiver 的 `state` 返回真实 Flow/引擎状态；`mediaPause/mediaPlay/mediaSeek` 调用 MediaSession transport，`seekBar` 调用真实进度条回调。`player/subtitle/route/decode` 用于定位原页面入口，不等同于手指或遥控器实际点击；焦点、PiP 系统按钮应另外操作设备验证。

受控双路线验证：先发 `cmd=config url=http://127.0.0.1:18765/config.json`，等待配置加载，再发 `cmd=open key=fixture url=fixture`。首路线始终 404，第二路线返回本地视频；视频至少两集，以便验证自动下一集。此配置仅写入独立测试包的数据。

测试结束卸载两个 codextest 包、移除设备 reverse 并停止本地服务器。最后不用 `-I` 重新构建普通 APK，确认 applicationId 为 `com.fongmi.android.tv` 且 Manifest 不含 RefactorReceiver。正常 APK 与测试 APK 使用相同输出文件名，不可混淆。

直播和投屏接收页面也可复用此隔离包：`configLive` 的 url 为 `/live.m3u`，配置完成后 `openLive`；TV 包可用 `openCast` 携带视频 url 与可选 DIDL `metadata`。`state` 返回 DirectPlaybackFlow 阶段、来源代次及自动播放权限，投屏额外返回 rendererState 和标题；`mediaNext/mediaPrevious` 通过真实 MediaSession 切台。先让测试应用在前台，再发启动页面的广播，避免 Android 后台启动限制。实际覆盖与未验证范围见 [本轮记录](/Users/wuwenjun/code/TV/docs/playback-components-live-cast-2026-10-22.md)。

频道/EPG 验收可将设备 `date +%s` 的结果作为 `/live.m3u?epoch=...` 参数，让 XMLTV 的节目时间与设备一致。此接口包含跨组频道、tvg-id/name 不同的频道及当前/未来节目。`state` 增加频道、组、EPG 文本与条数；`replayFuture` 检查无效节目点击，`replayCurrent` 选择首条节目，`across` 开启跨组切台；投屏 state 增加 rendererConnected。验收边界见 [数据与连接拆分记录](/Users/wuwenjun/code/TV/docs/live-navigation-data-renderer-2026-10-22.md)。

真实直播接口验收时，先等待 `state.liveConfigStatus=success`，不要把 `configLive` 广播返回当成异步加载完成。`liveSources` 表示直播源数量；已有 LiveActivity 可用 `liveHome value=源索引` 走页面的源切换入口。TV 可用 `liveSelect group=组索引 channel=频道索引` 走页面的定位/播放入口（收藏组通常占索引 0）。`removeAd value=true` 开启广告过滤，state 同时返回开关值。实际解码以 READY、playing、视频尺寸和画面为证；直播再检查媒体序号随时间递增。公开列表可访问不代表其中每个媒体地址可用。示例结果见 [真实接口验收](/Users/wuwenjun/code/TV/docs/live-api-validation-2026-10-23.md)。

点播 `config` 对应 `state.vodConfigStatus` 与 `vodSites`；`vodProbe key=站点key` 异步探测 home/homeVideo，state返回数量与第一条详情条目，便于用真实ID执行open。当前Exo MIME输出在state.mimeType。证书日期错误先校准设备时间，不要把旧强制停止日志当作播放器崩溃。饭太硬案例见 [排查记录](/Users/wuwenjun/code/TV/docs/fty-exo-playback-fix-2026-10-24.md)。
