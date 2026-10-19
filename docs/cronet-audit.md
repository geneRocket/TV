# Cronet 冗余核查与删除

日期：2026-10-20。已删除仓库未使用的 Cronet 两项直接依赖和两条 keep 规则。Java / Python 内置请求链路未接入 Cronet；删除后测试、三种 Debug APK 和 TV Java Release 混淆构建通过。未自动提交。

## 删除范围

- `app/build.gradle`：移除 `com.google.net.cronet:cronet-okhttp:0.1.0` 和 `org.chromium.net:cronet-embedded:76.3809.111`。
- `app/proguard-rules.pro`：移除 `org.chromium.net.**`、`com.google.net.cronet.**` 的 keep 规则。
- 随直接依赖一起退出运行时依赖图的还有 cronet-common 和 cronet-api；不需要额外全局 exclude。

## Python 接口

- `chaquo/src/main/python/app.py:50`：脚本下载使用 `requests.get`。
- `chaquo/src/main/python/base/spider.py:94`：`fetch` 使用 `requests.get`，`post` 使用 `requests.post`。
- `chaquo/build.gradle:24`：Python 环境直接安装 `requests`。
- `chaquo/src/main/java/com/fongmi/chaquo/Spider.java:97`：代理入口调用 Python `localProxy`；下载入口调用 Python `download`。Java 桥接层未接入 Cronet。
- `getProxyUrl` 返回本地 HTTP 服务地址，并不表示选择 Cronet 网络栈。

因此，继承内置 Spider 基类、使用其 fetch/post 的 py 接口不需要 Cronet。外部脚本可以自行选择请求库，也可能经 Java 互操作主动调用 `org.chromium.net` 或 Cronet 适配器；仓库扫描不能覆盖未提供的动态脚本。播放 URL 的实际播放另由播放器处理，内置 Java 网络入口使用 OkHttp。

## 静态及依赖证据

- 删除前全仓源码及资源检索仅发现上述两项依赖和两条包级 keep 规则，没有发现调用、导入或反射名称；删除后业务源码和构建规则均无 Cronet 引用。
- 仓库自带 AAR / JAR / DEX / SO（包含嵌套 classes.jar）未发现 Cronet 包名或名称引用。
- 已解析 Leanback Java Debug、Mobile Java Debug、Leanback Python Debug 和 Leanback Java Release 的运行时依赖。扫描其中 190 个非 Cronet 制品文件、26,910 个 class，未发现对 Cronet 包的引用。数量包含变体间重复，不代表唯一类数；静态扫描不覆盖运行时拼接类名。
- 删除前四个变体均引入 `cronet-okhttp:0.1.0`、`cronet-embedded:76.3809.111`、`cronet-common:76.3809.111`、`cronet-api:98.4758.101`。删除后重新解析同四个运行时依赖图，Cronet 制品数均为 0。最终审计未使用临时排除依赖的参数。

## 已测体积

删除前原生库 `libcronet.76.0.3809.111.so` 占 **3,823,360 字节（约 3.65 MiB）**，另有 Java 类和 `keep_cronet_api.xml`。

最初增量打包虽然移除了条目，却保留 APK 空闲区块，文件仅减少 159 字节。因此另外移动三种变体的打包缓存及旧输出到 `/tmp`，让前后两组都重新完整打包。基线通过仓库外 init script 临时加入原两项依赖，仓库源码始终保持删除状态；最终构建不带基线参数。

| arm64 Debug APK | 删除前（字节） | 删除后（字节） | 减少（字节） | 减少（MiB） |
| --- | ---: | ---: | ---: | ---: |
| Leanback Java | 64,218,670 | 60,171,399 | 4,047,271 | 3.86 |
| Mobile Java | 63,487,665 | 59,473,162 | 4,014,503 | 3.83 |
| Leanback Python | 82,685,994 | 78,655,107 | 4,030,887 | 3.84 |

三种 Debug APK 及 TV Java Release APK 均已检查 ZIP 条目和 DEX：无 Cronet 原生库 / 资源条目，也没有 `Lorg/chromium/net/` 或 `Lcom/google/net/cronet/` 类描述符。Release APK 为 46,149,234 字节；未测量其删除前的完整打包基线。

没有初始化调用的证据，因此不能把体积收益推断成已测得的启动速度、播放速度或内存收益。

## 验证结果

本轮使用 Android Studio JBR。最终完整打包、测试及依赖审计组合命令结果为 BUILD SUCCESSFUL、退出码 0；构建 / 测试任务如下：

```sh
./gradlew :chaquo:testPythonBridge \
  :app:testLeanbackJavaArm64_v8aDebugUnitTest \
  :app:testMobileJavaArm64_v8aDebugUnitTest \
  :app:assembleLeanbackJavaArm64_v8aDebug \
  :app:assembleMobileJavaArm64_v8aDebug \
  :app:assembleLeanbackPythonArm64_v8aDebug \
  :app:assembleLeanbackJavaArm64_v8aRelease \
  --offline --console=plain --max-workers=2
```

| 检查 | 结果 |
| --- | --- |
| Leanback Java 单测 | 678 项，0 失败 / 错误 / 跳过 |
| Mobile Java 单测 | 657 项，0 失败 / 错误 / 跳过 |
| Python 桥接单测 | 8 项通过 |
| Leanback / Mobile Java arm64 Debug | 均通过 |
| Leanback Python arm64 Debug | 通过 |
| Leanback Java arm64 Release | 混淆及资源收缩构建通过 |
| 四个运行时依赖图 | 均无 Cronet |
| 四个输出 APK | 均无 Cronet 条目及上述类描述符 |

Python 单测覆盖模块隔离等桥接行为，不覆盖真实网络和播放。未执行实际设备接口播放、外部 Java / Python 动态脚本，以及其余 ABI / Release 变体的构建验证；外部脚本若主动调用宿主 Cronet 类，仍需单独迁移。此前 EPG、EventBus 等工作区改动保留。
