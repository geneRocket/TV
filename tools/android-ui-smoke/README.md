# Android 手势释放回归检查

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
