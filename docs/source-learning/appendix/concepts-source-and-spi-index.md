# 概念、源码与 SPI 索引

> 总入口：[Dubbo 3.3.6 源码知识地图](../notes/09-knowledge-consolidation.md)
> 使用方法：先按概念确认对象边界，再按类和方法进入源码，最后返回对应阶段理解上下文。

## 1. 核心概念词典

| 概念 | 本路线中的定义 | 最容易混淆的对象 | 首读专题 |
|---|---|---|---|
| URL | 协议、地址、服务 key、参数、属性和 ScopeModel 的装配载体 | 普通日志字符串 | [全局架构](../notes/01-architecture-overview.md#7-url-为什么贯穿框架) |
| Invocation | 一次方法调用的接口、方法、参数、附件和调用状态 | 网络 Request | [RPC 全景](../notes/02-rpc-call-overview.md) |
| Result | 调用返回值或异常的统一容器，可异步完成 | Java 方法返回值本身 | [网络与响应](../notes/04-protocol-remoting-provider.md) |
| Proxy | 把 Java 接口方法转成 Invocation，或把 Provider Invoker 暴露为业务调用 | Invoker | [Consumer 入口](../notes/03-consumer-invocation.md) |
| Invoker | 可执行一次 Invocation 的统一抽象；代理、集群、Filter、协议地址都可表现为 Invoker | Provider 实例地址 | [对象包装树](../notes/03-consumer-invocation.md#3-实际-invoker-包装树) |
| Exporter | 保存 Provider Invoker 并支持 unexport 的服务级句柄 | Server | [暴露与缓存](../notes/05-service-export-and-reference.md) |
| Protocol | 从 Invoker export 出 Exporter，或从 URL refer 出 Invoker | 网络协议报文字节 | [Protocol 与网络](../notes/04-protocol-remoting-provider.md) |
| Directory | 保存并按 Invocation 返回当前可用 Invoker 列表 | Registry Client | [筛选漏斗](../notes/03-consumer-invocation.md#5-directoryrouterloadbalance-的筛选漏斗) |
| Registry | 注册/订阅接口 URL 的抽象，负责把外部事件交给 NotifyListener | Directory | [Nacos 接口发现](../notes/07-nacos-and-service-discovery.md#4-接口级服务发现) |
| ServiceDiscovery | 按应用实例进行注册与发现 | 接口级 Registry | [应用级服务发现](../notes/07-nacos-and-service-discovery.md#5-应用级服务发现) |
| MetadataInfo | 用 revision 标识的应用导出接口与协议元数据 | Nacos Instance metadata | [应用实例还原](../notes/07-nacos-and-service-discovery.md#52-从接口到应用再回到接口) |
| Router | 按 Invocation 和规则缩减候选 Invoker | LoadBalance | [治理分工](../notes/08-spi-governance-failure-security.md#6-filterrouterloadbalancecluster-的分工) |
| LoadBalance | 在当前候选中为一次尝试选择一个 Invoker | Cluster | [Consumer 选择](../notes/03-consumer-invocation.md#5-directoryrouterloadbalance-的筛选漏斗) |
| Cluster | 把 Directory 包装成具备 Failover/Failfast 等语义的 Invoker | Filter 链 | [Failover](../notes/08-spi-governance-failure-security.md#7-failover重选与超时预算) |
| Filter | 围绕 Invoker 调用执行上下文、观测、限流、认证、异常处理等横切逻辑 | Router | [Activate 与 Filter](../notes/08-spi-governance-failure-security.md#5-activate一组扩展如何启用并排序) |
| Wrapper | 用 SPI 接口单参数构造器透明包装具名扩展 | FilterChain 节点 | [Protocol Wrapper](../notes/08-spi-governance-failure-security.md#4-wrapper为什么-protocol-能透明插入调用链) |
| Adaptive Extension | 运行时从 URL/Invocation 解析扩展名并转发的 SPI 分发器 | 默认扩展实例 | [Adaptive](../notes/08-spi-governance-failure-security.md#3-adaptive由-url-把调用送到具名实现) |
| Activate | 按 group、URL 条件、显式列表和顺序组装一组扩展 | Adaptive 单选 | [Activate](../notes/08-spi-governance-failure-security.md#5-activate一组扩展如何启用并排序) |
| ScopeModel | Framework/Application/Module 的资源、扩展和 ClassLoader 作用域 | Spring ApplicationContext | [作用域](../notes/01-architecture-overview.md#8-scopemodel-如何隔离运行时资源) |
| ExchangeClient | 在 Transport 之上提供 Request/Response 语义的客户端 | Netty Channel | [网络分层](../notes/04-protocol-remoting-provider.md#6-codectransportexchangenetty-的职责) |
| DefaultFuture | 按 requestId 保存等待状态并在 Response 到达时完成 | Java 业务 Future 接口 | [Future](../notes/04-protocol-remoting-provider.md#3-defaultfuture为什么一定要先注册再发送) |
| Request/Response | Dubbo Exchange 层消息，带 requestId、状态和数据 | Invocation/Result | [报文](../notes/04-protocol-remoting-provider.md#4-16-字节-dubbo-报文头) |
| Client/Server | 地址级网络连接与监听资源，可被多个服务 Invoker/Exporter 复用 | Invoker/Exporter | [资源复用](../notes/05-service-export-and-reference.md#4-exporter-与-server-为什么按不同-key-缓存) |

## 2. 模块入口索引

| 想研究的问题 | 首选模块 | 代表包或类 |
|---|---|---|
| URL、SPI、配置环境、安全类检查 | `dubbo-common` | `org.apache.dubbo.common`、`extension`、`utils` |
| Service/Reference 配置与部署器 | `dubbo-config-api` | `ServiceConfig`、`ReferenceConfig`、`deploy` |
| Spring Boot 3 注解和 Bean 生命周期 | `dubbo-config-spring6`、starter | annotation processors、config binding、listeners |
| Proxy、Protocol、Filter 和异常 | `dubbo-rpc-api` | `rpc`、`proxy`、`protocol`、`filter` |
| Dubbo Protocol 与 Codec | `dubbo-rpc-dubbo` | `DubboInvoker`、`DubboProtocol`、`DubboCodec` |
| Directory、Router、LB、Cluster | `dubbo-cluster` | `directory`、`router`、`loadbalance`、`support` |
| Request/Response、Future、Netty | `dubbo-remoting-*` | `exchange`、`transport.netty4` |
| 接口级 Registry 与应用级发现 | `dubbo-registry-*` | `RegistryDirectory`、`ServiceDiscoveryRegistryDirectory` |
| Nacos Naming | `dubbo-registry-nacos` | `NacosRegistry`、`NacosServiceDiscovery` |
| Nacos Config/Metadata | `dubbo-configcenter-nacos`、`dubbo-metadata-report-nacos` | DynamicConfiguration、MetadataReport |
| Hessian2 | `dubbo-serialization-hessian2` | Hessian2 object input/output、class checker |

## 3. 核心类与方法索引

### 3.1 配置、模型和生命周期

| 类 | 关键方法 | 作用 | 对应阶段 |
|---|---|---|---|
| [`URL`](../../../dubbo-common/src/main/java/org/apache/dubbo/common/URL.java) | `getParameter`、`getMethodParameter`、`getScopeModel` | 承载协议选择、方法级配置和作用域 | 1、5、8 |
| [`ScopeModel`](../../../dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ScopeModel.java) | `getExtensionLoader`、`destroy` | 约束扩展、Bean、ClassLoader 生命周期 | 1、5、6 |
| [`DefaultApplicationDeployer`](../../../dubbo-config/dubbo-config-api/src/main/java/org/apache/dubbo/config/deploy/DefaultApplicationDeployer.java) | `initialize`、`startConfigCenter`、`startMetadataCenter`、`start` | 应用级初始化、三中心准备与启动 | 6、7 |
| [`DefaultModuleDeployer`](../../../dubbo-config/dubbo-config-api/src/main/java/org/apache/dubbo/config/deploy/DefaultModuleDeployer.java) | `start`、`exportServices`、`referServices`、`registerServiceInstance` | 模块级服务暴露、引用和应用实例注册 | 5、6、7 |
| [`ServiceConfig`](../../../dubbo-config/dubbo-config-api/src/main/java/org/apache/dubbo/config/ServiceConfig.java) | `export`、`doExport`、`doExportUrls`、`doExportUrl`、`unexport` | 生成 Provider URL，创建 Invoker/Exporter 并注册 | 5 |
| [`ReferenceConfig`](../../../dubbo-config/dubbo-config-api/src/main/java/org/apache/dubbo/config/ReferenceConfig.java) | `get`、`init`、`createProxy`、`destroy` | 生成 Consumer URL，创建 Directory/Invoker/Proxy | 5 |
| [`DubboDeployApplicationListener`](../../../dubbo-config/dubbo-config-spring/src/main/java/org/apache/dubbo/config/spring/context/DubboDeployApplicationListener.java) | `onApplicationEvent` | 把 Spring refresh/close 事件交给 Dubbo Deployer | 6 |
| [`ServiceAnnotationWithAotPostProcessor`](../../../dubbo-config/dubbo-config-spring6/src/main/java/org/apache/dubbo/config/spring6/beans/factory/annotation/ServiceAnnotationWithAotPostProcessor.java) | 扫描与注册方法 | 把 `@DubboService` 类注册成 ServiceBean | 6 |
| [`ReferenceAnnotationWithAotBeanPostProcessor`](../../../dubbo-config/dubbo-config-spring6/src/main/java/org/apache/dubbo/config/spring6/beans/factory/annotation/ReferenceAnnotationWithAotBeanPostProcessor.java) | 引用 Bean 注册与注入 | 把 `@DubboReference` 注入业务 Bean | 6 |

### 3.2 Proxy、Cluster 与协议调用

| 类 | 关键方法 | 作用 | 对应阶段 |
|---|---|---|---|
| [`JavassistProxyFactory`](../../../dubbo-rpc/dubbo-rpc-api/src/main/java/org/apache/dubbo/rpc/proxy/javassist/JavassistProxyFactory.java) | `getProxy`、`getInvoker` | Consumer 生成接口 Proxy；Provider 生成 ProxyInvoker | 2、5 |
| [`AbstractProxyInvoker`](../../../dubbo-rpc/dubbo-rpc-api/src/main/java/org/apache/dubbo/rpc/proxy/AbstractProxyInvoker.java) | `invoke`、`doInvoke` | Provider 把 Invocation 转成 Java 方法调用 | 2、4 |
| [`AbstractClusterInvoker`](../../../dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/support/AbstractClusterInvoker.java) | `invoke`、`list`、`select`、`checkInvokers` | 先路由，再初始化 LB，进入具体 Cluster 策略 | 3、8 |
| [`FailoverClusterInvoker`](../../../dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/support/FailoverClusterInvoker.java) | `doInvoke`、`calculateInvokeTimes` | 重新 list/reselect，区分 Biz 和非 Biz 失败 | 3、8 |
| [`AdaptiveLoadBalance`](../../../dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/loadbalance/AdaptiveLoadBalance.java) | `select` | 按方法级 `loadbalance` 选择具名实现 | 3、8 |
| [`ProtocolFilterWrapper`](../../../dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/filter/ProtocolFilterWrapper.java) | `export`、`refer` | 在具体协议边界组装 Provider/Consumer Filter 链 | 3、8 |
| [`ProtocolListenerWrapper`](../../../dubbo-rpc/dubbo-rpc-api/src/main/java/org/apache/dubbo/rpc/protocol/ProtocolListenerWrapper.java) | `export`、`refer` | 插入 Exporter/Invoker Listener | 5、8 |
| [`DubboInvoker`](../../../dubbo-rpc/dubbo-rpc-dubbo/src/main/java/org/apache/dubbo/rpc/protocol/dubbo/DubboInvoker.java) | `doInvoke`、`isAvailable` | 计算 timeout，创建 Request，调用 ExchangeClient，翻译网络异常 | 4、8 |
| [`DubboProtocol`](../../../dubbo-rpc/dubbo-rpc-dubbo/src/main/java/org/apache/dubbo/rpc/protocol/dubbo/DubboProtocol.java) | `export`、`protocolBindingRefer`、`getSharedClient`、`openServer` | 管理 Dubbo Exporter、Invoker、Client、Server | 4、5 |

### 3.3 Directory、Router 与服务发现

| 类 | 关键方法 | 作用 | 对应阶段 |
|---|---|---|---|
| [`AbstractDirectory`](../../../dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/directory/AbstractDirectory.java) | `list`、`doList`、`destroy` | 统一执行 RouterChain 并返回候选 Invoker | 3 |
| [`RegistryDirectory`](../../../dubbo-registry/dubbo-registry-api/src/main/java/org/apache/dubbo/registry/integration/RegistryDirectory.java) | `notify`、`refreshInvoker`、`toInvokers` | 接口级 URL 刷新，复用/新建/销毁地址级 Invoker | 5、7 |
| [`ServiceDiscoveryRegistryDirectory`](../../../dubbo-registry/dubbo-registry-api/src/main/java/org/apache/dubbo/registry/client/ServiceDiscoveryRegistryDirectory.java) | `notify`、地址刷新方法 | 应用实例和 MetadataInfo 还原后的 URL 刷新 | 7 |
| [`RouterChain`](../../../dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/RouterChain.java) | `buildChain`、`setInvokers`、`getSingleChain` | 装配 Activate Router，主备链一致性切换 | 8 |
| [`SingleRouterChain`](../../../dubbo-cluster/src/main/java/org/apache/dubbo/rpc/cluster/SingleRouterChain.java) | `route`、`simpleRoute`、`setInvokers` | 先 StateRouter，后普通 Router | 3、8 |
| [`NacosRegistry`](../../../dubbo-registry/dubbo-registry-nacos/src/main/java/org/apache/dubbo/registry/nacos/NacosRegistry.java) | register/subscribe、`buildURLs` | 接口级 URL 与 Nacos Instance 双向转换 | 7 |
| [`NacosServiceDiscovery`](../../../dubbo-registry/dubbo-registry-nacos/src/main/java/org/apache/dubbo/registry/nacos/NacosServiceDiscovery.java) | register/update/listener | 应用 ServiceInstance 注册和事件转换 | 7 |
| [`ServiceInstancesChangedListener`](../../../dubbo-registry/dubbo-registry-api/src/main/java/org/apache/dubbo/registry/client/event/listener/ServiceInstancesChangedListener.java) | `doOnEvent`、metadata 解析方法 | 按 revision 聚合实例，恢复接口 URL，处理重试 | 7 |
| [`MigrationInvoker`](../../../dubbo-registry/dubbo-registry-api/src/main/java/org/apache/dubbo/registry/client/migration/MigrationInvoker.java) | `migrateTo*`、`decideInvoker` | 在接口级与应用级 ClusterInvoker 之间选择和回退 | 7 |

### 3.4 网络、报文与响应

| 类 | 关键方法 | 作用 | 对应阶段 |
|---|---|---|---|
| [`DubboCodec`](../../../dubbo-rpc/dubbo-rpc-dubbo/src/main/java/org/apache/dubbo/rpc/protocol/dubbo/DubboCodec.java) | `encodeRequestData`、`decodeBody` | Invocation/Result 与 Dubbo body 的转换 | 4 |
| [`DefaultFuture`](../../../dubbo-remoting/dubbo-remoting-api/src/main/java/org/apache/dubbo/remoting/exchange/support/DefaultFuture.java) | `newFuture`、`sent`、`received`、`doReceived` | 按 requestId 注册、超时和完成响应 | 4 |
| [`HeaderExchangeHandler`](../../../dubbo-remoting/dubbo-remoting-api/src/main/java/org/apache/dubbo/remoting/exchange/support/header/HeaderExchangeHandler.java) | `handleRequest`、`received` | Provider 接收 Request，调用 ExchangeHandler 并创建 Response | 4 |

Netty Client/Server、Encoder/Decoder 和 Dispatcher 的具体类随传输扩展变化，统一从[阶段 4 网络分层](../notes/04-protocol-remoting-provider.md#6-codectransportexchangenetty-的职责)进入，避免把 Netty 实现当成所有协议的固定入口。

### 3.5 SPI 与安全

| 类 | 关键方法 | 作用 | 对应阶段 |
|---|---|---|---|
| [`ExtensionLoader`](../../../dubbo-common/src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java) | `getExtension`、`createExtension`、`getAdaptiveExtension`、`getActivateExtension` | 发现、创建、包装、选择和缓存扩展 | 8 |
| [`DefaultSerializeClassChecker`](../../../dubbo-common/src/main/java/org/apache/dubbo/common/utils/DefaultSerializeClassChecker.java) | `loadClass`、`loadClass0` | 执行 allow/blocked/STRICT 与 Serializable 检查 | 8 |
| [`SerializeSecurityConfigurator`](../../../dubbo-common/src/main/java/org/apache/dubbo/common/utils/SerializeSecurityConfigurator.java) | `refreshStatus`、`refreshCheck`、`registerInterface` | 加载安全配置并从本地 API 自动信任类型 | 8 |

安全结论必须同时对照[学习范围威胁模型](../threat-model.md)，不能把类型 allowlist 外推为认证、授权或传输安全。

## 4. SPI 扩展索引

| SPI | 默认或 Adaptive 键 | 选择形态 | 典型实现/Wrapper | 运行位置 |
|---|---|---|---|---|
| `Protocol` | 默认 `dubbo`；键为 URL protocol | Adaptive 单选 + Wrapper | `DubboProtocol`、`RegistryProtocol`、Filter/Listener Wrapper | export/refer |
| `ProxyFactory` | `proxy` | Adaptive 单选 | `JavassistProxyFactory` | 创建 Consumer Proxy / Provider Invoker |
| `Cluster` | 默认 `failover`；键 `cluster` | Adaptive 单选 + 可有 Wrapper | Failover、Failfast、Failsafe 等 | Directory → ClusterInvoker |
| `LoadBalance` | 默认 `random`；键 `loadbalance` | 每次尝试 Adaptive 单选 | Random、RoundRobin、LeastActive 等 | Cluster 选地址 |
| `RouterFactory` | 键 `router` | Activate 列表 | Mock、Script 等普通 Router | RouterChain |
| `StateRouterFactory` | 键 `router` | Activate 列表 | Tag、Condition 等 StateRouter | RouterChain |
| `Filter` | `service.filter` / `reference.filter` | Activate 列表 | Context、Exception、Token、自定义 Filter | 地址级 Invoker 链 |
| `ClusterFilter` | `service.filter` / `reference.filter` | Activate 列表 | Metrics、Monitor、RouterSnapshot 等 | ClusterInvoker 外层 |
| `ExporterListener` | `exporter.listener` | Activate 列表 | injvm/default/custom | Exporter 生命周期 |
| `InvokerListener` | `invoker.listener` | Activate 列表 | custom listeners | 地址级 Invoker 生命周期 |
| `Serialization` | `serialization` | Adaptive 单选 | Hessian2、Fastjson2 等 | Codec body |
| `Transporter` | `server` / `client` 等键 | Adaptive 单选 | Netty4 等 | bind/connect |
| `Dispatcher` | `dispatcher` | Adaptive 单选 | all、direct、message 等 | ChannelHandler 线程派发 |
| `RegistryFactory` | registry protocol | Adaptive 单选 | Nacos、Zookeeper 等 | 创建接口级 Registry |
| `ServiceDiscoveryFactory` | registry protocol | Adaptive 单选 | Nacos 等 | 创建应用级 ServiceDiscovery |
| `DynamicConfigurationFactory` | config protocol | Adaptive 单选 | Nacos 等 | 配置和治理监听 |
| `MetadataReportFactory` | metadata protocol | Adaptive 单选 | Nacos 等 | 元数据读写 |

判断一个 SPI 是“选一个”还是“取一组”的方法：

- 方法标 `@Adaptive` 并通过 URL 键取名，通常是单选并转发。
- 调用方使用 `getActivateExtension`，通常按 group/value/order 取一组。
- 实现类拥有 SPI 接口单参数构造器，则作为 Wrapper 透明包裹具名实例。

## 5. 对象身份检查表

分析“是否复用”“是否泄漏”时，至少记录：

| 对象 | 建议 key | 必要上下文 |
|---|---|---|
| Extension | SPI 类型 + 名称 + ScopeModel | raw/named/adaptive/wrapped class、ClassLoader |
| Proxy | interface + ReferenceConfig | InvocationHandler、下游 Invoker identity |
| ClusterInvoker | service key + Directory | cluster 类型、Directory identity |
| 地址级 Invoker | 完整有效 URL | target address、available、Client identity |
| Client | address + sharing 配置 | ExchangeClient/ReferenceCountExchangeClient、channel |
| Exporter | protocol service key | Provider Invoker、Server identity |
| Server | bind address + protocol | 监听端口、Exporter 数量 |
| Nacos Client | 逻辑角色 + namespace + server address | Naming/Config、listener/registration 状态 |
| MetadataInfo | application + revision | storage type、接口/协议条目 |

只比较 class name 或地址字符串不足以证明对象复用；必须保存 identity 和有效配置。

## 6. 反向检索示例

### URL 中出现 `loadbalance=roundrobin`

```text
URL 方法参数
→ AdaptiveLoadBalance.select
→ ExtensionLoader.getExtension("roundrobin")
→ RoundRobinLoadBalance.select
→ 当前候选中的一个 Invoker
```

### Nacos 收到应用实例事件

```text
NacosServiceDiscovery listener
→ ServiceInstancesChangedEvent
→ ServiceInstancesChangedListener 按 revision 聚合
→ MetadataInfo 还原接口 URL
→ ServiceDiscoveryRegistryDirectory refreshInvoker
→ MigrationInvoker decideInvoker
```

### Consumer 抛 `RpcException code=2`

```text
DefaultFuture timeout
→ DubboInvoker catch TimeoutException
→ RpcException.TIMEOUT_EXCEPTION
→ Failover 根据 retries 决定是否再试
→ Proxy/Result 向业务线程抛出
```

这三个示例分别展示了配置参数、控制面事件和异常码如何进入源码索引。
