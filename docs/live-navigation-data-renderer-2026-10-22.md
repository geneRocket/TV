# 频道、EPG、列表与投屏连接的职责拆分

基线：`6062e59d1`。继续上一轮控制流重构，本轮把频道导航、数据订阅、列表列宽和 renderer 服务连接迁出 Activity。

## 组件边界

| 组件 | 负责 | Activity 保留 |
|---|---|---|
| LiveChannelNavigator | 上/下频道、组内循环、跨组、收藏组跳过、组切换起点 | 具体 Adapter 更新、焦点/滚动、发起频道播放 |
| LiveDataConnection | 四类 LiveData 订阅与解绑、URL 归属过滤、EPG 身份匹配、XML 完成刷新、回看前置校验 | 选择当前频道、更新 View、播放返回的 Channel |
| LiveListLayout | 分组/频道/EPG 三列的测量、宽度、分隔线与重置 | Adapter 内容、选中态、遥控焦点 |
| CastRendererConnection | bind/unbind、服务连接/断开、连接后同步最新状态、销毁后拒收迟到连接 | RenderControl 对 UI 控制流的转换、界面展示 |

TV 与手机继续使用不同的字体、Logo 占位和 EPG padding；移动端 URL/EPG 保留后台订阅能力，另外两类仍跟随页面生命周期。页面销毁统一 close；连接采用显式 connect，确保 LiveData 缓存同步回调时字段已赋值。

TV LiveActivity 从 1293 行降至 1214 行，Mobile 从 1318 行降至 1259 行；CastActivity 也不再实现 ServiceConnection 或持有 Binder/绑定标志。核心结果是职责和生命周期边界，不以行数代表完整度。平台焦点与方向/PiP 等仍留在页面。

## 同步修复

- 两端原先在校验未来节目/回看能力前 beginSource，失败的点击也会清除用户暂停意图。现在由 LiveDataConnection.replay 先校验，再运行 UI/播放副作用。两端 Activity 入口测试在修复前均失败，修复后通过；设备上来源代次、暂停与位置均保持。
- 手机原先用 tvg-name 匹配 EPG，而 LiveViewModel/XML 解析返回 tvg-id。统一按 getTvgId 匹配；getTvgId 自带名称回退。设备使用 id=`fixture-id`、name=`Fixture Name`，两端均显示 Current fixture 节目。
- 手机 URL 结果现在同 TV 一样检查频道组引用、线路及当前 URL，旧来源结果不能覆盖新频道。
- 配置加载成功/失败的迟到回调也检查数据连接是否关闭，销毁后不再重启页面加载或显示旧错误。
- Renderer 连接即使晚于状态更新，也会同步最新播放状态；close 后的迟到连接不重新挂接已销毁页面。服务断开不丢失“仍需解绑”的事实。

## 测试与构建

- TV **758**、Mobile **740**，共 **1498** 项 Java 单测；失败/错误/跳过均 0。
- 两端 arm64 Java Debug 构建成功，lint 零 Error/Fatal；原有警告 TV184 / Mobile296。
- 频道导航测试覆盖组内双向循环、跨组跳过收藏、显式浏览收藏、仅一个可播组、空收藏组及无组。
- 数据连接测试覆盖后台/生命周期订阅选择、订阅去重、同步缓存回调中 close、旧组/线路/URL、id/name 不同的 EPG、XML 刷新、无效节目、回看 UI 与请求顺序、关闭期间抑制请求。
- 服务连接测试覆盖重复 bind、最新状态同步、重复 close、迟到连接、断开重连、绑定失败，以及从旧 Activity 测试迁移的“断开后仍需解绑/断开不创建绑定”断言。
- 原有 LiveSeekUi、CastResume 等回归继续执行，未删减行为断言。
- 完整日志：`/tmp/tv-live-components-final.log`；无效回看失败回归：`/tmp/tv-live-replay-selection-red.log`。

## 实际页面验证

API34 arm64 TV 模拟器，独立测试包，受控本地视频/M3U/XMLTV；未修改原安装包和原数据。

- 两端直播 READY，解码 640×360；id/name 不同的频道显示两条 EPG 和当前节目。
- 两端暂停后点击未来节目：仍 PAUSED，来源代次未变，位置未变。
- TV 连续下一台从 Fixture/One 经 Two 到 Other/Three，最终正常解码播放。
- 投屏接收页面 READY，rendererConnected=true；暂停后 rendererState 与 Flow 同步为 PAUSED。一次启动观测超时后重取状态成功，日志无应用 fatal/ANR；不把超时本身当成通过。
- 手机界面运行于 TV 模拟器，不覆盖手机硬件完整触摸/PiP 矩阵；本轮没有局域网 DLNA 控制器，不宣称验证 SSDP/SOAP 互通。

普通 APK 已恢复并检查：applicationId 为 com.fongmi.android.tv，无测试 Receiver/codextest。提交前清理测试包、reverse 与本地媒体服务器；本地提交，不推送。
