# EPG 流式解析

日期：2026-10-20。

## 实现

`XmlTvReader` 使用 Android 自带的 XmlPullParser 逐条读取 XMLTV，替换 EpgParser 中整份 Tv 对象树的 Simple XML 反序列化。

- 文件入口先扫描频道 ID、显示名和图标，再回到文件开头逐条处理节目，仅保存匹配直播频道且开始或结束于当天的节目。两遍扫描复用同一已打开文件，兼容频道晚于节目声明，以及扫描期间缓存文件被替换的场景。
- 字符串入口单遍处理，保留原有根日期、节目顺序和无效时间过滤行为。调用方传入的 XML 字符串仍存在，但不再额外构造完整节目对象树。
- 保留精确 ID 优先、别名匹配、重复频道声明的图标查找、跨日节目、多源合并及现有 gzip 缓存处理。
- 每个解析事件检查取消，跳过不需要的子树时同样检查；文件读取由 try-with-resources 关闭。完整文档验证成功后才开始合并到直播频道。
- 不加载外部 DTD。对默认关闭且不支持设置 DTD 特性的解析器，不强行调用 setFeature。
- Simple XML 仍供其他业务使用；新增的 KXml 依赖仅用于 JVM 测试，不进入应用运行时。

文件路径不再保留所有节目对象；主要常驻解析数据为频道元信息、匹配的当天节目及当前条目。代价是扫描文件两遍。没有测量或承诺具体耗时、峰值内存改善数值。

## 验证

新增 9 项测试，覆盖字段、Unicode / CDATA / 实体、编码、外部 DTD、未知子树、2 万条节目的增量交付、解析中取消、坏 XML、根日期、频道晚声明、ID / 别名优先级、图标和日期筛选。

首轮测试暴露 KXml 不支持显式关闭已关闭的 DTD 特性；调整为检查后再关闭，定向测试通过。最终执行：

```sh
./gradlew :app:testLeanbackJavaArm64_v8aDebugUnitTest \
  :app:testMobileJavaArm64_v8aDebugUnitTest \
  :app:assembleLeanbackJavaArm64_v8aDebug \
  :app:assembleMobileJavaArm64_v8aDebug \
  --offline --console=plain --max-workers=2
```

使用本机 Android Studio JBR；结果 BUILD SUCCESSFUL，退出码 0。

| 验证 | 结果 |
| --- | --- |
| Leanback Java 单测 | 669 项，0 失败 / 错误 / 跳过 |
| Mobile Java 单测 | 643 项，0 失败 / 错误 / 跳过 |
| Leanback Java arm64 Debug | 构建通过 |
| Mobile Java arm64 Debug | 构建通过 |

未执行设备 EPG 加载、真实大源耗时 / 内存分析及 Release 混淆验证。未修改 Python 请求链路或 Cronet 依赖；未自动提交。
