# App 产品场景与回归基线

日期：2026-10-24。范围为整个 App 的功能入口与共享业务逻辑，不限于点播/投屏。

这里按用户目标定义预期，再关联逻辑测试。表内的测试是可重复的自动化证据，不代表对应界面已在设备上逐按钮验收；设备栏明确列出缺口。有限的场景清单也不等于所有输入、所有事件顺序均已穷尽。

## 通用预期

1. 用户最后一次有效选择生效；旧页面、旧配置、旧视频的异步结果不能覆盖新选择。
2. 播放暂停是用户意图；退后台和投屏接管是暂时的播放权限变化，不能擅自把暂停变成播放。
3. 刷新、切内核、解码切换、投屏返回保留当前视频进度；明确重播从片头位置开始；换影片或切集不能套用上一集进度。
4. 有可用缓存时优先展示；失败、取消、错误响应不能破坏上一份有效缓存或数据库。
5. 历史、收藏、站点身份随所属配置隔离；视频 ID 按不透明字符串保存和恢复。
6. 空数据、未知总数、断网、权限拒绝、页面销毁是正常产品分支，不能无限加载、闪退或触发旧页面操作。

## 分模块场景清单

“已有”表示使用现有测试并在本轮重跑；“新增”表示本轮加入行为测试。每组待设备事项不计入自动化通过声明。

