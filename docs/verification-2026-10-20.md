# 提交前检查与验证

日期：2026-10-20。范围为 EPG 流式解析、三阶段 EventBus 收缩、Cronet 删除，以及 QuickJS 升级可行性报告。QuickJS 引擎及版本没有更换。复核未发现阻塞提交的明确缺陷。

检查了 EPG 频道晚声明、别名 / ID 优先级、编码、取消和结果发布边界；播放命令 / 错误的实例归属、主线程交付及释放行为；主页状态和扫码结果的生命周期；Cronet 源码引用、运行时依赖与 APK 内容。数据库结构、配置格式和脚本接口未迁移。

## Gradle 验证

使用本机 Android Studio JBR 执行，结果 BUILD SUCCESSFUL，退出码 0。部分未变化任务复用 Gradle 已通过的增量结果。

```sh
./gradlew :core:test :quickjs:testDebugUnitTest \
  :ijkplayer:testDebugUnitTest :thunder:testDebugUnitTest \
  :chaquo:testPythonBridge \
  :app:testLeanbackJavaArm64_v8aDebugUnitTest \
  :app:testMobileJavaArm64_v8aDebugUnitTest \
  :app:assembleLeanbackJavaArm64_v8aDebug \
  :app:assembleMobileJavaArm64_v8aDebug \
  :app:assembleLeanbackPythonArm64_v8aDebug \
  :app:assembleLeanbackJavaArm64_v8aRelease \
  :app:assembleMobileJavaArm64_v8aRelease \
  :app:lintMobileJavaArm64_v8aDebug \
  --offline --console=plain --max-workers=2
```

| 测试模块 | 项数 | 失败 / 错误 / 跳过 |
| --- | ---: | --- |
| core | 40 | 0 / 0 / 0 |
| quickjs | 3 | 0 / 0 / 0 |
| ijkplayer | 4 | 0 / 0 / 0 |
| thunder | 3 | 0 / 0 / 0 |
| app Leanback Java | 678 | 0 / 0 / 0 |
| app Mobile Java | 657 | 0 / 0 / 0 |
| Java 合计 | 1385 | 0 / 0 / 0 |
| Python 桥接 | 8 | 全部通过 |

三种 Debug 构建、TV / Mobile Java arm64 Release 混淆及资源收缩构建通过。Mobile Debug lint：0 Error / Fatal，296 Warning。`git diff --check` 通过。

## Android 原生冒烟检查

在已有 arm64、API 34 模拟器上，以 app_process 加载本次 Mobile Debug APK 和临时检查类，不安装或启动应用，不修改应用数据。结果：`ANDROID_SMOKE_OK checks=11`，退出码 0。

- 使用 Android 平台真实 XML 解析器，验证 CDATA / 实体 / 中文、无效时间过滤、根日期及截断 XML 异常；外部 DTD 声明未读取不存在的文件。
- 文件入口验证 UTF-16、节目先于频道声明、别名匹配、节目文本和频道图标。
- 使用真实 Android Intent，验证扫码返回成功、取消、缺少 Intent 和缺少地址四种结果。

这些检查没有实际启动相机、播放媒体或操作界面；不替代真实扫码、权限、旋转 / 进程恢复、网络接口播放、超时恢复、字幕 / 弹幕、投屏测试。没有验证其他 Android API / ABI 或 Python Release。QuickJS 单测仍是桥接引用模拟，不代表新内核兼容验证。

Cronet 依赖 / APK 检查及完整打包体积对比详见 [Cronet 报告](cronet-audit.md)。分阶段行为与验证记录见 [EPG 报告](epg-streaming.md)、[EventBus 报告](eventbus-reduction.md) 和 [QuickJS 报告](quickjs-upgrade.md)。这些报告中“未自动提交”记录的是各阶段结束时状态；本次根据用户明确要求统一提交。
