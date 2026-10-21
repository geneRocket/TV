# 播放页面组件及直播/投屏控制重构

基线：`ad99dbfd8`。本轮按点播页面组件、直播控制、投屏控制检查与收敛，保留频道、EPG、遥控焦点、原播放内核与现有界面。

## 职责调整

- `VodEpisodeCatalog`：两端共用剧集顺序、scoped resume identity、历史起点和片头数据的绑定，不改持久化格式。
- `PlaybackVideoViews`：五个播放页面共用 Exo/IJK 显隐、内核标签与速度控件能力渲染；组件不触发播放、不修改偏好。
- `PlaybackSubtitleLauncher`：五个播放页面共用字幕弹窗延迟启动、搜索名称回退、页面/请求有效性与结果回调检查。
- `DirectPlaybackFlow`：TV/Mobile 直播及 TV DLNA renderer 共用语义输入、主线程串行分发、播放意图与生命周期权限、恢复决策、结束事件去重和来源代次。
- 页面仍保留频道/EPG 的具体选择、平台 PiP/方向/服务交互及界面效果。DirectFlow 的 Port 执行这些平台差异，不再由页面决定恢复动作次序。
- `PlaybackController.executeInput` 执行低层效果，避免 public 输入路由递归；prepare 通过 handler 的 playWhenReady 策略保留暂停/后台权限。点播 Flow 默认行为保持原样。
- 五个实际播放器创建点均有 input handler；`SessionCallback` 只向绑定 handler 投递，移除全局 EventBus 回退，旧 owner 或已释放 handler 不再把迟到命令送给新页面。
- 直播/投屏的 Handler 延迟任务纳入 CallbackScope；手机直播服务延迟清理保留 app 作用域，捕获旧 Controller 并按服务 owner 检查。直播两端共用 PlaybackArtwork，取消旧台标请求并拒收迟到结果。

Activity 没有被整体改写。列表数据绑定、字幕和内核展示组件已抽出；频道导航、平台 Port 仍可按后续具体功能继续拆分，不以文件行数作为正确性验收。

## 确认与修复的边界

1. TV 直播 onResume 原先无条件 play；现在前后台权限不覆盖用户暂停意图。暂停后 prepare/解码重建也受同一策略约束。
2. 手机直播原始台标加载没有请求归属，新频道可能被旧请求覆盖；复用已测试的共享图片作用域。
3. 投屏收到没有新 CastAction 的前台 Intent，原先仍 checkAction 并重启旧 URI。现只在携带新 CastAction 时重置来源。真实设备先复现 sourceGeneration 从 1→2 并自动播放；修复后前台往返保持 generation=1、PAUSED、位置 3581ms。`CastResumeTest` 在撤去 guard 时明确断言失败，恢复 guard 后通过。
4. 冷进程直接打开 CastActivity 时，构造 DIDLParser 依赖尚未建立的全局 SAX driver，实际抛出 SAXException 并进入 CrashActivity。移除 eager DIDLParser，`CastMediaTitle` 使用平台 SAXParserFactory 按需读取原显示用的第一个 item id；空/异常 metadata 回退 URI。坏 SAX driver 属性下的单测通过，Android 上有效 DIDL metadata 显示 `media-id`，画面正常。

## 自动化验证

- TV **737**、Mobile **725** Java 单测，共 **1462**；失败、错误、跳过均 0。
- 两端 Java arm64 Debug 构建成功。
- 两端 lint：0 Error/Fatal，TV 184、Mobile 296 条 Warning。
- 最终命令日志：`/tmp/tv-live-cast-refactor-final.log`；Cast Intent guard 失败回归：`/tmp/tv-cast-resume-red.log`。
- 两端普通 APK 边界探针均 `checks=9 defects=0`；Manifest applicationId 为 `com.fongmi.android.tv`，无 codextest / RefactorReceiver。
- 新测试覆盖：12 个 DirectFlow 场景（用户暂停/后台权限/准备策略/旧来源/销毁/恢复预算/重入/结束去重）、全部六种 MediaSession 输入的 handler 归属、剧集顺序与配置隔离、字幕名称回退、Cast Intent 和 metadata 解析。
- 原手机直播 seek UI 测试保留取消、零位移、暂停回看、时间线消失等断言；fixture 改为注入真实 DirectFlow 状态，而非 Controller 缓存。

## 实际页面验证及限制

在 API34 arm64 TV 模拟器中安装独立 applicationId 测试包，使用本地 H.264/AAC 视频及双频道 M3U。原应用安装包和数据未改。

- TV 直播：READY，640×360；MediaSession 暂停、后台/前台往返保持暂停；解码重建仍 PAUSED；系统下一台后新来源 READY。
- 手机直播：实际进入 LiveActivity 解码到 READY，系统暂停到 PAUSED。运行环境是 TV 模拟器；手机后台/PiP 完整设备矩阵本轮不计作通过，由共享意图测试覆盖其逻辑，后续仍需手机硬件验收。
- TV 投屏接收页面：实际本地视频播放、MediaSession 暂停、暂停字幕 reload、seek、继续播放；新的 URI 重置来源；空前台 Intent 保持来源和暂停；有效 DIDL metadata 名称显示正确。
- 使用测试 Receiver 构造 CastAction 验证接收页面及本地 renderer 回调；没有真实手机/局域网控制器，因此不声称验证 SSDP 发现、网络 SOAP 控制或所有 DLNA 发送端。
- 设备无前台窗口时的 Receiver 启动会被 Android 后台启动限制拦截；这些尝试不计作播放验证。验收通过场景先启动测试应用的前台页面。

正常构建输出已恢复。提交前清理独立测试包、reverse、本地 fixture 服务器和设备临时探针；只做本地提交，不推送。
