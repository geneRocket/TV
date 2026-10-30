# TV 列表首屏性能修复

## 问题与原因

当前提交的模拟器记录中，首页内容返回后一次主线程doFrame持续1166.919ms，其中RV OnLayout占1064.849ms。该布局同步创建133个ViewHolder，创建和绑定累计828.893ms，实际执行CPU占布局时间98.4%。

实际依赖Leanback1.2.0/grid1.0.0的ListRow横向Grid使用wrap_content高度和wrap_content行高。GridLayoutManager.processRowSizeSecondary(true)在没有子View时，仅对`!hasFixedSize()`的Grid测量样本卡片。原先CustomRowPresenter将横向Grid标记为固定尺寸，使首次行高度缺少卡片尺寸信息，外层会先按过小的行高填充大量行，再同步创建这些行的卡片。

将横向Grid显式设为`setHasFixedSize(false)`，使首次测量获得真实卡片高度。不能只删除设置，因为BaseGridView默认就是true。固定视口的外层列表设置保留。该共享Presenter用于首页、分类与搜索结果等TV横行列表；没有截断结果、修改片源数据或改变网络超时。

## 可重复回归

隔离设备测试提供固定100行/500张卡片，对照同一个APK中恢复旧标志与生产标志的行为。三轮交替运行：

|配置|首次创建卡片|附着横行|首次布局中位数|
|---|---:|---:|---:|
|旧固定尺寸标志|500|100|3126.764ms|
|按内容测量|20|4|183.529ms|

创建数量减少96%，该测试首次布局耗时减少约94.1%。这是离线布局专项测试，不能外推为整个App启动提速94%。测试使用真实生产卡片Presenter，包含500项完整adapter数据，正常配置只创建视口所需卡片。测试脚本同时验证旧模式可复现和生产模式限制创建数量。

命令与边界见`tools/android-ui-smoke/row-layout/README.md`。

## 构建与单测

以下任务通过：

```sh
./gradlew :app:testLeanbackJavaArm64_v8aDebugUnitTest \
  :app:testMobileJavaArm64_v8aDebugUnitTest \
  :app:assembleLeanbackPythonArm64_v8aDebug \
  :app:assembleLeanbackJavaArmeabi_v7aDebug
```

电视端965项、移动端940项测试，均无失败、错误或跳过。32位电视版构建通过，32位真机运行性能尚未实测。生产改动仅在leanback源码，不改变手机端列表行为。WebView、DLNA初始化和网络不可达属于独立问题，本次未混入修改。

真实App修复包已安装并复录15秒，但片源未返回与基线相同的完整数据，因此不将这轮首屏或布局耗时作为等负载提速证据。后续检查确认历史和快捷功能可显示、遥控器可移动焦点；完整推荐列表与搜索结果仍需在片源正常时复测。修复效果的可比证据来自上述固定500卡片测试。