| 模块/入口 | 用户场景与合理预期 | 逻辑测试证据 | 尚需验收 |
|---|---|---|---|
| 配置输入 | 单地址、多行、常见分隔符、重复地址：顺序稳定、去重、保留地址中的参数 | 已有 `ConfigUrlParserTest` | 剪贴板、扫码填写 UI |
| 配置加载 | 首次无缓存只抓取一次；全部新鲜不刷新；部分缺失或过期不阻塞已有缓存；27 种三配置缓存组合保持选择顺序 | 新增 `CacheFirstLoaderTest` 场景矩阵；已有配置优先级/合并测试 | 长时离线启动 |
| 配置更新 | 连续切配置、迟到回调、缺失配置续接、并行 HTTP 被取消：只允许当前代次更新；内层任务取消不影响独立外层任务 | 已有及新增 `ConfigLoadTaskTest` | 真实接口刷新、Python 多配置 |
| 配置错误 | 空对象、错误 JSON 结构、非法列表、HTML 错误页不能成为有效缓存；HTML 前有注释或 XML 声明也识别；JSON、JS、XMLTV、M3U 中的 HTML 文本不误杀 | 已有 `ConfigPayloadValidatorTest`；新增 `RemoteContentValidatorTest` | 真实站点挑战页 |
| 首页/分类 | 首屏、已知末页、空页、分页失败、未知页数 0/-1：末页保留，未知总数继续，空页停止，失败可重试 | 新增 `ProductNavigationContractTest`；已有 `PaginationStateTest` | 首页布局、分类焦点、筛选按钮 |
| 分类页面恢复 | 进入详情再返回、子分类返回、筛选开关与参数、页码和列表位置保留 | 已有 `PageTest`、`CollectPaginationRestoreTest` | 电视焦点与手机滚动位置、进程重建 |
| 搜索 | 空关键词不启动 worker；重复源去重；不同配置同名源保留独立身份；一源失败仍交付其他结果 | 已有及新增 `SiteSearchSessionTest` | 语音、键盘、拼音输入 |
| 搜索时序 | 同词重搜也拒绝旧 token；超时结束；离页取消；完成回调立即开始下一轮不被旧清理取消 | 新增 `SiteSearchSessionTest`，已有 deadline/并发预算测试 | 快速真实输入与旋转 |
| 搜索与自动换源 | 自动模式仅接受同一影片，允许质量/括号年份差异，拒绝续作/同名相似作品；切手动后不再自动选第一项 | 已有 `SearchSorterTest`、`VodSourceMatcherTest`；新增 `SiteSearchSessionTest` | 实际影片元数据差异 |
| 详情与历史入口 | 站点已加载直接打开；未选配置加入现有配置集合；已删配置回退搜索；失败后重试；旧错误不覆盖新点击 | 已有及新增 `VodEntryResolverTest` | 海报、演员跳转、简介、分享、磁链下载 |
| 点播选集 | 首集上一集、末集下一集、空集列表、反向播放、循环、重选当前路线：保持明确的边界语义，不重复切集 | 新增 `ProductNavigationContractTest`；已有 `EpisodeNavigatorTest`、`VodPlaybackFlowTest`、两端 `PlaybackReselectionTest` | 遥控器数字选集、拖动列表 |
| 点播起播/续播 | 首次播放、保存进度、跳片头、手动重播、刷新、换源、切内核、切解码、换字幕：按 StartPolicy 保留或重置进度 | 已有 `VodPlaybackCoordinatorTest`、`VodPlaybackIntentTest`、`VodEpisodeCatalogTest`、`VodPlaybackFlowTest`；新增 `VodPlaybackProductTest` | 实际 Exo/IJK 解码与字幕显示 |
| 点播状态与恢复 | 解析/准备/缓冲/成功/暂停/结束/失败/停止/释放；旧解析和旧引擎事件不改当前集；重试有预算，成功结束恢复状态 | 已有 `VodPlaybackCoordinatorTest`、`VodRecoveryFlowTest`、`RecoveryWindowStateMachineTest`；新增投屏交叉状态测试 | 连续失败真实接口、返回键实际行为 |
| 点播投屏中断 | 播放或暂停 × 生命周期前后顺序 × 准备/缓冲/播放状态 × 起点/中间/临近结束，共36组合：进度、倍速和用户暂停不丢 | 新增 `VodPlaybackProductTest.returnFromCastRestoresPositionSpeedAndUserPauseAcrossLifecycleOrders` | DLNA 发送端真实接管/停止 |
| 投屏连续操作 | 多次投屏返回取最新进度，显式归零保留 0；返回后换集取新集历史，重播取片头，新影片不继承旧暂停或进度 | 新增 `VodPlaybackProductTest` | 多发送设备、异常断连 |
| 投屏迟到事件 | 首次解析或重新解析未完成就投屏：取消旧解析；结果迟到不能 prepare 已释放播放器、报错恢复或抢回播放；关闭页面后所有旧输入无效 | 新增 `VodPlaybackProductTest`、`DefaultPlayerSessionTest` | 系统杀进程、系统音频焦点 |
| 投屏接收页 | 只回前台的 intent 不重复加载旧 URI；标题提取；renderer 生命周期连接/解绑；播放结束只交付一次 | 已有 `CastResumeTest`、`CastRendererConnectionTest`、`CastMediaTitleTest`；新增 `DirectPlaybackFlowTest` | DLNA 发现、进度上报、发送端暂停/seek |
| 直播切台 | 组内前后循环、跨组循环、跳过收藏、仅一个播放组、空普通组、全部空组：有台可切则切台，无台不空播 | 已有及新增 `LiveChannelNavigatorTest`，60种单组方向/位置/跨组组合 | 数字选台、遥控器长按、密码组 UI |
| 直播换源/数据 | 切频道、组、线路后旧 URL 不覆盖；前后台订阅只移除一次；关闭后不重连；XML 事件仅刷新当前 EPG | 已有 `LiveDataConnectionTest`、`LiveEpgCancellationTest` | 真实节目单实时更新 |
| 直播回看 | 未来节目、无回看配置不可播放；有效节目先更新 UI 后请求；切回直播不污染频道持久配置；回看保留点播式 seek | 已有 `LiveReplayPlaybackTest`、两端 `LiveReplaySelectionTest`、`LiveDataConnectionTest` | 时移直播、跨日回看 |
| 直播/投屏控制 | 用户暂停跨多次退后台保留；临时生命周期暂停可恢复；选择新源恢复播放意图；系统停止后 onResume 不自动播放；关闭后排队事件作废 | 新增 `DirectPlaybackFlowTest` | 蓝牙/耳机与系统通知控制 |
| EPG 解析 | XML/JSON、gzip、编码/时区、跨日、无效时间、取消、旧频道回调：保留有效当前节目，关闭流并丢弃过期结果 | 已有 `EpgParserTest`、`EpgTimeParsingTest`、`EpgStreamingTest`、`EpgCancellationTest` 等 | 真实大节目单的性能 |
| 历史进度 | 未播放成功不覆盖历史；新路线失败不擦旧进度；成功后暂停、缓冲、结束允许更新；显式归零生效，未知时长不擦已知时长 | 新增 `VodHistoryProductTest`；已有 `VodHistoryCommitGateTest`、`HistoryTest` | 退出/杀进程/重启后的 Room 恢复 |
| 历史与收藏身份 | 视频 ID 含 URL、中文、`@@@` 或数字片段都完整恢复；最后一段才是配置 ID；兼容无配置后缀的旧键 | 新增 `PersistedEntryProductTest` | 实际导入后点击收藏与历史 |
| 数据隔离与同步 | 同名站点跨配置不串；删除只影响目标；异步旧快照不擦新进度；收藏/配置列表不暴露可变缓存引用；导入关联本地配置 | 已有 `HistoryDeletionScopeTest`、`HistorySyncScopeTest`、`PersistenceCacheIsolationTest`、`SyncPullsTest` | 双设备同步与冲突交互 |
| 网络/脚本 | 取消传播、认证、重定向、线程 scope、脚本/JAR 缓存、旧会话资源清理：错误或取消不能作为成功数据交付 | 已有 `AuthInterceptorTest`、`ResponseInterceptorTest`、`HttpCallScopeTest`、`HttpScopeWorkerTest`、`ScriptCacheTest`、`JarCacheTest`、QuickJS 测试 | Python 远程脚本和第三方原生协议 |
| 播放 URL 与流 | 行内请求头不丢；本地 HLS 代理直接选择 HLS；直播清单不当点播删段；广告名/MAP/CDN 特征不能误删正常段；字幕白名单有效 | 已有 `UrlUtilTest`、`PlaybackInlineHeadersTest`、`ExoProxyMimeTypeTest`、`M3u8AdFilterTest` | 实际受保护视频、DRM、字幕/弹幕同步 |
| 下载与恢复 | 中断/取消保留有效旧文件，首次失败不造缓存；gzip/ZIP 错误与越界路径不污染目标；数据库切换失败回滚且不与写入竞争 | 已有 `DownloadTest`、`FileUtilZipTest`、`FileUtilGzipTest`、`DatabaseFileSwapTest`、`DatabaseResetConcurrencyTest` | Android SAF 授权、磁盘满、升级安装 |
| 设置与备份 | 缺值/错误类型/越界选项可打开设置及播放页；所有合法选项保持原值；越界回默认但不重写原备份 | 已有及新增 `SettingReadFallbackTest`，7类选项28种非法值和20种合法值 | 修改选项后的重启、自动备份、权限与生物认证 |
| 电视播放界面 | 准备/缓冲清除旧暂停和 seek 浮层；暂停隐藏加载；短暂 seek 不闪圈；延迟任务不能在后台改元数据；快速重复操作后无残留 | 已有及新增 `TvVodPlaybackStateBinderTest` | 所有焦点、全屏黑屏、真实遥控器及截图 |
| 电视列表/页面 | 收藏与历史差量更新；直播列表刷新时保留可见选择；列表边界/空列表安全；离页搜索不再提交 | 已有 TV Adapter、`LiveListSelectionTest`、`ActivityLifecycleRegressionTest` | Home/设置/搜索/收藏逐按钮点击 |
| 手机界面 | 手势取消/释放、回看 seek、列表差量、直播 Service 所有权和 Home 配置生命周期 | 已有 `GestureReleaseTest`、`LiveSeekUiTest`、`LiveServiceOwnershipTest`、`HomeConfigViewModelTest` | 横竖屏、PiP、后台播放、锁屏、SAF、扫码权限、分享/投屏发现 |

