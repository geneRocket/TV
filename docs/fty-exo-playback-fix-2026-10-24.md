# 饭太硬点播 Exo Source error 排查与修复

基线：`ec2ae1cd4`。接口为用户提供的 `https://gh-proxy.com/https://raw.githubusercontent.com/qist/tvbox/refs/heads/master/fty.json`。

## 已确认原因

1. 模拟器时间错误。用户堆栈发生在设备时间2026-08-28，实际客户端日期为2026-10-24。对应代理请求报 CertificateNotYetValidException：视频站证书从2026-10-06 16:16:09 +08:00生效，设备却用8月28日校验证书。HTTPS失败后无法返回有效媒体，Exo产生格式识别错误；HLS回退也报“不以EXTM3U开头”。
2. 本地HLS代理首次格式选择不正确。`http://127.0.0.1:9978/proxy?do=m3u8&url=...` 没有媒体扩展名，原代码首次仍创建ProgressiveMediaSource。校准时间后，旧版需第2次请求、回退HLS才播放成功。

开始排查时的旧SIGKILL记录已确认是force-stop，不作为崩溃证据。用户堆栈为播放器Source error，本次未复现Java进程崩溃。

## 修复

- 通过Android alarm服务将模拟器系统时间校准至宿主的2026-10-24；保留HTTPS证书验证和信任库。
- ExoUtil首次创建MediaItem时识别HTTP(S)回环地址/proxy的独立do=m3u8参数，直接选择HLS MIME。
- 显式格式优先；支持备用端口、localhost、IPv6回环及参数解码。远程地址、普通视频路径、py/mp4代理、嵌套URL文本不强制改为HLS；原URL和请求头保持。
- 此代码不绕过其他设备上的错误日期，设备时钟仍应校准。

## 验证

- 饭太硬配置加载49个站点；厂长home/homeVideo实际返回57条，首条《峡谷》详情可播放，Exo解码1920×1038。
- 用户原phimgood 20261017/32712_ee30a32a/index.m3u8代理地址在校准时间后返回HTTP200、HLS MIME、166916字节、1463个分片及ENDLIST。
- 修复包播放同一原始代理URL：首次请求即READY、recovery=IDLE，Exo MIME为application/x-mpegURL、1920×800；进度从60推进到47781ms，已截图确认，removeAd=true。
- 修复后测试进程的有界日志中，UnrecognizedInputFormatException、CertificateNotYetValidException、FATAL EXCEPTION计数均0。
- 新增4项MIME测试，其中2项修复前失败、修复后通过；两端全部Java单测TV774 / Mobile749，共1523项，失败/错误/跳过均0。
- 两端普通Java arm64 Debug构建成功；aapt确认包名com.fongmi.android.tv，无测试Receiver；git diff --check通过。
- 证据：`/tmp/tv-fat-vod-20261024/mime-red.log`、`final-checks.log`、`fixed-playback.png`。

## 部署与边界

- 卸载隔离测试包，移除本轮19978 adb forward；普通TV修复包以install -r更新至当前模拟器并启动，未清除原应用数据。临时数据库副本删除。
- 测试入口增加点播配置状态、站点home/homeVideo探测及当前Exo MIME，仅显式启用隔离包时生效。
- 未承诺全部49个站点可用，未覆盖所有电视硬件。复测包含真实站点详情及用户同一故障URL的直接播放，不是完整分类/搜索验收。

## removeAd 开启后的追加复测

- 用户要求后，将原应用 remove_ad 设为true；重启原应用后再次读取确认仍为true，decode_2=1和exo_buffer=15保留。
- 隔离测试包同样先开启removeAd，再加载饭太硬49站配置。厂长《峡谷》首次请求READY、Exo、1920×1038；通过真实进度条回调拖至600000ms后，后续观测READY、playing=true、位置694825ms，画面已截图确认。
- 原故障HLS代理地址首次请求READY、application/x-mpegURL、1920×800；持续至82194ms仍playing=true、recovery=IDLE、removeAd=true。
- 本次测试进程有界日志未出现格式识别错误、证书日期错误、FATAL或ANR记录。进度观测有一次3秒超时，随后状态正常；模拟器也记录到掉帧，未做性能归因，不宣称完成流畅度验收。
- 未记录到本次清单的广告删片摘要，因此仅确认开启过滤不阻断这些样本的播放，不据此宣称实际删除了多少广告。
- 证据目录：`/tmp/tv-removead-retest-20261024/`，含fty-seek.png、hls-removead.png、构建日志。
- 本轮未改产品代码；普通TV Debug重新构建成功，无测试Receiver；隔离包已卸载，原应用已重新启动并保持广告过滤开启。
