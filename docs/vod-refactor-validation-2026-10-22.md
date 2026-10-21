# 点播控制层重构验收

基线：`4e70e8ba8`；实现覆盖 Leanback / Mobile Java。交付范围为本地提交，不推送。

## 目标与实现对应

| 要求 | 实现与证据 |
|---|---|
| 统一播放输入 | `PlaybackInput`、`VodPlaybackFlow`；按钮/手势/键盘/ActionEvent 经页面入口，TimeBar / MediaSession / Controller 字幕经 inputHandler，最终进入同一协调器。两端没有原始会话订阅，也没有直接调用协调器 play/pause/seek/reload/next/previous/stop/permission。 |
| 暂停字幕不得自动播放 | Flow 保存播放意图、速度、位置，再更新字幕、挂接 delegate、reload。旧 Live/Cast Controller 字幕路径也显式保留 playWhenReady 和速度。原基线探针失败，当前两端普通 APK 探针通过；实际暂停字幕场景通过。 |
| IJK→Exo 状态不能脱节 | `VodSessionAdapter` 只选择/挂接，不 prepare；由 Flow 随后 reload。真实内核切换后最终 PAUSED，非 RELEASED，位置保留。 |
| PREPARING seek 快照一致 | `ExoPlayerEngine.seekTo` 更新并钳制 requestedStartPosition。真实适配器回归修复前失败，修复后与 APK 探针均为 40000ms。 |
| 共享恢复、换源、历史 | `VodRecoveryFlow` 统一恢复预算和推进顺序；`VodSourceSearch` / `VodSourcePolicy` / `VodSourceSwitchState` 统一搜索与候选策略；`VodHistoryController` 与 Flow 历史门控共享。TV 原专属类已迁移/删除。 |
| 共享生命周期与归属 | Flow 串行队列、主线程投递、请求代次过滤；用户意图与前后台播放权限分开；关页 close 取消订阅/恢复/搜索。系统媒体、字幕与轨道回调有 owner/session/request 检查。 |
| Activity 消费同一状态 | 自动结束/循环/相邻集、片头片尾跳转、错误恢复已迁至共享 Flow；手机不再观察原始会话。缓冲 UI 也读取 Flow 快照，TV 展示层不再推进恢复状态。 |
| 异步 UI 不污染新页面 | `CallbackScope` 管 Handler 任务；`PlaybackArtwork` 管图片代次；弹幕已有 requestId；字幕/弹幕文件/TrackDialog 增加请求或会话归属。对应 reset/close/旧结果测试通过。 |

页面仍保留 ViewBinding、焦点、平台方向/PiP、列表选择呈现、对话框，以及与数据/解析 API 连接的 Port；本轮没有更换播放内核、UI 框架或重写整个 Activity。用户手动输入与自动恢复保留不同的预算行为。

## 状态与事件验证

播放阶段继续使用 IDLE、RESOLVING、PREPARING、BUFFERING、READY、PAUSED、ENDED、ERROR、STOPPED、RELEASED；恢复窗口独立，不把全屏、暂停、后台等交叉组合扩成一个枚举。

`VodPlaybackFlowTest` 覆盖暂停/播放字幕、替换旧 delegate、手动/自动解码预算、准备中 seek、后台权限、重入队列、旧 owner、关闭、延迟状态代次、结束去重/循环、片尾重复 tick。`VodRecoveryFlowTest` 覆盖预算耗尽/READY 后用户控制/无后续动作/取消销毁。源策略覆盖 384 种快照组合；CallbackScope / Artwork / SessionOwnership 覆盖旧回调与重复调度。

补充发现：页面接收到 Flow 的最新快照时，Controller.playerState 缓存仍可能是上一事件，造成缓冲提示判断不一致。两端新增 `bufferingUiUsesFlowSnapshotInsteadOfControllerCache`，修复前均失败，修复后通过。TV Binder 的重复恢复状态回调同时移除，恢复窗口由 Flow 独占。

## 构建与自动化结果

