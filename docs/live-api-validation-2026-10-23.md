# 真实直播接口播放验收

代码基线：`085dd71c8`。使用原应用已保存配置中的公开直播入口，未用本地 MP4 代替真实直播。本轮只增加 opt-in 测试 Receiver 的配置加载状态、换直播源、指定频道及广告过滤开关命令，没有修改产品代码。

## 环境与成功结果

- API34 arm64 Google TV 模拟器 emulator-5554，隔离包 `com.fongmi.android.tv.codextest.tv`；保留原安装包与原应用数据。
- 从已有 Kimentanm 配置中的 `https://ghfast.top/https://raw.githubusercontent.com/Kimentanm/aptv/master/m3u/iptv.m3u` 实际加载 M3U；不是只检查入口 HTTP 200。
- CGTN 英语：`https://english-livebkali.cgtn.com/live/encgtn_0.m3u8`，Exo，READY、playing=true，1920×1080，视频画面已截图确认。
- 通过 MediaSession 下一台切到 CGTN 纪录：`https://english-livebkali.cgtn.com/live/doccgtn_0.m3u8`，来源代次 8→9，Exo READY、playing=true，1920×1080。
- 英语频道 MediaSession 暂停后 PAUSED、playing=false；恢复后 READY、playing=true。
- 纪录频道切换 IJK 后仍 READY、playing=true，1920×1080；连续观测 5 秒，位置 47275→52342ms。
- 英语频道实际媒体子清单无 ENDLIST，6 个分片；间隔 6 秒，MEDIA-SEQUENCE 从 5031→5032，确认清单持续更新。
- 测试时 removeAd=true。M3u8AdFilter 位于 Exo 网络入口；普通直播清单按保护分支保留原内容。本次证明开启过滤后直播仍可播放，不代表直播广告已被移除，也不代表 IJK 使用该 Java 过滤器。

## 失败与未覆盖

- 当前配置的咪咕入口 `https://develop202.github.io/migu_video/interface.txt` 返回 HTTP200、469 字节，仅有 M3U 头，没有频道条目。
- `https://epg.pw/test_channels.m3u` 入口返回 HTTP200、约732KB，媒体地址均在 stream1.freetv.fun。本次桌面抽测的 8 条 CCTV 地址均读取超时；应用首个频道停留 BUFFERING、0×0。不能由此断言该列表全部频道均不可用。
- Kimentanm 的部分 CCTV PHP 地址在桌面入口探测返回 HLS，但应用尝试过程中记录到媒体 HTTP404、没有出画面；未把仅 HTTP200 的入口结果算作播放成功，也未据此直接修改播放器。
- 现有 IPv6 列表入口可下载，但未完成其具体频道的播放验收，不计为成功。
- 初次尝试完整聚合配置时，隔离测试包出现 signal9 进程退出，未定位原因；随后改用配置内单独的原始 M3U 列表，以上成功播放期间未复现该退出。因此不把本次结果扩展为完整聚合配置的稳定性验收。
- 未测试手机硬件、完整 EPG、全部频道或长时间网络波动；EPG 本次为0条，不宣称 EPG 通过。
- 日期使用用户时区 Asia/Shanghai 的2026-10-23；模拟器自身时钟不同，不用于报告日期。

## 构建、证据及清理

- TV 隔离验收包构建、安装成功；新增测试命令实际执行成功。
- 实测截图：`/tmp/tv-live-api-20261023/cgtn-en.png`、`cgtn-doc.png`、`cgtn-doc-ijk.png`。
- 日志：`/tmp/tv-live-api-20261023/build-isolated.log`、`build-normal.log`。
- 完成后卸载隔离测试包，删除临时配置数据库快照；本轮未启动本地服务器或增加 adb reverse。
- 两端普通 Java arm64 Debug APK 已重新构建成功，aapt 检查 applicationId 均为 `com.fongmi.android.tv`，无 RefactorReceiver/codextest。
- 本轮不改业务代码，未为设备测试变更重复运行上一轮完整单测；`git diff --check` 通过。
