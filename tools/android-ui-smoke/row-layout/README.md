# Leanback 横行首次测量回归

隔离测试包使用生产 `CustomRowPresenter` 和 `VodPresenter`，固定创建100行、每行5张无远程图片的卡片。`baseline=true`只将横向Grid的`hasFixedSize`恢复为旧值；正常模式使用生产设置。对比同一个APK和同一批数据，排除片源与网络差异。

```sh
./gradlew -I tools/android-ui-smoke/row-layout/init.gradle :app:assembleLeanbackJavaArm64_v8aDebug
adb install -r -t app/build/outputs/apk/leanbackJavaArm64_v8a/debug/leanback-java-arm64_v8a.apk
python3 tools/android-ui-smoke/row-layout/run.py --serial emulator-5554 --output /tmp/row-layout-results.json
adb uninstall com.fongmi.android.tv.rowbench
```

需要JDK17、Python3和Android SDK，`adb`在PATH中，或传`--adb /absolute/path/to/adb`。测试包名为`com.fongmi.android.tv.rowbench`，不清除用户App的数据。仅显式传入init脚本时包含测试Activity；不要分发此测试包。init脚本替换leanback manifest，测试包只用于直接启动测试Activity，不用于其他页面验收。

脚本交替运行三组前后模式，断言旧配置首次创建500张卡片、附着100行；生产配置仅创建少于50张卡片、附着少于10行。首次布局时间为提交adapter至首个有卡片的pre-draw，不能当作App完整启动耗时。实际可见行数与卡片尺寸、屏幕尺寸有关，阈值针对默认ARM64 TV模拟器。若生产配置回退为固定尺寸，正常模式的断言会失败。

2026-10-30 Android14 ARM64 TV模拟器：旧配置创建500张/100行，中位3126.764ms；生产配置创建20张/4行，中位183.529ms。所有500张卡片保留在adapter中，后续滚动由正常虚拟化布局创建。32位APK已构建验证，未在32位真机实测。
