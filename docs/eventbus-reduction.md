# EventBus 分阶段收缩

日期：2026-10-20。完成两条局部通信迁移，删除 `StateEvent`、`ScanEvent`。应用事件类型由 7 种减为 5 种；三个扫码对话框不再注册 EventBus。没有引入新的全局消息中心或依赖。

## 已迁移

| 通信 | 原路径 | 新路径 |
| --- | --- | --- |
| 手机主页配置加载状态 | MainActivity → 全局 StateEvent → VodFragment | Activity 作用域 HomeConfigViewModel → viewLifecycleOwner 观察 |
| 手机扫码地址 | ScanActivity → 全局 ScanEvent → 所有已注册对话框 | Activity Result → 发起扫码的 Cast / Sync / Transmit 对话框 |

配置状态具有 IDLE / LOADING / READY / FAILED，保留最新状态。视图稍后创建也能收到加载失败，销毁后自动停止观察；不同 Activity 的 ViewModelStore 隔离状态。成功状态只清除配置失败标记，不干预 SiteViewModel 负责的首页内容加载进度。

扫码启动器在 Fragment 实例初始化时注册，由 Fragment 生命周期管理结果回调。成功通过 Intent 返回地址；取消返回 null，不启动设备扫描。回调同时检查 ScanTask 是否仍存在。相机权限、CaptureManager、二维码地址筛选及设备发现逻辑保持原实现。

## 后续边界

| 保留事件 | 后续收缩方向 |
| --- | --- |
| ErrorEvent | 优先转为播放控制器实例回调，先明确当前播放页面 / 会话归属，再迁移重试与切源链路 |
| RefreshEvent | 将字幕、弹幕、详情和播放器刷新等局部命令逐项交还所属页面；配置、壁纸、历史及收藏刷新分别明确观察范围 |
| ServerEvent / ActionEvent | 远程入口仍需跨组件分发，后续明确目标页面或播放会话 |
| CastEvent | 保留接收与导航功能，后续明确接收页面和投屏会话归属 |

本轮保留 AbsActivity / BaseFragment 中仍有用途的 EventBus 注册，不扩大到播放错误、远控和全局刷新重构。

## 验证

新增 5 项 HomeConfigViewModel 测试：延迟观察、视图销毁 / 替换、停止期间只交付最新状态、失败重试、Activity 存储隔离与保留。

```sh
./gradlew :app:testLeanbackJavaArm64_v8aDebugUnitTest \
  :app:testMobileJavaArm64_v8aDebugUnitTest \
  :app:assembleLeanbackJavaArm64_v8aDebug \
  :app:assembleMobileJavaArm64_v8aDebug \
  --offline --console=plain --max-workers=2
```

使用本机 Android Studio JBR，BUILD SUCCESSFUL，退出码 0。

| 检查 | 结果 |
| --- | --- |
| Leanback Java 单测 | 669 项，0 失败 / 错误 / 跳过 |
| Mobile Java 单测 | 648 项，0 失败 / 错误 / 跳过 |
| Leanback Java arm64 Debug | 通过 |
| Mobile Java arm64 Debug | 通过 |
| 旧事件引用与旧扫码启动调用 | 无残留业务引用；另有一个无关单测方法名包含 StateEvent 字样 |
| git diff --check | 通过 |

尚未执行设备端真实扫码、取消、相机权限及扫码期间旋转 / 进程重建验证，不能用上述单测代替这些场景。未执行 Release 混淆验证。此前 EPG 等未提交改动保留；本轮未自动提交。

## 第二阶段：播放错误归属

继续于 2026-10-20 完成。

- `ErrorEvent` 保留错误类型、重试次数、错误码和提示数据，工厂方法改为返回数据，不再通过 EventBus 发布。
- 每个 PlaybackController 在创建时绑定所属页面的错误回调。TV 点播、直播、投屏，以及手机点播、直播共五个页面移除错误订阅；各页面产生的播放错误也交给自己的控制器。
- 主线程错误保持同步交付，其他线程先投递主线程。原有当前播放器、前后台、恢复窗口判断和重试 / 切源逻辑保留；回调异常记录日志。
- PlaybackErrors 只属于一个控制器，没有静态订阅表。停止、释放内核、开始新播放及重新设置媒体源会使此前已排队错误失效；完整释放会清除页面回调并拒绝后续交付。
- 手机详情页空片源改为本页提示。旧路径全局广播 FLAG，在允许后台播放且原播放页仍存活时，可能误触发该后台播放页的恢复逻辑；现在不再向播放器发送此页面错误。

