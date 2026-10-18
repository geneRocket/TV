# aa0af9db 差异审查与修复

日期：2026-10-19。比较范围：`git diff aa0af9db`，审查起点 HEAD 为 `fec581821`，工作区起初干净。

## 结论

按六个模块完成静态审查和现有测试核对，确认并修复两处共享层回归。重点展开行为变化及其调用链；大体积测试样本按相关用例读取，不逐行展开。未发现其他能够确认并复现的缺陷，不代表已经排除全部运行时问题。

按用户确认保留：最低 Android 版本 7.0、中英文语言资源范围，以及站点/直播源/频道 `playerType` 和直播文本 `player=0/1/2` 的移除。

## 修复的缺陷

### P2：旧广告规则失效

- 位置：`app/src/main/java/com/fongmi/android/tv/utils/AdBlocker.java`。
- 触发：配置 `ads` 中使用不带 `regex:` 前缀的正则，或用 `ads.example.com` 匹配 `a.b.ads.example.com`。
- 基线行为：WebView 将配置项同时用于字符串包含匹配和正则匹配。
- 根因：新实现仅编译带前缀的正则；域名快速匹配又只覆盖精确域名和最后两段域名，因此旧配置漏拦截。
- 修复：同时编译旧格式与带前缀的格式；保留原始正则转义和大小写，不改默认广告检测规则。无效正则继续使用字面量回退。
- 回归测试：旧格式主机正则、深层子域名、旧格式 URL 正则，修复前 3 项失败，修复后两种 UI variant 均通过。

### P2：嗅探规则缓存暴露编译半成品

- 位置：`app/src/main/java/com/fongmi/android/tv/bean/Rule.java`。
- 触发：多个 WebView/嗅探请求首次并发读取同一个规则的正则或排除规则；或规则编译中途失败后再次读取。
- 基线行为：每次嗅探直接编译并匹配，不共享编译中的列表。
- 根因：新缓存先把空 `ArrayList` 赋给字段，再逐项编译，其他线程可看到空表或不完整列表；编译异常也会留下半成品。
- 修复：在局部变量中完整编译，以 `volatile` 字段发布不可修改的列表。编译失败不发布缓存，后续读取不会跳过失败规则。
- 回归测试：正则列表并发读取、排除列表并发读取、编译失败后重试，修复前 3 项失败，修复后两种 UI variant 均通过。

两项修复均不改变 JSON 格式、公共方法签名、数据库 schema 或依赖。

## 模块检查记录

| 模块 | 重点核对 | 结果 |
| --- | --- | --- |
| 构建与运行时 | Gradle flavors、依赖升级、Manifest、混淆规则、Java/Python loader 路由 | 三种 Debug 构建通过；保留已确认的兼容性调整 |
| 网络与脚本 | 代理/认证/重定向、响应关闭、HTTP 取消传播、脚本缓存及生命周期 | 定向网络测试及 QuickJS/Python 测试通过；本轮未确认其他缺陷 |
| 配置与数据 | scoped site key、合并优先级、缺失配置异步补载、缓存回退、历史/收藏、备份恢复 | 相关现有单测通过；本轮未确认其他缺陷 |
| 播放链路 | 解析取消、切集换源、续播、内核恢复、DRM、字幕/弹幕、直播回看、M3U8 过滤 | 修复广告规则兼容性与嗅探规则缓存；DRM 兼容内核切换仍在 |
| TV 界面 | 遥控器事件、焦点、分页、Fragment 生命周期、投屏绑定/解绑 | Leanback 单测与构建通过；真实遥控器和焦点仍需设备验证 |
| 手机界面 | 分页、配置/历史/收藏适配器、横竖屏、后台服务、画中画、投屏对话框生命周期 | Mobile 单测、构建、lint 通过；系统生命周期行为仍需设备验证 |

## 最终验证

以下 Gradle 任务均使用 `--offline --console=plain`，退出码为 0：

| 测试任务 | 测试数 | 失败/错误/跳过 |
| --- | ---: | --- |
| `:core:test` | 40 | 0/0/0 |
| `:quickjs:testDebugUnitTest` | 3 | 0/0/0 |
| `:ijkplayer:testDebugUnitTest` | 4 | 0/0/0 |
| `:thunder:testDebugUnitTest` | 3 | 0/0/0 |
| `:app:testLeanbackJavaArm64_v8aDebugUnitTest` | 660 | 0/0/0 |
| `:app:testMobileJavaArm64_v8aDebugUnitTest` | 634 | 0/0/0 |
| `:chaquo:testPythonBridge` | 8 | 0/0/0 |

合计 1,344 个 Java 测试及 8 个 Python 测试。Gradle 允许复用未受影响任务的缓存；修改后的应用测试已执行。

构建通过：`:app:assembleLeanbackJavaArm64_v8aDebug`、`:app:assembleMobileJavaArm64_v8aDebug`、`:app:assembleLeanbackPythonArm64_v8aDebug`。

`:app:lintMobileJavaArm64_v8aDebug` 无错误，305 条警告，与预审数量相同。没有将警告批量清理纳入修复。

## 验证边界

未进行真机/模拟器播放、遥控器焦点、横竖屏、后台/画中画、DLNA 发现与播放、外部源实际播放验证；未进行 Release 混淆打包验证。上述静态审查、单测和 Debug 构建不替代这些验证。

未自动提交或推送。
