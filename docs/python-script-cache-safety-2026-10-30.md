# Python 脚本与依赖缓存修复

本轮仅修复Python同名依赖串用，以及失败响应覆盖正常脚本。JS/JAR刷新竞态和Python显式getCache/setCache命名空间不在本轮修改范围。

## 修改

- 每次加载Spider时分配独立依赖目录和模块集合，避免不同片源、相同片源的新旧实例共享同名依赖文件和模块全局变量。
- 保留getDependence、loadModule、loadSpider调用形式；依赖下载由Java全局目录流程移至Python初始化阶段，使用对应实例的目录。构造期loadModule也可以取得实例所属上下文并按需加载。
- 同一实例重复加载依赖复用自己的模块。可重入锁协调加载与销毁，防止其他线程取得半初始化模块，同时允许同线程递归加载。
- 销毁实例时，仅移除该实例的依赖模块和目录，不影响其他片源或后来加载的新实例。
- 下载检查HTTP状态与Python语法；通过后才写同目录临时文件、flush/fsync，再用os.replace替换目标。HTTP错误、语法错误和网络异常不会覆盖原文件。
- 使用requests标准重定向，并增加连接5秒、读取20秒的超时参数。这不是整个请求的绝对总时限。

失败仍向调用方报错，不代表自动运行旧文件，也不保证语法合法的脚本一定能正确执行。脚本自行直接读取共享cache/py路径、使用普通import导入远程依赖等非base.loadModule路径，未做真实片源兼容验证。

## 验证

新增核心回归先验证旧实现失败，再验证修复后通过：HTTP503保留旧文件、非法Python保留旧文件、不同片源及新旧实例依赖隔离。后续新增并发测试先复现半初始化模块问题，再用可重入锁修正。

完整Python桥接测试14项通过，覆盖既有主模块隔离、销毁、新旧实例及新增依赖隔离、网络失败、路径限制、并发初始化。独立审查另验证了同线程循环依赖加载不会死锁。

```sh
./gradlew :chaquo:testPythonBridge \
  :app:assembleLeanbackPythonArmeabi_v7aDebug \
  :app:assembleLeanbackPythonArm64_v8aDebug \
  :app:testLeanbackJavaArm64_v8aDebugUnitTest \
  :app:testMobileJavaArm64_v8aDebugUnitTest
```

本地Python测试使用模拟网络响应和Android导入桩，执行实际Python加载代码，不访问公网。32位和64位Python版APK构建用于验证Chaquopy/Python3.8打包兼容；未进行真实片源和32位真机运行测试。
