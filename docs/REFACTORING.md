# 应用重构路线图

## 目标

在不改变现有 VOD、直播、播放、爬虫和本地服务兼容性的前提下，逐步降低 TV 与移动端重复实现，建立可验证的业务边界，并更新界面体验。重构期间每个阶段都必须保持 `leanback`、`mobile` 和 Java/Python 爬虫 flavor 可以独立演进。

## 不变约束

- `app/src/leanback` 和 `app/src/mobile` 继续分别拥有输入方式、布局和导航；不把遥控器焦点逻辑与触控逻辑强行合并。
- `app/src/main` 只放与呈现方式无关的业务契约、用例和共享组件。
- VOD 的 scoped site key、配置缓存回退、crawler loader 生命周期和播放器取消/超时语义必须保持兼容。
- Room schema 变更必须随迁移和 schema export 一起提交。
- 每次共享层改动至少构建一个 Leanback 和一个 Mobile Java variant；涉及 Python loader 时额外构建 Python variant。

## 当前基线（2026-07-23）

- `:app:assembleLeanbackJavaArm64_v8aDebug` 构建成功。
- `app` 有 377 个 Java 源文件；`main`、`leanback`、`mobile` 分别为 177、109、89 个。
- TV 和移动端存在 40 余个同路径类；`VideoActivity` 两端合计 5,575 行，是最高优先级的拆分对象。
- 新增纯 Java `:core` 模块，承载请求调度契约；其单元测试覆盖同键替换、跨键并发、超时取消和超时回调清理。

## 目标架构

```text
TV UI / Mobile UI
        │  只负责渲染、输入和导航
        ▼
Presentation contract（状态、事件、导航参数）
        ▼
Feature use cases（Vod / Live / Playback / Library）
        ▼
Data & runtime gateways（Config / DB / Loader / Player / Server）
```

现有 `SiteViewModel`、`LiveViewModel` 和 `Source` 是迁移入口。先抽取可测试的请求编排和状态契约，再让两个 UI source set 依赖它们；不直接移动 mode-specific Activity。

## 分阶段交付

### 0. 护栏与观测

1. 为纯 Java 逻辑建立单元测试，并为关键 UI 流程建立最小 smoke checklist。
2. 为构建增加可重复的 Leanback、Mobile、Python 校验入口。
3. 记录启动、首页、详情、搜索、直播切换、播放成功率与崩溃基线。

验收：现有构建通过；所有既有行为都有对应的测试或可执行人工检查项。

当前可通过以下命令执行共享层的回归矩阵：

```bash
./gradlew :app:verifyRefactor
```

该任务包含 `:core:test`、Leanback lint 与三套 app 构建；当前 lint 基线为 0 error、199 个既有 warning，后续改动不得引入新的 error。

### 1. 共享业务边界

1. 将 ViewModel 中的请求取消、超时和过期结果过滤整理为共享请求协调器。
2. 将配置、播放和数据库访问收敛为显式 gateway；保留现有实现作为适配器。
3. 将公开 `MutableLiveData` 替换为只读观察接口，防止 UI 侧修改业务状态。

验收：首页、分类、搜索、详情、直播 URL 解析在快速切换时不会展示过期结果，且无需依赖 Activity 生命周期完成取消。

配置加载补充约束：`VodConfig`、`LiveConfig` 与 `WallConfig` 的公开加载入口必须经 `ThreadPools.configLoad()` 的 FIFO 队列执行；一次事务需在同一任务内完成初始化、清理、配置选择、读取与解析。`ThreadPools.config()` 仅承载缓存刷新等不应改写当前配置状态的后台工作，不能用于新的全局配置加载入口。

页面销毁时必须使用三类配置的 `release()`，把清理请求排在已提交的加载事务之后；不得直接调用实例 `clear()` 让旧任务在销毁后重新写回状态。

### 2. 播放内核

1. 从两端 `VideoActivity` 抽离共同的会话状态、剧集导航、错误恢复和资源释放策略。
2. 保留 TV 的焦点/按键与移动端的 PiP/通知服务为薄适配层。
3. 为 `Source`、`ParseJob`、协议 extractor 建立可注入的边界和超时回归测试。

验收：两端播放、换源、下一集、循环、后台/前台恢复和退出释放保持一致；不出现旧解析结果覆盖新请求。

播放取消约束：`ParseJob` 的同步网络解析必须持有活动 OkHttp `Call`；超时、换源和释放时除取消 `Future` 外还必须显式取消该 `Call`，不能仅依赖线程中断。

### 3. 内容与资料库

1. 统一 VOD、直播、收藏、历史、下载的列表状态与空/加载/错误展示模型。
2. 拆分巨型 Config/History/Setting 类，按读取、写入、缓存和迁移职责组织。
3. 将 Room 静态缓存逐步收敛到 repository，并补充迁移测试。

当前进展：`HistoryRepository` 已成为 UI、同步、服务器处理和数据库缓存清理的统一入口；`History` 暂保留为兼容实体与实现，后续可在不改调用方的前提下替换其缓存/Room 细节。

验收：重复列表逻辑减少，数据库升级与备份恢复可验证，离线缓存和多配置 site key 行为不回退。

### 4. 视觉与交互更新

1. 建立共享的色彩、间距、排版、形状和状态 token；TV 与移动端各自映射到合适的组件。
2. 优先改造首页、详情、播放控制、直播和设置等高频路径。
3. TV 以焦点可见性和遥控器路径为第一约束；移动端以单手操作、深色模式和自适应布局为第一约束。

验收：所有高频页面在深浅主题、中文/英文和常见屏幕尺寸下可用；TV 的每个可操作元素均能通过 DPAD 到达。

### 5. 模块化与清理

1. 已将无 Android 依赖的请求协调器迁至 `:core`；当播放和 feature 边界稳定后，继续分离它们。
2. 移除已迁移的重复代码、废弃状态和无用依赖；不修改 vendored/native 模块，除非需求明确要求。
3. 把构建、静态检查、单元测试和 smoke 测试接入持续集成。

验收：模块依赖单向、完整构建矩阵通过、包体积与启动性能不劣于基线。

## 优先级原则

先处理高频且双端重复的流程：播放 > 首页/详情/搜索 > 直播 > 配置/资料库 > 设置。每个 PR 只横切一个明确行为，避免将 UI 重画、数据库迁移和 crawler runtime 改动混在同一次发布中。
