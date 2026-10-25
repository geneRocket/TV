# 字幕与壁纸的状态、I/O 和发布边界

后续多站搜索与选站行为已扩展，最新能力与验证见 [字幕搜索扩展](subtitle-search-expansion-2026-10-25.md)；本文保留本轮重构时的历史记录。

本轮继续审查播放器、直播数据连接、配置与字幕调用链，重构两处有具体职责冲突和行为回退的模块。保留现有播放内核、站点接口、Room 格式和配置身份。

## 字幕

原调用链由 `SubtitleViewModel` 同时处理 HTTP、ASSRT HTML、备用站 JSON、简化搜索、分页和取消，弹窗另外维护页码并在请求前清空 Adapter。翻页失败会切到备用站第一页；压缩包文件列表可能沿用搜索页码；测试需要反射修改 ViewModel 内部序号。

现在调用方向为：

`SearchSubtitleDialog` → `SubtitleViewModel` → `SubtitleSearchController` → `SubtitleGateway` → `HttpSubtitleGateway` → 两个站点解析器。

- Controller 独占搜索降级、已接受页码、文件列表、加载/错误状态及请求寿命。`SubtitleSearchState` 是完整屏幕快照，弹窗只渲染，不再用 Adapter 和本地页码决定业务行为。
- 备用站仅参与第一页降级。后续页失败保留当前列表和已接受页码；成功后才切页；文件列表关闭搜索分页。
- 请求操作拥有所属 HTTP 取消句柄，回调在交付队列再次验证操作身份和步骤。覆盖同步回调、重入、旧响应、新搜索、取消、关闭以及新选择。解析时复制所选字幕，过期回调不会修改 UI 持有的条目。
- Provider 解析没有 Android 生命周期依赖；HTTP 适配器负责响应关闭；ViewModel 只适配 LiveData 和生命周期。
- 弹窗按 View 生命周期观察数据，销毁 View 后释放 Binding；取消解析恢复先前屏幕。移除无处理逻辑的“下载”按钮，选择具体字幕仍通过原回调交给播放器。

## 壁纸

旧加载先清空活动配置，失败后将活动配置切换到点播接口的壁纸地址，导致旧图片与新配置名称不一致；同时配置选择与 Glide、下载、文件替换混在同一对象。

- `ConfigRequests` 增加独立壁纸代次，调用前复制输入；每次加载使用私有候选对象。失败保留当前配置；成功后才发布；刷新入口沿用当前配置快照。
- `WallpaperSelection` 管理待执行的选择意图和点播继承优先级。用户新选择的独立壁纸优先于较早开始的点播同步；旧请求完成不能清空新请求意图；失败或队列拒绝后恢复当前配置的同步策略。
- `WallpaperImages` 负责平台图片读取及 Glide 处理，`WallpaperFileStore` 注入读取/处理依赖，产出待提交文件。准备完成不等于替换可见壁纸，只有显式提交才替换，取消或失败会清理暂存文件。
- `WallConfig` 只组织候选构建、代次提交与 UI 通知；点播继承壁纸选择也进入同一壁纸请求边界，能使旧下载过期。图片读取使用候选 URL，避免从全局活动配置读取错误来源。
- 两端设置页补全刷新失败的终态，关闭进度提示并显示错误。

文件替换、数据库与偏好持久化没有跨系统事务；没有把取消门控称为崩溃安全的分布式事务。

## 验证

字幕分页测试已通过旧行为恢复实验：恢复“后续页失败也触发备用站”时断言失败，修复后通过。文件测试覆盖读取失败、处理失败、空文件、首次失败、替换失败、准备后未提交、处理时取消及准备后取消。

壁纸原生验收通过：成功加载本地 PNG 并完成 Glide 处理，然后分别触发 404 和无效图片，两端配置 URL 与 JPEG 散列均保持不变。恢复旧的“失败后指向点播壁纸地址”行为时，设备测试在 URL 保留断言失败；恢复修复后两端均通过。

字幕原生验收使用只读 API34 TV 模拟器，两端隔离包上的真实 Dialog、Adapter 和按钮，Provider 注入确定性数据：第一页、第二页失败保留列表、重试、压缩包文件列表、选择字幕和关闭弹窗通过。该测试不代表第三方网站或实体手机验收。

隔离包 Java 崩溃记录为 0；测试包、本地服务、reverse 与本轮只读模拟器已清理。实体设备焦点、手机旋转、外部字幕网站、真实播放器下载所选外部字幕、Python flavor 与其他 ABI 未在本轮设备验收。

最终验证命令（Android Studio JBR，离线模式，两个 worker）：

```sh
./gradlew :app:testLeanbackJavaArm64_v8aDebugUnitTest :app:testMobileJavaArm64_v8aDebugUnitTest \
  :app:assembleLeanbackJavaArm64_v8aDebug :app:assembleMobileJavaArm64_v8aDebug \
  :app:lintLeanbackJavaArm64_v8aDebug :app:lintMobileJavaArm64_v8aDebug \
  --offline --console=plain --max-workers=2
```

退出码 0。TV 914、Mobile 887 次测试执行，合计 1801，失败/错误/跳过均为 0；两端 Java arm64 Debug 构建成功。Lint：TV 0 错误/177 警告、Mobile 0 错误/289 警告。普通 APK 包名均为 `com.fongmi.android.tv`，Manifest 和 DEX 均无隔离包测试入口。差异与新增文件空白检查、Python 测试脚本语法检查通过；Room schema、依赖和预置二进制没有变化。

测试保持既有行为覆盖并改用公开操作驱动，移除了针对 ViewModel 私有字段的反射。其余审查到的 EngineHost/SourcePipeline、直播导航与数据连接边界继续保留；本轮不宣称整个应用已无架构问题。改动尚未提交。
