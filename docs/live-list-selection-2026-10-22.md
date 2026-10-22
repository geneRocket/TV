# TV 直播列表选中态拆分

基线：`132b0cb49`。

本轮选择“直播列表内容与播放高亮”这一明确功能继续拆分，没有改动 PlaybackController、播放控制流或手机列表。

## 职责

- Leanback `LiveListSelection` 接管当前展示分组的去重、频道列表替换、EPG 列表替换、频道/节目高亮及局部刷新。
- Activity 保留 Adapter/Presenter 的创建、分组过滤与收藏增删、遥控焦点/滚动、播放入口、列宽及文字呈现。组件不会发起播放请求。
- 延迟局部刷新使用 CallbackScope；替换列表/reset 时取消旧任务，close 时取消两类列表任务并拒收后续操作。执行延迟刷新时重新查找位置，保留原有“布局计算期间等待，项目消失后不通知”的行为。
- 保留原 Channel/EpgData 的 equals 规则、同组引用去重、频道与 EPG 高亮相互独立，以及 EPG 替换后忘记上次激活项的语义。

这是职责提取和回调生命周期收口，没有设备复现证据支持把它描述成某个用户可见故障的修复。

## 验证

新增 7 项 JVM 行为测试：频道高亮切换与清除、频道/EPG 独立、同组去重及 reset、布局结束后重查位置、项目已删除、EPG 替换后拒收旧刷新、close 后取消双方任务并拒收后续操作。测试替身只覆盖 Android TextUtils 所在的 bean getter，保留实际 equals 和选中态实现；不替代 RecyclerView 设备测试。

执行两端 Java arm64 Debug 单测、assemble 和 lint 任务，完整命令退出成功：

- TV 765 项，0 失败/错误/跳过；包含新增 7 项，TV 单测实际重新执行。
- Mobile 740 项，0 失败/错误/跳过；代码未改，单测与构建任务复用 Gradle UP-TO-DATE 结果。
- 两端 Debug 构建成功；lint 无 Error/Fatal，既有警告 TV184 / Mobile296。
- `git diff --check` 通过。
- 构建日志：`/tmp/tv-live-selection-final.log`。

本轮没有重新运行设备遥控焦点、滚动及真实播放矩阵。PlaybackController 的引擎兼容和会话创建/释放仍是后续可独立审查的边界，本轮未扩大修改范围。
