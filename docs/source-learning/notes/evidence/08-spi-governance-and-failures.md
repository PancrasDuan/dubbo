# 阶段 8 证据：SPI、治理与异常实验

> 主笔记：[SPI、集群治理、异常与安全](../08-spi-governance-failure-security.md)
> 安全基线：[Dubbo 3.3.6 学习范围威胁模型](../../threat-model.md)
> 验证日期：2026-07-26
> 发布基线：Apache Dubbo 3.3.6，tag commit `f1585880bee4ca7776f44380c47c994217721ffe`
> 证据采集源码基线：`b7868dbb56181bf04e3b0b6c11a77b25ffd2a951`（基于 `dubbo-3.3.6` 的学习提交）

## 1. 验证范围

本阶段采用三类证据：

1. 上游确定性单元测试：验证 SPI 创建/Adaptive/Activate、Filter 链、Router、Failover、超时翻译和序列化类检查。
2. 阶段 3、4 已保存的真实对象树和 Failover 运行实验：验证实际 Filter 顺序、业务异常不重试和 Provider 下线后的重选。
3. 新增的可控 Provider 延迟场景：验证真实 Request/Future 超时、错误码和 Provider 端继续执行行为。

没有连接外部 Nacos，也没有修改动态治理规则的真实 Nacos dataId；配置事件到 Router 的部分使用 Tag/Condition StateRouter 测试和源码事件链证明。控制面断线、恢复证据仍沿用阶段 7 的明确边界。

## 2. 实验 A：SPI 与序列化安全

命令：

```bash
JAVA_HOME=/Users/pancras/Office/jdk-21.0.3.jdk/Contents/Home \
/Users/pancras/Office/apache-maven-3.9.9/bin/mvn \
  -pl dubbo-common \
  -Dtest=ExtensionLoaderTest,ExtensionLoader_Adaptive_Test,ExtensionLoader_Activate_Test,AdaptiveClassCodeGeneratorTest,DefaultSerializeClassCheckerTest,SerializeSecurityManagerTest,SerializeSecurityConfiguratorTest \
  test
```

第一次在受限沙箱中执行时，Surefire 分叉所需的本机 socket 被拒绝，实际运行 0 项；允许本机测试进程后用相同参数重跑成功。成功结果：

| 测试类 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `ExtensionLoaderTest` | 49 | 0 | 0 | 0 |
| `ExtensionLoader_Adaptive_Test` | 15 | 0 | 0 | 0 |
| `ExtensionLoader_Activate_Test` | 1 | 0 | 0 | 0 |
| `AdaptiveClassCodeGeneratorTest` | 1 | 0 | 0 | 0 |
| `SerializeSecurityManagerTest` | 4 | 0 | 0 | 0 |
| `DefaultSerializeClassCheckerTest` | 5 | 0 | 0 | 0 |
| `SerializeSecurityConfiguratorTest` | 18 | 0 | 0 | 0 |
| 合计 | 93 | 0 | 0 | 0 |

总耗时 2.976 秒。覆盖点包括：

- 具名扩展、默认扩展、别名、依赖注入、Wrapper、Adaptive 和销毁；
- Adaptive 从 URL/Invocation 读取扩展键，缺参和非 Adaptive 方法失败；
- Activate 自动启用、显式增删和顺序；
- allow/blocked/alwaysAllowed 前缀通知；
- WARN、STRICT、DISABLE 模式；
- 自动信任接口参数、返回值、异常、泛型和对象字段；
- Serializable 检查和多 Module 配置的放宽语义。

### 2.1 SPI 对象缓存结论

测试与源码共同确认三个不同对象域：

```text
实现类 → raw extension instance
扩展名 → wrapped named instance
SPI 类型 → adaptive instance
```

因此比较对象 identity 时必须说明比较的是 raw、named 还是 adaptive。仅看到同一实现类不能推导最终 Wrapper 对象相同。

### 2.2 序列化类检查结论

测试确认：

