# 配置、存储、页面与事件执行边界重构

范围：按用户要求重构上一轮评审的1、2、3、4项，不修改广告过滤算法，不改变Room格式和现有功能。

## 验收清单

- [x] 配置读取/合并与配置生效分离；加载中不清空当前可用配置，成功后发布完整快照；网络、直播联动由应用服务组织。
- [x] 历史/收藏存储实例化，注入DAO、通知、执行器与配置范围；异步操作固定提交时的范围。
- [x] TV与手机详情、历史加载、选集编排由页面Presenter负责，Activity负责呈现与平台动作。
- [x] 播放Flow内部保证串行执行，明确关闭、重入、异常及跨线程输入的行为。
- [x] 行为测试、两端单测、构建与lint通过；记录设备验证边界。

保留旧入口作为兼容适配，禁止保留两份业务实现。每项完成后填写实现与验证证据。按用户要求提交，不推送。

## 1. 配置：请求、文档、运行时与生效动作

调用方向：`VodConfig / LiveConfig`兼容入口 → `ConfigRequests` → 请求独享的候选配置 → `VodConfigLoader / LiveConfigLoader`、`ConfigJsonLoader`、`ConfigMerger` → `ConfigApplicationService`发布。

- `ConfigRequests`持有点播与直播各自的加载代次，独立于当前运行时对象；入口复制Config及列表，后台执行不再读取调用者随后修改的输入。
- Loader负责下载、目录递归、有效性检查和缓存回退；`ConfigJsonLoader`通过注入Reader执行I/O，订阅目录解析不再经由全局App反序列化。Merger只合并负载，保留站点配置身份与各自jar归属。
- `ConfigDocument`保存不可变内容和来源列表，对外返回JSON副本。站点/路线的可变选择模型仍是运行时对象，不将它们冒充不可变数据。
- 每次加载构建独立候选对象，失败前不会清空当前可用站点。缓存缺失项补齐时，复制当前集合和配置元数据后再发布，保留已存在的站点/直播模型以兼容当前页面。
- `ConfigLoadTask.commit`与取消共享代次门控，过期任务不能发布。配置选择持久化、脚本预加载、直播/壁纸联动与组合网络策略的生效入口集中到应用服务；网络策略的具体应用集中到`ConfigNetworkService`。
- `ConfigLoadTransaction`明确构建与提交阶段；叶子解析器只构建候选数据，批量合并完成后提交一次。`ConfigRuntime`通过一个volatile快照发布点播与关联直播，组合网络策略读取同一份快照。替换与增补是显式操作，不通过对象是否等于当前单例推断。恢复配置中的选择只修改候选状态，预热和开机直播动作在发布后执行。
- 网络客户端和数据库不构成跨系统事务；这里保证候选构建与运行时发布的边界，不宣称外部HTTP请求和所有后续平台动作具有数据库事务语义。

## 2. 存储：注入依赖与固定范围

`HistoryRepository / KeepRepository`持有实例化的`HistoryStorage / KeepStorage`。缓存、未完成写入、锁和写入版本均属于存储实例；存储实现不再访问App、Setting、VodConfig、AppDatabase或RefreshEvent。

构造参数提供DAO、写入/同步执行器、变更通知；历史存储另接收时钟、名字转换和关联Track删除动作。`PersistenceServices`是生产组合根，负责连接Room及应用事件，不把单例查找藏回存储实现。

`PersistenceScope`是不可变配置ID集合；Repository的`in(scope)`创建固定范围的操作入口。页面在详情请求开始时捕获范围；重试同一条目沿用该范围。延迟删除固定用户发起操作时的范围；导入固定配置映射及数据副本；异步历史保存固定副本。未带作用域的旧实体入口仅通过无状态`HistoryStore / KeepStore`适配默认存储实例，缓存和写入逻辑只有一份。

原有缓存副本隔离、写入取消、数据库重置锁顺序测试已迁移到真实存储实例，并新增不依赖Room全局状态的内存DAO行为测试。Room字段、schema及历史/收藏身份编码不变。

## 3. 页面：Presenter拥有业务状态，Adapter只呈现

- `VodDetailPresenter`由TV和手机共用：创建请求身份、隔离过期结果，决定空详情、收藏入口失败、名字回退及换源搜索继续等行为。平台Port提供界面动作。
- `VodPagePresenter`拥有当前历史、详情加载的配置范围、历史预取与加载任务、超时回退、无痕处理、用户修改保护和收藏操作；销毁或取消请求时丢弃尚未交付的数据。
- `VodEpisodeSelection`拥有路线、选集身份及倒序规则。历史中的路线标识先匹配到当前列表的真实路线对象；缺失路线按既有规则回退，过期选集不能更改当前选择。TV、手机点播及手机详情页共用它。
- 两端VideoActivity不再持有独立History字段、历史任务队列或预取锁，不再通过Adapter对象决定或修改路线/选集状态。手机FlagAdapter移除选择/倒序业务方法，只按Presenter给出的索引更新受影响行。
- Activity仍负责控件布局、焦点、手势、字幕/弹幕视图和系统平台交互；现有SiteViewModel继续作为数据请求适配器。这里没有为了采用新框架而重写所有页面。