## 本轮确认为缺陷并修复

| 触发条件 | 原行为/根因 | 修复与失败证据 |
|---|---|---|
| 点播被投屏接管，最近状态回调仍在旧位置 | release 使用上次事件进度，可能恢复到0 | `DefaultPlayerSession` 释放前抓取当前快照；上一轮测试 `480000 -> 0` 失败，本轮继续保留回归 |
| 倍速播放后投屏并返回 | reload 从新建引擎读取默认1倍速 | 冻结释放快照，Coordinator 在释放通知时保留控制状态；新增测试修复前期望2.25、实际1.0 |
| 初次/重复解析期间被投屏接管，之后旧解析成功 | 被释放的 session 仍接收 prepare，触发 ERROR | Coordinator 释放通知取消解析、作废 pending request，保留解析阶段的目标进度；两条测试修复前失败 |
| 跨组切台但仅一组可播放，或组间存在空组 | 无下一组时重播边界频道，或选中空组而不切台 | 无其他播放组则组内循环，自动跨组跳过空组；四条测试修复前失败，保留手动浏览空收藏组 |
| 历史/收藏视频ID内有分隔符，尤其随后是数字 | ID只取第二段；配置ID错误读取第三段 | 使用首尾边界还原ID，配置ID取末段；原测试截断`video@@@part`、把123认成配置1 |
| 分类/搜索接口总页数为-1且有内容 | 认为已经末页，后面内容不可达 | 未知总页数统一按非正数处理；原测试停止了本应继续的分页 |
| 已经播放成功的视频缓冲中退出 | History gate允许，但HistoryController拒绝BUFFERING保存 | 允许成功路线保存缓冲阶段进度；全状态/路线矩阵原测试失败 |
| 错误HTML响应前有完整注释/XML声明 | 识别漏过，有机会污染配置/脚本缓存 | 跳过前导注释和XML声明再识别；保留真实JS/JSON/XML资源；原HTML判定测试失败 |
| 备份中的background/reset/language/backup_mode/size/quality/config_cache越界 | 部分页面直接以值索引数组；缩略图比例也可能越界 | 七类选项验证范围并回默认，保留备份原值；原测试返回MIN_VALUE而非默认值 |