- `alwaysAllowed` 可显式覆盖 blocked 前缀；
- 普通自动允许项不会覆盖已 blocked 的前缀；
- STRICT 拒绝未进入 allowlist 的类；
- WARN 拒绝 blocked 类，未知但未 blocked 的类会记录一次告警后加载；
- 默认拒绝未实现 `Serializable` 的非 primitive 类；
- 同一 Framework Manager 已经被某个 Module 放宽后，后来的 Module 不能重新收紧。

最后一点不是建议“使用宽松配置”，而是提醒多 Module 应用必须统一安全设置，避免初始化顺序改变有效策略。

## 3. 实验 B：Filter、Router、LoadBalance 与 Failover

命令：

```bash
JAVA_HOME=/Users/pancras/Office/jdk-21.0.3.jdk/Contents/Home \
/Users/pancras/Office/apache-maven-3.9.9/bin/mvn \
  -pl dubbo-cluster -am \
  -Dtest=DefaultFilterChainBuilderTest,FailoverClusterInvokerTest,AdaptiveLoadBalanceTest,TagStateRouterTest,ConditionStateRouterTest,AbstractClusterInvokerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：18 个 Reactor 模块成功，总耗时 6.000 秒。

| 测试类 | 通过 | 跳过 | 主要覆盖 |
|---|---:|---:|---|
| `DefaultFilterChainBuilderTest` | 2 | 0 | Activate Filter 列表与正反向回调包装 |
| `FailoverClusterInvokerTest` | 9 | 0 | 重试次数、重选、Biz 异常、最终异常 |
| `AbstractClusterInvokerTest` | 10 | 1 | 可用性、选择、重选、空 Invoker |
| `AdaptiveLoadBalanceTest` | 3 | 0 | URL/方法级负载均衡选择 |
| `TagStateRouterTest` | 6 | 0 | 标签规则、force、空结果和地址缓存 |
| `ConditionStateRouterTest` | 17 | 0 | 条件匹配、规则变化和地址位图 |
| 合计 | 47 | 1 | 48 项，1 项跳过 |

Condition/Tag 测试日志包含刻意输入非法地址产生的 error 日志，对应测试仍通过；它们是负向用例的预期观察，不是 Reactor 失败。

### 3.1 动态治理的数据流

从测试与源码断点得到：

```text
DynamicConfiguration / GovernanceRuleRepository
→ TagStateRouter 或 ConditionStateRouter 的规则监听回调
→ YAML/条件表达式解析
→ 更新 RouterRule
→ 按当前 Invoker 列表重建 BitList 缓存
→ SingleRouterChain 先 StateRouter 后普通 Router
→ Cluster 获得过滤后的候选列表
→ Adaptive LoadBalance 选择一个地址
```

地址刷新和规则刷新是两个输入源：Directory 的新 Invoker 列表会触发 Router `notify`，治理规则事件会替换规则并重算缓存。RouterChain 的双链切换避免正在处理旧地址的调用误用新缓存。

### 3.2 空地址的两个来源

| 空结果来源 | 证据位置 | 排查重点 |
|---|---|---|
| Registry/Directory 本来没有 Invoker | `AbstractClusterInvoker.checkInvokers` | Provider 注册、订阅、empty protection、Directory 刷新 |
| Router 把非空列表过滤为空 | `SingleRouterChain.simpleRoute` | 每层 Router 输入输出、规则、force、fail-fast |

两者都可能到达 `NO_INVOKER_AVAILABLE_AFTER_FILTER`，错误码为 6。只凭最终异常文本无法区分，应保存 RouterSnapshot 或在 Directory.list 前后比较数量。

### 3.3 Failover 决策快照

| 输入 | 行为 |
|---|---|
| URL `retries=n` | 总尝试 `n + 1` 次 |
| RpcContext 单次 retries | 覆盖 URL，读取后删除 |
| 下一次尝试 | 重新 Directory.list，再执行选择 |
| 已调用 Invoker | 重选时尽量避开，候选不足时允许复用 |
| `RpcException.isBiz()` | 立即抛出，不重试 |
| 其他 RpcException | 保存最后异常，继续到剩余尝试 |
| 全部失败 | 使用最后错误码，附总尝试次数、Provider 集合和最后 cause |

阶段 3 的真实双 Provider 实验补充了运行证据：停止 20880 后，第一次命中 20880 得到非业务失败，Failover 重新 list/reselect，第二次命中 20881 成功；`biz-failure` 场景即使单次 override 为 2，也只调用 20880 一次。

## 4. 实验 C：ExceptionFilter 与 timeout 组件

命令：

```bash
JAVA_HOME=/Users/pancras/Office/jdk-21.0.3.jdk/Contents/Home \
/Users/pancras/Office/apache-maven-3.9.9/bin/mvn \
  -pl dubbo-rpc/dubbo-rpc-api -am \
  -Dtest=ExceptionFilterTest,TimeoutFilterTest,TimeoutCountDownTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：7 项全部通过，0 failure、0 error、0 skip，总耗时 10.041 秒。