## 4. 事件执行：Flow自己保证串行

`SerialExecutor`包住Flow的交付执行器，同一时刻只有一个FIFO drain。支持直接执行器与线程池；重入命令排到当前命令之后，单个运行时异常被记录且不阻断后续命令。拒绝调度时不把旧命令留待意外重放；关闭后丢弃尚未开始的工作；已经运行的命令允许完成。

点播与直播/投屏Flow不再各自实现未经线程约束的ArrayDeque；直接播放接管清理旧待执行命令。业务状态读通过volatile发布，点播Coordinator继续负责同步的状态与请求代次。

串行不等于改变原生播放器的线程要求：生产仍使用`MainThreadExecutor`，安装Port、生命周期接管和原生资源释放仍由页面所属线程组织；不会把Exo/IJK移到任意线程池。并发测试用替身验证输入不重叠，实际Looper/原生链路另用模拟器验收。

## 验证记录

重构开始于2026-10-24，完成验收于2026-10-25（Asia/Shanghai）。使用Android Studio JBR、Gradle离线模式及两个worker。

```sh
./gradlew :core:test :quickjs:testDebugUnitTest :ijkplayer:testDebugUnitTest \
  :app:testLeanbackJavaArm64_v8aDebugUnitTest :app:testMobileJavaArm64_v8aDebugUnitTest \
  :app:assembleLeanbackJavaArm64_v8aDebug :app:assembleMobileJavaArm64_v8aDebug \
  --offline --console=plain --max-workers=2
./gradlew :app:lintLeanbackJavaArm64_v8aDebug :app:lintMobileJavaArm64_v8aDebug \
  --offline --console=plain --max-workers=2
python3 -m unittest discover -s chaquo/src/test/python -p 'test_*.py'
```

Java测试执行：TV 896、Mobile 869、core 51、QuickJS 3、IJK 4，共1823次，失败/错误/跳过均为0；Python bridge 8次通过。共享测试分别在两端执行，不将其称为1831个独立用户场景。两端Java arm64 Debug构建成功。

最终lint：TV 0错误/177警告、Mobile 0错误/289警告。两端普通APK包名均为`com.fongmi.android.tv`，Manifest无临时Receiver或隔离包标记。`git diff --check`和新增文件空白检查通过，schema无变化，无二进制/日志等意外产物。测试Python脚本语法检查通过。

新增测试主要验证配置失败保留旧运行时、缓存回退、目录源切换、请求输入/文档副本隔离、代次取消；存储范围固定、延迟删除、独立缓存、异步保存和导入快照；详情请求过期/取消、空结果产品策略、历史加载期间用户修改、路线匹配、选集倒序和收藏；串行执行器与真实Flow并发输入、重入、异常、拒绝和关闭。

### 原生验收

独立只读Android TV API34 arm64模拟器，隔离包、本地180秒视频、真实Exo/IJK；所有下列脚本退出码为0，原生视频尺寸均为640×360。

| 路径 | 接管前→返回后位置（毫秒） | 结果 |
| --- | --- | --- |
| TV Exo暂停点播→投屏页→返回 | 31012→31012 | PAUSED，2倍 |
| TV Exo播放点播→投屏页→返回 | 30631→31009 | READY，2倍 |
| TV直播→投屏页→返回 | 367→574 | 原频道READY |
| TV IJK暂停点播→投屏页→返回 | 29328→29328 | PAUSED，2倍，IJK |
| Mobile暂停点播→直播→返回 | 30387→30387 | PAUSED，2倍 |
| Mobile播放点播→直播→返回 | 30160→31355 | READY，2倍 |
| TV配置加载失败→换路线→第1集切第2集 | — | 旧配置URL和站点保留；Working路线，两集均READY |
| Mobile同上 | — | 旧配置URL和站点保留；Working路线，两集均READY |
| TV/Mobile点播继承直播→混合直播配置→切源 | — | 点播与关联直播一起生效；文本、JSON及失效地址混合加载保留两个有效源，均READY，切源代次2→4 |

隔离包Java崩溃记录为0。测试包、reverse端口、本地服务和只读模拟器已清理。最后的手机Adapter局部刷新调整通过自动验证，未重新进行实体触控验收。

未验证实体遥控焦点、外部DLNA发现与传输、实体手机旋转/PiP、OEM解码、第三方接口、其他ABI或Python flavor APK；Mobile变体运行于TV模拟器，不代表手机硬件验收。普通构建必须检查不含测试Receiver。