## 验证与边界

测试方法内有明确的组合循环，JUnit XML 中仍按一个方法计数，不将每条断言虚报成独立测试。

本轮运行命令（JDK 使用本机 Android Studio JBR）：

```sh
./gradlew :core:test :quickjs:testDebugUnitTest :ijkplayer:testDebugUnitTest \
  :app:testLeanbackJavaArm64_v8aDebugUnitTest :app:testMobileJavaArm64_v8aDebugUnitTest \
  :app:assembleLeanbackJavaArm64_v8aDebug :app:assembleMobileJavaArm64_v8aDebug \
  --offline --console=plain --max-workers=2
```

最终新鲜验证结果：

- 电视端823、手机端795、core 43、QuickJS 3、IJK 4项Java单测通过，失败/错误/跳过均为0。
- `python3 -m unittest discover -s chaquo/src/test/python -p 'test_*.py'`：8项通过；它们验证Python bridge的模块隔离，不代表远程Python接口实测。
- 总计1676项测试执行通过；共享测试会分别在电视和手机构建中执行，不把它们称为1676个独立用户场景。
- 两端普通Java arm64 Debug APK构建成功。电视lint 0错误、184条警告；手机lint 0错误、296条警告。`git diff --check`通过。
- 没有构建Python flavor或其他ABI的APK，没有执行设备instrumentation。

设备未连接，未运行本轮真实投屏、真实播放、焦点、旋转和系统权限验收。第三方接口及所有原生协议不能由JVM单测替代。本轮没有删除功能，也未自动提交或推送。

后续整体架构重构及新增模拟器原生播放验收记录见[播放架构重构](playback-architecture-refactor-2026-10-24.md)。上面的1676项为重构前历史结果；重构后的测试执行数、构建与设备边界以该记录为准。