| 测试类 | 通过 | 覆盖 |
|---|---:|---|
| `ExceptionFilterTest` | 4 | 声明异常、运行时异常、缺类兼容包装 |
| `TimeoutFilterTest` | 2 | 超时 countdown 到期告警 |
| `TimeoutCountDownTest` | 1 | 剩余时间与到期判断 |

Provider `TimeoutFilter` 只在响应完成时检查倒计时并记录告警，不中断业务线程。这与后面的真实延迟实验一致。

## 5. 实验 D：Hessian2 不可信类型

命令：

```bash
JAVA_HOME=/Users/pancras/Office/jdk-21.0.3.jdk/Contents/Home \
/Users/pancras/Office/apache-maven-3.9.9/bin/mvn \
  -pl dubbo-serialization/dubbo-serialization-hessian2 -am \
  -Dtest=Hessian2SerializationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：10 项全部通过，0 failure、0 error、0 skip，总耗时 2.925 秒。

观察结果：

- STRICT 模式写入/读取未信任的 `TestPojo` 时记录拒绝日志；
- 已信任且实现 Serializable 的类型正常往返；
- 未实现 Serializable 的对象被拒绝；
- 部分流中声明的未知不可信类型在 Hessian2 兼容路径被读为 Map，而不是实例化目标 Java 类。

Map 回退仍可能携带不可信字段、深层结构或大体积数据，因此必须继续执行参数大小限制与业务校验。

## 6. 实验 E：真实 3000 ms 超时

### 6.1 学习示例改动

Provider 新增只对 `name=timeout` 生效的延迟开关：

```text
--learning.provider-delay-ms=3500
```

默认值为 0，不改变 baseline、failover 和 biz-failure 场景。Consumer 新增：

```text
--learning.scenario=timeout --learning.call-count=1
```

Consumer 引用仍使用 `timeout=3000`、`retries=0`。构建命令：

```bash
JAVA_HOME=/Users/pancras/Office/jdk-21.0.3.jdk/Contents/Home \
/Users/pancras/Office/apache-maven-3.9.9/bin/mvn \
  -pl :source-learning-provider,:source-learning-consumer -am \
  -DskipTests package
```

57 个 Reactor 模块全部成功，总耗时 57.069 秒，两个 Spring Boot jar 均重新打包。

### 6.2 运行命令

Provider：

```bash
/Users/pancras/Office/jdk-21.0.3.jdk/Contents/Home/bin/java \
  -jar dubbo-demo/dubbo-demo-source-learning/source-learning-provider/target/source-learning-provider-3.3.6.jar \
  --learning.provider-delay-ms=3500
```

Consumer：

```bash
/Users/pancras/Office/jdk-21.0.3.jdk/Contents/Home/bin/java \
  -jar dubbo-demo/dubbo-demo-source-learning/source-learning-consumer/target/source-learning-consumer-3.3.6.jar \
  --learning.scenario=timeout --learning.call-count=1
