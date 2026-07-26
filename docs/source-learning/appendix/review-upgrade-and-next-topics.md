# 复习题、边界对比与升级清单

> 总入口：[Dubbo 3.3.6 源码知识地图](../notes/09-knowledge-consolidation.md)
> 使用方法：先遮住参考答案口述，再沿链接验证；升级时复制第 5 节形成独立版本差异记录。

## 1. 十分钟口述提纲

不看文档，依次说明：

1. `@DubboReference` 如何变成业务 Proxy，Proxy 下游有哪些 Invoker 层。
2. Directory、Router、LoadBalance、Cluster 分别输入什么、输出什么。
3. `DubboInvoker` 如何创建 Request，`DefaultFuture` 为什么必须先注册再发送。
4. Provider 如何从 request path/version/group 找到 Exporter 和业务 Bean。
5. Response 如何按 requestId 回到原业务线程，为什么同步接口仍使用异步网络内核。
6. `@DubboService`、`ServiceConfig`、Exporter 和 Server 如何创建与复用。
7. Spring refresh/close 事件如何启动和停止 Dubbo Application/Module。
8. Nacos 接口级实例与应用级实例如何分别变成接口 Invoker。
9. Adaptive、Activate、Wrapper 的差别，以及 URL 如何选择扩展。
10. 网络、超时、业务和序列化异常在哪里分类，哪些可能重试。

能在十分钟内说清主链并指出每个问题的验证入口，说明已经形成可用知识结构；记不住类名时可以用[源码索引](concepts-source-and-spi-index.md)补齐。

## 2. 核心复习题与参考答案

### 2.1 架构与对象

**Q1：为什么不能只按 Maven 模块理解运行链路？**

参考答案：Maven 模块描述编译和依赖边界，运行链路由 Proxy、Invoker、Filter、Directory、Client、Exporter 等对象跨模块组合。一个调用栈会穿越 `dubbo-config`、`dubbo-cluster`、`dubbo-rpc` 和 `dubbo-remoting`，还会被 SPI Wrapper 插入额外节点。参见[模块与运行时地图](../notes/01-architecture-overview.md)。

**Q2：URL 为什么不只是地址？**