- 完整 Java 测试：TV **717**、Mobile **706**，合计 **1423**，失败/错误/跳过均 0。
- 两端 Java arm64 Debug 构建成功；两端 lint 0 错误/致命问题，TV 184、Mobile 296 条警告。
- 最终完整命令日志：`/tmp/tv-refactor-final-normal.log`；最新缓存 UI 失败回归日志：`/tmp/tv-refactor-stale-ui-red.log`。早期 Exo 失败回归：`/tmp/tv-refactor-engine-red.log`。
- 提交前重新执行两端完整单测、构建、lint，结果成功，计数保持 717/706；普通 APK Manifest 再次确认无测试组件。日志：`/tmp/tv-refactor-precommit.log`。
- 两端普通 APK 的 PlaybackBoundaryAudit：各 **checks=9 defects=0**。此层调用真实适配入口，外部引擎用替身控制；不冒充实际播放验收。
- 普通 APK Manifest 检查：applicationId 为 `com.fongmi.android.tv`，无 RefactorReceiver / codextest，QuickJS authority 保持原值。

## 实际播放验收

使用 opt-in 测试 Receiver 和独立 applicationId，不覆盖原安装包、不操作原应用数据。媒体为本地生成的 180 秒 H.264/AAC 640×360 测试视频；本地 type4 接口提供 Broken（404）和 Working 两路线、两集。测试包 Provider authority 为避免安装冲突而改写，因此不覆盖 QuickJS URI 行为。

| 平台/场景 | 实测结果 |
|---|---|
| API34 arm64 Google TV，正常 Exo 播放/自然结束 | 解码尺寸 640×360，READY/playing=true；完整播放到 ENDED。 |
| 两端系统播放暂停、真实 TimeBar seek | 暂停生效；seek 目标生效，缓冲完成回 PAUSED，未自动播放。 |
| 两端暂停字幕 / IJK→Exo 字幕 | 暂停保持；delegate 兼容切换最终 PAUSED，位置保留。 |
| TV 当前路线再次进入全屏、返回 | request 不重启、视频尺寸正常；回小窗仍正常播放。 |
| TV 遥控 DPAD_CENTER/左右/返回与焦点 | 实际注入按键，暂停/退出全屏后焦点落在 video；截图可见有效视频帧，非黑屏。 |
| TV/Mobile 坏路线自动恢复 | 分别 request=8/10 后 Working 路线 READY；恢复为 STABILIZING。TV 此时按返回正常退出至 RELEASED，没有被“停止切换”拦截。 |
| Mobile 暂停→后台→回来 | PAUSED 保持。 |
| API34 arm64 手机模拟器，PiP 与系统按钮 | 真实 PiP 可见视频；点系统暂停后展开，PAUSED 保持。 |
| 手机横屏→竖屏 | fullscreen 随方向变化，暂停与位置保持。 |
| 手机结束→自动下一集→销毁 | 从 Episode 1 seek 至结尾后只推进至 Episode 2，request=11，READY；finish 后 RELEASED。 |

手机场景使用本地已有 google_apis 镜像建立的临时 AVD。测试 Receiver 直接调用页面入口与系统传输入口，不能声称每个按钮都经过物理点击；真实点击覆盖 TV 遥控器和手机系统 PiP 控件。按钮、回调静态登记见历史入口清单。未覆盖不同厂商硬件解码器、真实网络抖动、外部字幕搜索服务、投屏设备及所有音视频格式；这些属于本轮环境之外的兼容性范围。

## 复核与边界

最后缓冲 UI 修复后，两端测试包重新安装，seek 到 40000 后回 PAUSED，再播放回 READY，progress=GONE。TV 一度 ADB 传输超时，reconnect 后 reverse 映射已丢失，重建映射后复测通过；不把超时和无媒体连接的尝试计作成功。

普通构建输出已恢复，并重新运行两端九项 APK 探针、检查普通 Manifest；设备测试包/reverse/探针文件、临时手机模拟器及注册、本地服务器已清理。原 TV 模拟器保留。历史审查报告保持原始基线结论，行号只对应当时版本；实施状态以本文与进度记录为准。
