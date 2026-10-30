# JS DOM 缓存优化

`Parser` 的 `pdfa` 与 `pdfh` 原先分别缓存一个 Document。同一份 HTML 先提取列表再提取字段时，两条路径会各执行一次 `Jsoup.parse`。

现在优先复用另一槽位中内容相同的 Document，仍保留两个槽位，避免提取列表片段时淘汰整页缓存。匹配使用字符串内容相等，不依赖 Java 字符串实例。Parser 的选择操作不修改原文档，排除节点前已有 `Elements.clone()`，因此这两条内部路径可以共享文档。

## 验证

`CacheTest` 覆盖双向复用、不同字符串实例、连续片段提取后整页保留、内容改变后的失效。优化前四项均失败，优化后通过。

2026-10-30 本地 JDK 17、Jsoup 1.22.2 微基准：构造含 500 个 article 的 31,793 字符 HTML，每轮使用 200 个新 Cache，依次调用 `getPdfa(html)` 和 `getPdfh(new String(html))`。预热六轮后，交替测量前后实现九轮，取中位数：169.2 ms → 85.0 ms，减少 49.7%。这个结果只衡量重复建树路径，不包含 JS 引擎、选择器、网络和设备运行开销，不能视为首页或搜索整体提速比例。

执行相关测试：

```sh
./gradlew :quickjs:testDebugUnitTest
./gradlew :app:testLeanbackJavaArm64_v8aDebugUnitTest
./gradlew :app:testMobileJavaArm64_v8aDebugUnitTest
```
