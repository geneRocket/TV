# QuickJS 升级与替换核查

核查日期：2026-10-20。结论：当前封装已是最新正式版；本轮不更换生产引擎。现成 QuickJS-NG 候选不能同时保持当前 32 位 ABI 和旧字节码脚本兼容。未发现可直接替换且已验证不丢功能的制品。

## 当前实现

- `quickjs/build.gradle:25`：`wang.harlon.quickjs:wrapper-android:3.2.3` 和 `wrapper-java:3.2.3`。
- Maven Central 两项制品的 latest / release 均为 3.2.3，元数据最后更新时间为 2025-07-01。上游 main 相比 3.2.3 只有 README 改动，没有尚待升级的实现修复。
- 3.2.3 源码标签固定的引擎提交为 `HarlonWang/quickjs@f209a8556bc6e4e87cd0fb8c0932b47c5f3eeae5`，其 VERSION 为 `2024-02-14`。封装版本与引擎版本不同，不能将“封装最新版”理解为“内核最新版”。
- 实际缓存 AAR 含 arm64-v8a、armeabi-v7a、x86、x86_64；应用现有 flavor 使用其中前三项。
- 上游 3.2.0 已声明支持 16 KiB 页，不需要为这一项盲目切换封装。

## 兼容性约束

`crawler/Spider.java` 的模块加载器既接受 JS 源码，也接受 `//bb`；字节码不仅可能出现在入口，还可能出现在导入的模块。`Module.bb` 解码并调整旧格式头后直接交给当前内核。

QuickJS 官方说明字节码格式绑定具体引擎版本。因此，升级 Bellard 内核、切换 QuickJS-NG 或其他引擎均不能默认读取现有字节码；修改版本头不能转换指令、原子表和对象序列化格式。没有源码的旧脚本也不能重新编译。这里是兼容性风险判断，未将错误格式字节码送入新内核进行执行实验。

宿主还依赖 ES Module 加载 / 相对路径、Promise / async、单线程执行、JS 对象引用释放、注解导出方法、HTTP、DOM 选择、加解密、定时器和本地存储。替换方案必须覆盖这些桥接语义，不能仅验证 `1 + 1`。

## 核对的替换候选

`ZhiJianMesh/QuickJS` 是基于现有封装演进的 QuickJS-NG 候选。核查其 0.2.2 源码及实际发布 AAR：

| 项目 | 当前 3.2.3 | 候选 0.2.2 |
| --- | --- | --- |
| 引擎 | QuickJS fork，VERSION 2024-02-14 | QuickJS-NG，源码版本宏 0.15.0 |
| Java 命名空间 | `com.whl.quickjs.wrapper` | `cn.net.zhijian.quickjs` |
| 释放上下文 | `destroy()` | `close()` |
| 发布 AAR 的 ABI | arm64-v8a / armeabi-v7a / x86 / x86_64 | arm64-v8a / x86_64 |
| 候选最低 Android API | — | 24，与本应用最低版本相符 |
| 当前 `//bb` 兼容 | 现有实现路径 | 未建立，不能假设兼容 |

检查的是下载到内存中的正式发布包，未将候选二进制加入仓库或替换现有依赖。候选 AAR 约 1.30 MB，但它缺少两个 ABI，不能据此宣称等价条件下更小。QuickJS-NG 上游当前最新发布为 0.17.0，也不能把候选封装视为已经包含该版本。

## 建议路径

1. **保留全部现有功能时**：当前继续使用 3.2.3。后续迁移应先建立引擎接口和真实脚本测试集，旧内核继续承接旧字节码整条依赖链，新内核先承接可重新编译的源码脚本。这会增加包体和维护成本，不能作为无成本性能升级。
2. **明确放弃旧字节码及 32 位设备时**：才考虑单引擎迁移到 QuickJS-NG；仍需完整迁移宿主桥接，不能只替换 Gradle 坐标。
3. **要求新内核且保留 32 位源码支持时**：需要自己从源码构建并维护所需 ABI，随后验证 Android 7、arm64 / armv7 / x86、16 KiB ELF 对齐、R8 及实际脚本。旧字节码兼容问题仍独立存在。

本轮没有已测得的性能瓶颈数据，不能断言换引擎后首页、搜索或播放必然更快。

## 验证边界

完成了本地调用链、版本元数据、固定源码版本、Java API、当前及候选 AAR 内容检查。现有 `AsyncTest` 三项使用 Java 代理模拟引用，不执行原生引擎，不能证明新引擎兼容。此次没有修改 QuickJS 代码或依赖，没有进行替换后的构建、真实脚本或性能验证。EPG 等此前未提交改动保留。

## 一手来源

- [现有封装发布记录](https://github.com/HarlonWang/quickjs-wrapper/releases)
- [Android 制品版本元数据](https://repo.maven.apache.org/maven2/wang/harlon/quickjs/wrapper-android/maven-metadata.xml)
- [Java 制品版本元数据](https://repo.maven.apache.org/maven2/wang/harlon/quickjs/wrapper-java/maven-metadata.xml)
- [3.2.3 与 main 差异](https://github.com/HarlonWang/quickjs-wrapper/compare/3.2.3...main)
- [固定内核 VERSION](https://github.com/HarlonWang/quickjs/blob/f209a8556bc6e4e87cd0fb8c0932b47c5f3eeae5/VERSION)
- [QuickJS 字节码说明](https://bellard.org/quickjs/quickjs.html#Script-evaluation)
- [候选封装 0.2.2 发布及 AAR](https://github.com/ZhiJianMesh/QuickJS/releases/tag/0.2.2)
- [候选内核版本宏](https://github.com/ZhiJianMesh/QuickJS/blob/0.2.2/native/quickjs-ng/quickjs.h)
- [QuickJS-NG 0.17.0](https://github.com/quickjs-ng/quickjs/releases/tag/v0.17.0)
