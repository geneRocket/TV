# 点播控制层重构进度

目标按 `videoactivity-refactor-review-2026-10-21.md` 实施，覆盖 TV 和手机，不缩减为三个局部补丁。

## 验收要求

- [x] 阶段一：按钮、手势、TimeBar、系统媒体和字幕走共享操作入口。
- [x] 字幕 reload 保留暂停、速度、位置；IJK→Exo 先挂接会话再 prepare，协调器最终 READY/PAUSED。
- [x] Exo PREPARING seek 后快照返回最新目标。
- [x] 阶段二：共享用户控制/自动恢复/换源/生命周期策略；迟到事件和重入有明确归属。
- [x] 阶段三：两端页面消费统一点播状态，业务策略迁出，页面保留渲染、焦点和平台交互。
- [x] 图片、弹幕、对话框和延迟回调按页面/影片代次隔离。
- [x] 所有已确认问题有失败回归和修复后证据；真实入口和适配方法纳入验证。
- [x] 两端 Java 单测、Debug 构建、lint；设备覆盖 Exo/IJK、返回、失败恢复、旋转/PiP/后台；迟到回调用受控队列/真实适配入口测试。

## 当前权威状态

2026-10-22：三阶段实现与验证结果见 [实施验收记录](/Users/wuwenjun/code/TV/docs/vod-refactor-validation-2026-10-22.md)。最终完整测试 TV717/Mobile706，均零失败；构建/lint 通过。最后额外修复 Flow 状态与 Controller 缓冲缓存混用，两端失败回归已证实。最新两端设备 seek/暂停/恢复播放复核通过，普通构建输出已恢复；测试包、reverse、设备探针、临时手机模拟器和服务器已清理。用户随后要求检查并提交，提交前复验通过，交付本地提交，不推送。下文保留分阶段历史证据。

从 `4e70e8ba8` 开始实施。已新增 PlaybackInput / VodPlaybackFlow，接入两端播放、暂停、手势、进度条、系统媒体和字幕；字幕先挂接再 prepare；旧 Live/Cast 字幕入口也保留暂停与速度。Exo 准备阶段 seek 的快照回归已完成修复前失败、修复后通过。

阶段一证据：`/tmp/tv-refactor-stage1-green.log` 两端单测和 Debug 构建成功；`/tmp/tv-refactor-stage1-apk.log` 补充打包成功；当前 TV/Mobile APK 边界探针均 `checks=3 defects=0`。探针第二项现经过实际共享 Flow 字幕入口，而非人为重复旧错误顺序。设备临时副本已清理。

阶段二进展：新增 VodRecoveryFlow，共享恢复窗口、重试/解码/内核/解析/路线/站点推进次序、取回控制权、返回取消、总预算与最终停止；两端已删除 RecoveryWindow/PlaybackRecovery 所有权和旧 TV RecoveryController。前后台权限经 Flow 串行入口处理；TV 冗余 mResumeOnForeground 已移除，以用户播放意图为准。Flow 新增主线程投递、重入队列、请求代次过滤和统一状态订阅。`/tmp/tv-refactor-stage2-tests.log` 两端单测通过；尚未针对阶段二重新打包/设备验收。

进一步完成：搜索与换源状态迁至 main/vod（VodSourceSearch、VodSourceSwitchState），新增共享纯 VodSourcePolicy；两端旧候选选择策略已迁移，支持阈值/等待/手动完成/自动耗尽。TV 原专属搜索/换源测试迁至共享测试，两端都会执行。手机原始 mPlayers.observe 已移除，renderPlayback 只接收 Flow 状态并同步选集。自动结束、循环、相邻集、起播策略归 Flow；重载、前后台权限也经串行流程。两端历史策略共享 VodHistoryController，提交门控归 Flow；片头片尾判断迁出页面。`/tmp/tv-refactor-unified-state.log`、`/tmp/tv-refactor-shared-history.log` 两端单测均通过。

2026-10-22 继续：新增 VodSessionAdapter，手动 PLAYER/DECODE 与自动内核回退均经 Flow 控制，适配器只切换/挂接而不 prepare。页面统一操作入口覆盖字幕、内核、解码、相邻集、循环、重载、选集和生命周期权限；Mobile 不再观察原始会话。解析错误恢复从渲染移至 Flow。新增 CallbackScope 管理两端全部页面 Handler 延迟任务（销毁后的服务清理由 app 作用域按旧 owner 检查），PlaybackArtwork 统一封面请求并拒收旧影片/销毁后结果；字幕对话框、弹幕文件、TrackDialog 加请求/会话归属检查。已有弹幕 requestId 和历史 TaskScope 保护保留。

证据：`/tmp/tv-refactor-full-check.log` 两端完整测试/构建/lint 成功（其后有新增改动，不能充当最终验收）；`/tmp/tv-refactor-engine-flow.log` 最新内核接口两端测试通过。新加自动解码保留总预算、手动解码重置预算用例待下一轮执行。

历史待办现已完成：实际 Exo/IJK、系统媒体/TimeBar、TV 全屏/遥控焦点、双路线失败恢复、手机 PiP 系统暂停/横竖屏/后台/自动下一集；两端普通 APK 九项边界探针；最终测试/构建/lint 与静态入口审计。使用独立 applicationId 测试包和本地受控媒体，原安装应用数据未修改。仍不能把静态清单解读为所有按钮都物理点击过，也不涵盖所有厂商设备与网络服务。