```

### 6.3 关键时间线

```text
17:24:25.345 Consumer 发送 learning-01，name=timeout
17:24:25.350 Consumer 选中 127.0.0.1:20880
17:24:25.375 Provider 收到请求并进入 3500 ms 延迟
17:24:28.380 Consumer 捕获 RpcException，code=2
               cause=Timeout after 3000ms waiting for result
17:24:28.879 Provider 完成约 3504 ms 处理，ProfilerServerFilter 告警
```

Consumer 的最终学习标记：

```text
LEARNING_EXPECTED_TIMEOUT requestId=learning-01
exceptionType=org.apache.dubbo.rpc.RpcException
code=2
message=... Tried 1 times ... Timeout after 3000ms waiting for result
```

该实验确认：

1. `DefaultFuture` 等待到期，经 `DubboInvoker` 变为 `TIMEOUT_EXCEPTION`。
2. `retries=0` 使总尝试次数为 1。
3. Consumer 关闭连接和退出不等于 Provider 业务线程被取消。
4. Provider 的 Timeout/Profiler Filter 在业务返回后才观察到超时并记录告警。
5. 若方法不是幂等的，开启超时重试可能在首个 Provider 仍执行时向另一 Provider 发起重复操作。

运行结束后已用 Ctrl-C 停止 Provider，Netty 20880 监听和 Dubbo 资源均已关闭。

## 7. 异常矩阵证据汇总

| 类别 | 本阶段证据 | 结果 |
|---|---|---|
| Provider 未启动/地址不可用 | 阶段 0、3 直连与双 Provider 实验 | 地址为空在调用前失败；已有不可用地址进入连接/服务失败 |
| Nacos 中断/恢复 | 阶段 7 Mock 与 retry 测试 | SDK 重连、请求重试、Failback 和元数据重试是不同保护层 |
| Router 为空 | Tag/Condition/AbstractCluster 测试 | Router 后空列表在 Cluster 前置检查失败，码 6 |
| 网络断开 | 阶段 3 Provider 停止实验与 DubboInvoker 测试路径 | 非 Biz 失败可触发重新 list 和重选 |
| Future 超时 | 本阶段真实 3500/3000 ms 实验 | 码 2，总尝试 1，Provider 继续执行 |
| 业务异常 | 阶段 3 `biz-failure` | 只调用一次，不因 override=2 重试 |
| 序列化不可信类型 | Hessian2 10 项测试 | STRICT 拒绝未知目标类，Serializable 检查有效 |
| 动态治理规则 | Tag/Condition StateRouter 23 项测试 | 规则与地址变化都会重建路由缓存 |

## 8. 故障证据采集模板

以后复验新协议、注册中心或升级版本时，每个故障至少保存：

```text
版本/tag/commit：
唯一 requestId：
Consumer URL 的 timeout/retries/cluster/loadbalance：
Directory 原始 Invoker 数量：
每层 Router 输入输出数量：
每次尝试的目标地址：
原始 Throwable 类型：
RpcException code / isBiz / cause：
Provider 是否实际收到、是否执行完成：
最终结果与总耗时：
恢复后是否复用 Invoker/Client：
```

没有 Provider 日志时，不能仅凭 Consumer timeout 断言服务端已中断；没有 Router 输入输出时，也不能把码 6 直接归因于注册中心。

## 9. 证据结论

阶段 8 共执行 158 项定向测试，其中 157 项通过、1 项跳过，无失败和错误；另完成 57 模块学习示例构建及一次真实超时调用。

这些证据足以支持 G8 对 SPI 选择、Activate 顺序、治理职责和主要异常翻译的验收。环境相关的缺口仍是：真实 Nacos 动态规则变更、控制面 TLS/ACL 和生产认证插件配置；它们已经被明确放入威胁模型的部署方责任，而没有写成框架默认保证。