参考答案：URL 同时携带 protocol、address、service key、方法级参数、attachments/attributes 和 ScopeModel；Adaptive、Filter 激活、LB、Cluster、Client/Server 创建都从中取配置。参见[URL 装配指令](../notes/05-service-export-and-reference.md#6-url-不是日志字符串而是装配指令)。

**Q3：Proxy 和 Invoker 的职责差别是什么？**

参考答案：Consumer Proxy 把 Java 方法变成 Invocation；Invoker 是统一执行抽象，可以代表 Cluster、Filter 节点或具体协议地址。Provider 侧 ProxyInvoker 做反向转换，把 Invocation 调用到业务对象。

**Q4：Exporter 与 Server 为什么不能混为一谈？**

参考答案：Exporter 是服务级发布句柄，按 service key 管理 Provider Invoker；Server 是地址级监听资源，多个服务可以复用同一端口。unexport 一个服务不一定关闭 Server，只有没有服务或整体销毁时才关闭监听。参见[创建与复用实验](../notes/evidence/05-creation-sharing-and-destroy.md)。

**Q5：ScopeModel 解决什么问题？**

参考答案：它把 Framework、Application、Module 的扩展、Bean、配置和 ClassLoader 生命周期组织起来；相同 SPI 名称在不同作用域不一定是同一实例。它不是 Spring ApplicationContext 的别名。

### 2.2 一次调用

**Q6：Consumer 的四层治理次序是什么？**

参考答案：Directory 取得当前地址；Router 按规则缩减候选；LoadBalance 为一次尝试选择一个地址；Cluster 组织一次或多次尝试和重选。Filter 在这些对象外处理横切逻辑。

**Q7：为什么 Future 要在发送前注册？**

参考答案：Response 可能非常快，如果 Request 已发送但 requestId 尚未放进 Future map，接收线程无法找到等待者。先注册再发送保证 Response 一定有匹配入口。参见[DefaultFuture](../notes/04-protocol-remoting-provider.md#3-defaultfuture为什么一定要先注册再发送)。

**Q8：同步 RPC 为什么仍然是异步内核？**

参考答案：发送、网络 IO 和 Response 完成都以 Request/Future 异步工作；同步语义只是业务调用线程在结果边界等待并 recreate Result。

**Q9：Provider 如何找到目标服务和方法？**

参考答案：解码得到 path、version、group、method 和 parameter descriptor；DubboProtocol request handler 用 service key 查 Exporter，再经过 Provider Filter 和 ProxyInvoker 调到业务 Bean。方法描述也参与重载和泛化调用安全校验。

**Q10：一次调用至少有哪些线程边界？**

参考答案：Consumer 业务线程、Consumer Netty IO、Provider Netty IO、Provider 业务线程、Response completion/回调线程。具体线程名随 dispatcher、threadpool 和同步/异步模式变化。

### 2.3 暴露、引用与 Spring

**Q11：Provider 暴露的最短对象链是什么？**

参考答案：ServiceBean/ServiceConfig → ProxyFactory.getInvoker → Adaptive Protocol.export → Wrapper/Filter/Listener → DubboProtocol.export → Exporter/Server → 注册和元数据发布。

**Q12：Consumer 引用的最短对象链是什么？**

参考答案：ReferenceBean/ReferenceConfig → Protocol.refer → Directory → ClusterInvoker → ProxyFactory.getProxy → 字段注入。直连路径可以跳过 Registry，但不会跳过具体协议 Invoker。

**Q13：Spring Boot 属性何时进入 Dubbo？**

参考答案：starter/配置绑定先把 Spring Environment 转成 Dubbo Config bean；注解处理器注册 ServiceBean/ReferenceBean；refresh 事件驱动 Deployer 启动。远端配置还会通过 Dubbo Environment 参与最终配置，二者优先级不能混为一个机制。

**Q14：Spring 关闭时为什么要看多个资源？**

参考答案：关闭涉及 unexport、unregister、unsubscribe、Invoker/Directory/Client/Server/Registry 销毁和 ScopeModel 解绑。只看到 Spring context close 或端口关闭不足以证明全部释放。

### 2.4 Nacos 与服务发现

**Q15：同一个 Nacos 地址为什么可能有多个 Client？**

参考答案：接口级 Registry、应用级 ServiceDiscovery、Config Center 和 Metadata Center 是独立逻辑角色，各自创建 NamingService 或 ConfigService，并有独立监听和生命周期。

**Q16：接口级 Nacos Instance 保存什么？**

参考答案：serviceName 由接口/version/group 编码，Instance 的 ip/port 和 metadata 可直接恢复 Provider URL，属于较自描述的数据。

**Q17：应用级实例为什么还需要 MetadataInfo？**

参考答案：应用实例只代表进程及紧凑 endpoint/revision；Consumer 还要通过接口到应用映射找到应用，再按 revision 获取 MetadataInfo，才能恢复具体接口和协议 URL。

**Q18：`metadata-type=local` 是否从 Consumer 本地文件读元数据？**

参考答案：不是。Consumer 根据实例 endpoint 临时引用 Provider 的内部 MetadataService，远程获取 Provider 本地维护的元数据。

**Q19：MigrationInvoker 会合并两套地址再做 LB 吗？**

参考答案：不会。它同时持有接口级和应用级两条候选 Invoker 链，每次根据 migration step、地址比较和可用性选择其中一条，必要时回退。

### 2.5 SPI、失败与安全

**Q20：Adaptive、Activate、Wrapper 的一句话区别是什么？**

参考答案：Adaptive 按 URL/Invocation 选一个具名扩展；Activate 按条件和顺序取一组扩展；Wrapper 在具名扩展外透明套一层对象。

**Q21：Filter 列表顺序为什么等于请求执行顺序？**

参考答案：Activate 先得到排序列表，FilterChainBuilder 从尾向前包装，所以列表第一个成为最外层，请求最先进入、响应最后返回。

**Q22：Failover 总尝试次数如何计算？**

参考答案：`retries + 1`；RpcContext 单次 override 优先并在读取后删除。每次重试前重新 Directory.list，然后重选地址。

**Q23：业务异常为什么不重试？**

参考答案：Failover 捕获 `RpcException` 后检查 `isBiz()`，业务异常立即抛出。阶段 3 实测即使 override retries=2，Provider 也只收到一次调用。

**Q24：Consumer timeout 是否会取消 Provider 方法？**

参考答案：默认不会。阶段 8 实测 Consumer 3000 ms 超时后，Provider 仍执行到约 3500 ms 并产生超时告警。必须用幂等、截止时间传播、Provider 资源控制和支持取消的协议/业务机制处理。

**Q25：序列化 STRICT 能保证什么，不能保证什么？**

参考答案：它限制未进入 allowlist 的 Java 类型被加载/实例化，并可要求 Serializable；它不认证发送者、不校验字段语义、不自动限制所有对象图资源，也不替代业务授权和 TLS。参见[威胁模型](../threat-model.md)。

## 3. 场景推演题

### 场景 A：Nacos 页面能看到 Provider，但 Consumer 报无地址

推演顺序：

1. 确认看到的是接口级 serviceName 还是应用名实例。
2. 比较 namespace、Naming group、接口 group/version。
3. 应用级模式检查接口到应用映射、实例 revision 和 MetadataInfo。
4. 检查 empty protection 与最后一次 Directory.notify。
5. 比较 Directory 原始地址与 Router 后地址，避免把路由空误判为发现空。

### 场景 B：接口偶尔执行两次，但 Consumer 只记录一次成功

推演顺序：

1. 检查 `retries` 和 Cluster 类型。
2. 用同一 requestId 查多个 Provider 的 receive 日志。
3. 检查首个调用是否 timeout/网络失败但业务已执行。
4. 确认幂等键和去重存储，而不是仅降低日志级别。
5. 评估 timeout countdown、总预算和是否应改用 Failfast。

### 场景 C：升级后自定义 Filter 消失或顺序变化

推演顺序：

1. 比较 SPI 资源文件是否仍被打包和加载。
2. 比较 ScopeModel、group、`@Activate.value` 与 URL key。
3. 检查显式列表是否含 `-default`、`-name` 或 `default` 位置标记。
4. 保存升级前后 Activate 排序列表和实际对象树。
5. 检查 before/after/order 或 Wrapper 顺序变化。

### 场景 D：STRICT 模式拒绝新 DTO

推演顺序：

1. 确认 DTO 是否出现在本地受信任接口参数、返回值、异常、泛型或字段中。
2. 检查 `autoTrustSerializeClass`、`trustSerializeClassLevel` 和 Serializable。
3. 比较多个 Module 是否配置不一致。
4. 只增加最窄必要类/包前缀并添加负向测试。
5. 不把 `DISABLE` 作为长期兼容方案。

## 4. Dubbo Protocol 与 Triple 的边界性对比

本路线只完成 Dubbo Protocol + Hessian2 的端到端实证。下表用于说明哪些知识可复用、哪些必须在 Triple 专题重新验证，不声称已经完成 Triple 全链路学习。

| 维度 | Dubbo Protocol 基线 | Triple 3.3.6 源码边界 | 能否直接复用结论 |
|---|---|---|---|
| Protocol SPI | `dubbo` → `DubboProtocol` | `tri` → [`TripleProtocol`](../../../dubbo-rpc/dubbo-rpc-triple/src/main/java/org/apache/dubbo/rpc/protocol/tri/TripleProtocol.java) | Adaptive/Wrapper 思路可复用，具体实现不可 |
| 默认端口 | 20880 | `TripleProtocol.getDefaultPort()` 返回 50051 | 不可 |
| 传输/报文 | Dubbo 16 字节私有头 + Exchange Request/Response | HTTP/2/HTTP/3 连接、header/path/status 与 stream 抽象 | 不可 |
| Consumer Invoker | `DubboInvoker` + ExchangeClient + DefaultFuture | [`TripleInvoker`](../../../dubbo-rpc/dubbo-rpc-triple/src/main/java/org/apache/dubbo/rpc/protocol/tri/TripleInvoker.java) + ClientCall/ConnectionClient | Cluster 之前可复用，地址级链要重读 |
| 调用形态 | 本路线验证 unary request/response | unary、server/client/bidirectional stream | 流式背压和取消必须新验证 |
| 序列化 | 本路线固定 Hessian2 | 由 method descriptor 与 Triple serialization/protobuf/gRPC 兼容路径决定 | allowlist 结论不能原样套用 |
| Provider 路由 | service key → Exporter | gRPC path mapping，且 3.3.6 代码还注册可选 REST mapping | 服务发现概念可复用，路径解析要重读 |
| 状态/异常 | Dubbo Response status、RpcException 翻译 | `TriRpcStatus`、HTTP/gRPC 状态和 stream error | 必须建立新矩阵 |
| 治理上层 | Directory、Router、LB、Cluster | 仍接入 Dubbo 的 URL、Invoker 与治理模型 | 大部分概念可复用，但 protocol-specific error 会影响重试 |
| 注册/元数据 | Provider URL、应用实例和 MetadataInfo | endpoint/protocol 元数据不同 | 事件链可复用，URL 还原结果要实测 |
| 安全 | Netty TLS 可选、Hessian2 类型边界 | HTTP/2/3 TLS、gRPC metadata、Triple 序列化边界 | 威胁类别可复用，具体控制重新验证 |

Triple 专题的最小新证据应包括：

- unary 与四种 stream 形态的客户端/服务端对象树；
- HTTP/2 headers、path、message frame 和 status/trailer；
- flow control、backpressure、cancel、deadline 和线程模型；
- Java 接口模式与原生 gRPC/Protobuf 兼容模式；
- Triple 序列化类型、异常到 `TriRpcStatus` 的映射；
- Nacos endpoint/MetadataInfo 如何恢复 Triple URL；
- TLS、身份信息传递和跨语言互操作实验。

## 5. 版本升级检查清单

升级不是把文档中的 `3.3.6` 替换成新版本号。为每个目标版本建立单独差异记录，并完成以下检查。

### 5.1 基线与构建

- [ ] 记录旧/新 tag、commit、JDK、Maven、Spring Boot、Nacos SDK 和序列化依赖版本。
- [ ] 在干净工作树构建旧/新版本，保存 Reactor 模块数、耗时、warning 和失败差异。
- [ ] 确认学习示例未被父 POM、插件或打包方式变化破坏。
- [ ] 比较模块新增、删除、重命名和 Maven 依赖方向。

### 5.2 SPI 与对象树

- [ ] 比较核心 SPI 资源文件、默认扩展名和 `@SPI` scope。
- [ ] 比较 Adaptive 生成规则、手写 Adaptive 类和 URL key。
- [ ] 比较 Protocol Wrapper、Activate Filter/Router/Listener 列表与顺序。
- [ ] 运行同一示例，保存 Consumer/Provider Invoker 树和 ScopeModel identity 差异。

### 5.3 暴露、引用与生命周期

- [ ] 比较 ServiceConfig/ReferenceConfig/Deployer 主方法和状态机。
- [ ] 比较 Exporter、Server、Client 的 cache key 与引用计数。
- [ ] 验证 Spring refresh、AOT 注解处理、延迟引用和 close 事件。
- [ ] 验证优雅停机时间线、注册注销顺序和端口关闭。

### 5.4 调用、协议与异常

- [ ] 重新采集一条完整 Request/Response 调用栈和线程切换。
- [ ] 比较 Dubbo header/body、serialization id、requestId/Future 管理。
- [ ] 复跑双 Provider、Provider 下线、业务异常和真实 timeout 场景。
- [ ] 比较 `RpcException` code、异常翻译、默认 retries/timeout 和 countdown。
- [ ] 检查 Filter/Cluster 在 sync、async、oneway、generic 调用中的位置差异。

### 5.5 Nacos 与治理

- [ ] 比较接口级 serviceName、Instance metadata 和应用 ServiceInstance 字段。
- [ ] 比较 register-mode、migration step、threshold、promotion 和默认行为。
- [ ] 比较接口到应用映射、MetadataInfo revision、local/remote 获取路径。
- [ ] 真实 Nacos 复验注册、订阅、扩缩容、空推送、断线恢复和规则变更。
- [ ] 比较 Client 创建/复用、SDK 重连、Dubbo retry/Failback 的责任边界。

### 5.6 安全

- [ ] 阅读新版本 `SECURITY.md`、安全公告和依赖 CVE，不只看代码 diff。
- [ ] 比较 allowlist/blockedlist、默认检查级别、自动信任和 Serializable 行为。
- [ ] 复跑不可信类型、未知类、非 Serializable 和过大输入负向用例。
- [ ] 验证 TLS、auth/token、Nacos ACL 和日志脱敏配置仍有效。
- [ ] 更新威胁模型时保留旧版本边界，明确新增保证、移除保证和下游责任变化。

### 5.7 文档验收

- [ ] 每个差异都标明源码、测试、运行或待复验等级。
- [ ] 新版本结论写入独立差异文档，不直接覆盖 3.3.6 主线。
- [ ] 更新类/方法链接、断点、日志标记和实验命令。
- [ ] 运行 Markdown 链接检查、`git diff --check` 和示例构建。

## 6. 后续专题学习清单

| 优先级 | 专题 | 可复用基础 | 最小交付物 |
|---:|---|---|---|
| 1 | Triple 与 gRPC 兼容 | 阶段 1、2、3、5、8 | unary/stream 对象树、帧与状态、跨语言实验 |
| 2 | 异步、泛化与流式调用 | Proxy/Invoker/Future 主线 | sync/async/future/oneway/generic/stream 矩阵 |
| 3 | 多注册中心与跨机房治理 | 阶段 7、8 | 多 Registry Directory、路由/容灾事件链 |
| 4 | Metrics、Tracing、Observability | Filter/线程/requestId | 指标来源、trace context、故障关联手册 |
| 5 | 性能、对象复用与连接模型 | Client/Server/Invoker 生命周期 | JFR、分配热点、连接/线程/队列基准 |
| 6 | Native Image 与 AOT | ScopeModel、Spring AOT、SPI | 反射描述、Adaptive、序列化与启动差异 |
| 7 | 公司实际版本差异 | 本文第 5 节 | patch/插件/配置/运行对象差异清单 |

专题仍采用同一证据原则：一个稳定成功基线、一个单变量变化实验、一个失败/恢复实验，再写源码结论和下游责任。

## 7. 间隔复习建议

| 时间 | 任务 |
|---|---|
| 当天 | 口述十分钟主线，完成 Q1—Q10 |
| 3 天后 | 不看答案完成 Q11—Q25，画一次同步 RPC 图 |
| 1 周后 | 随机选择一个故障，用断点手册写出证据采集计划 |
| 2 周后 | 从源码索引随机抽 10 个类，说明上游、下游和作用域 |
| 1 月后 | 选择 Triple 或实际版本，执行一项最小差异实验 |

复习的目标是恢复“对象关系和验证方法”，而不是背诵行号。类名或方法变化时，稳定概念仍能引导到新实现。
