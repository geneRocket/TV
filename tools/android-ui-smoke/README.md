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