当前真正通过 EventBus 发布的应用事件剩 **4 种**：RefreshEvent、ServerEvent、ActionEvent、CastEvent。ErrorEvent 名称和数据类型暂时保留，避免无关类型重命名。

新增 PlaybackErrorsTest 四项，验证两个控制器间隔离、停止后丢弃旧排队错误且可继续播放、释放后丢弃排队及新错误，以及同步回调内停止播放。首先运行两端错误交付 / 恢复定向测试通过；最终以同一全量命令重新验证：

| 检查 | 第二阶段结果 |
| --- | --- |
| Leanback Java 单测 | 673 项，0 失败 / 错误 / 跳过 |
| Mobile Java 单测 | 652 项，0 失败 / 错误 / 跳过 |
| Leanback Java arm64 Debug | 通过 |
| Mobile Java arm64 Debug | 通过 |
| 全量 Gradle 命令 | BUILD SUCCESSFUL，退出码 0 |
| 旧 onErrorEvent 订阅及未使用返回值的错误工厂调用 | 无业务残留 |

以上单测包含既有恢复、会话和播放协调测试，但没有在设备上执行实际解析失败、播放超时、解码失败、切集、切源、后台播放或投屏场景，也未执行 Release 混淆验证。尚不能据此宣称这些设备场景均已通过；未自动提交。

## 第三阶段：局部播放命令

继续于 2026-10-20 完成。将详情刷新、播放重载、字幕和弹幕四项从 RefreshEvent 移出，改为 PlaybackCommand；RefreshEvent 分类从 12 项减为 8 项，仍使用 EventBus 的事件类保持 4 种。

- `/action?do=refresh&type=detail|player|subtitle|danmaku` 的参数及成功响应不变。HTTP 工作线程读取当前播放器，交给该控制器的主线程命令回调。交付时检查它是否仍是当前播放器；若切换或释放则丢弃，不重定向到新播放器。没有当前播放器时保持无操作。
- `/action?do=file` 对 XML 弹幕和字幕文件的处理使用同一实例命令；APK 及其他文件路径处理保持原实现。
- TV 本地弹幕文件选择不再全局广播，直接交给文件选择器原本绑定的播放器。本地字幕仍沿用已有直接 setSub 路径，保留直播等场景的字幕能力。
- TV / 手机点播处理四类命令，保留原有前后台 / 外部播放器跳转判断；直播仅处理播放重载，投屏仅处理字幕，与原接收范围一致。直播配置刷新 LIVE 仍保留全局通知。
- 控制器完整释放会清除命令回调；释放内核使此前排队命令失效，避免旧页面重新成为当前播放器后执行旧命令。主线程本地操作保持同步，不新增线程池或全局订阅表。

新增 5 项测试：遥控类型与路径映射、交付前切换播放器不误发、释放后不交付、释放内核后旧命令失效、同步本地命令及未知类型忽略。首次定向构建发现测试辅助方法漏写，补齐后两端命令 / 错误回调测试通过。随后用同一全量命令验证：

| 检查 | 第三阶段结果 |
| --- | --- |
| Leanback Java 单测 | 678 项，0 失败 / 错误 / 跳过 |
| Mobile Java 单测 | 657 项，0 失败 / 错误 / 跳过 |
| Leanback Java arm64 Debug | 通过 |
| Mobile Java arm64 Debug | 通过 |
| 全量 Gradle 命令 | BUILD SUCCESSFUL，退出码 0 |
| 旧四项 RefreshEvent 发布 / 处理引用 | 无残留 |

未在设备上验证网页遥控、真实字幕 / 弹幕加载、文件选择器及切换播放页场景；未执行 Release 混淆验证。未改变远控协议格式，未自动提交。后续可继续明确 ActionEvent 的播放操作归属，以及 ServerEvent 的页面导航归属。
