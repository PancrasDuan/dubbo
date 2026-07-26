# 阶段 1 证据：模块依赖和调用栈映射

> 对应主笔记：[Dubbo 全局架构与模块地图](../01-architecture-overview.md)
> 适用版本：Apache Dubbo 3.3.6，commit `b7868dbb56181bf04e3b0b6c11a77b25ffd2a951`

本文件保存阶段 1 的可核对事实：聚合关系、直接 compile 依赖、代表类路径和阶段 0 调用栈的模块归属。主笔记只保留提炼后的架构结论。

## 1. 聚合关系

### 1.1 根 POM

- 稳定学习分支原有 54 个根 `<module>` 条目。
- 加入阶段 0 的 `dubbo-demo/dubbo-demo-source-learning` 后为 55 个。
- 根条目同时包含一级聚合器、叶子 JAR、插件和 demo，不能按出现顺序推断依赖方向。

重点一级条目：

```text
dubbo-common
dubbo-remoting
dubbo-rpc
dubbo-cluster
dubbo-registry
dubbo-configcenter
dubbo-config
dubbo-serialization
dubbo-metadata
dubbo-spring-boot-project/*
```

### 1.2 重点聚合 POM

| 聚合器 | packaging | 固定子模块 | profile 条件子模块 |
|---|---|---|---|
| `dubbo-config` | `pom` | `dubbo-config-api`、`dubbo-config-spring` | `dubbo-config-spring6` |
| `dubbo-rpc` | `pom` | `dubbo-rpc-api`、`dubbo-rpc-dubbo`、`dubbo-rpc-injvm`、`dubbo-rpc-triple` | 无 |
| `dubbo-remoting` | `pom` | `dubbo-remoting-api`、`dubbo-remoting-netty`、`dubbo-remoting-zookeeper-curator5`、`dubbo-remoting-netty4`、`dubbo-remoting-http12`、`dubbo-remoting-http3`、`dubbo-remoting-websocket` | 无 |
| `dubbo-serialization` | `pom` | `dubbo-serialization-api`、`dubbo-serialization-hessian2`、`dubbo-serialization-fastjson2` | 无 |
| `dubbo-registry` | `pom` | `dubbo-registry-api`、`dubbo-registry-multicast`、`dubbo-registry-zookeeper`、`dubbo-registry-nacos`、`dubbo-registry-multiple` | 无 |
| `dubbo-configcenter` | `pom` | `dubbo-configcenter-zookeeper`、`dubbo-configcenter-apollo`、`dubbo-configcenter-nacos`、`dubbo-configcenter-file` | 无 |
| `dubbo-metadata` | `pom` | `dubbo-metadata-api`、`dubbo-metadata-definition-protobuf`、`dubbo-metadata-processor`、`dubbo-metadata-report-zookeeper`、`dubbo-metadata-report-nacos` | 无 |

`dubbo-common` 和 `dubbo-cluster` 自己就是 JAR，不是同名子模块的聚合器。`dubbo-spring-boot-project` 目录没有统一聚合 POM，它的 autoconfigure、starter、actuator、compatible 等模块由根 POM 分别引入。

## 2. 直接 POM 依赖证据

以下只列 `org.apache.dubbo` 组内的直接依赖。`compile` 是主代码依赖，`test` 只用于模块自身测试，`optional` 不会无条件传递给使用方。

| 模块 | 直接组内依赖 |
|---|---|
| `dubbo-serialization-api` | `dubbo-common`（compile） |
| `dubbo-serialization-hessian2` | `dubbo-serialization-api`、`hessian-lite`、`dubbo-native`（compile） |
| `dubbo-remoting-api` | `dubbo-common`、`dubbo-serialization-api`（compile）；Hessian2/Fastjson2（test） |
| `dubbo-remoting-netty4` | `dubbo-remoting-api`、metrics、native（compile）；Hessian2/Fastjson2（test） |
| `dubbo-rpc-api` | `dubbo-common`、`dubbo-serialization-api`、`dubbo-remoting-api`、native（compile） |
| `dubbo-cluster` | `dubbo-rpc-api`、metrics-registry（compile），metrics-default（compile optional）；协议/序列化实现（test） |
| `dubbo-rpc-dubbo` | `dubbo-rpc-api`、`dubbo-remoting-api`、`dubbo-cluster`（compile）；Netty4/Hessian2/Fastjson2（test） |
| `dubbo-metadata-api` | `dubbo-cluster`、metrics、`dubbo-rpc-triple`（compile），`dubbo-rpc-api`（compile optional） |
| `dubbo-registry-api` | `dubbo-common`、`dubbo-cluster`、`dubbo-metadata-api`、metrics、native（compile） |
| `dubbo-registry-nacos` | `dubbo-registry-api`、`dubbo-common`（compile）；协议/网络/序列化实现（test） |
| `dubbo-config-api` | `dubbo-registry-api`、`dubbo-metadata-api`、metrics、tracing、`dubbo-remoting-api`、`dubbo-rpc-api`、`dubbo-rpc-injvm`（compile）；具体协议、网络、注册和配置中心实现多为 test |
| `dubbo-config-spring` | `dubbo-config-api`（compile）；具体协议、网络、序列化、注册中心实现多为 test |
| `dubbo-config-spring6` | `dubbo-config-api`、`dubbo-config-spring`（compile） |
| `dubbo-configcenter-nacos` | `dubbo-common`、metrics（compile） |
| `dubbo-metadata-report-nacos` | `dubbo-metadata-api`、`dubbo-configcenter-nacos`（compile） |
| `dubbo-spring-boot-autoconfigure` | compatible autoconfigure 与聚合制品 `dubbo`（compile）；common、Triple servlet/websocket、QoS、config-spring 为 compile optional |

### 2.1 依赖事实带来的实施结论

- 依赖 `dubbo-rpc-dubbo` 不会通过 compile 传递自动带入 Netty4 和 Hessian2。
- 阶段 0 显式依赖 `dubbo-remoting-netty4` 和 `dubbo-serialization-hessian2`，否则 SPI 可能没有目标实现。
- `dubbo-config-api` 是高层组合入口，依赖面较宽；不能把它当作无依赖的纯 POJO 配置模块。
- Registry API 依赖 Metadata API，Metadata API 又依赖 Cluster；治理模块之间存在实际代码协作，不是三个完全平行的目录。

## 3. 重点模块与代表类索引

### 3.1 `dubbo-common`

| 类 | 路径 | 导航用途 |
|---|---|---|
| `URL` | `dubbo-common/src/main/java/org/apache/dubbo/common/URL.java` | 地址、参数、attributes、ScopeModel 关联 |
| `ExtensionLoader` | `dubbo-common/src/main/java/org/apache/dubbo/common/extension/ExtensionLoader.java` | SPI 实例加载、Adaptive、Activate、Wrapper |
| `ScopeModel` | `dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ScopeModel.java` | 作用域公共资源和销毁 |
| `FrameworkModel` | `dubbo-common/src/main/java/org/apache/dubbo/rpc/model/FrameworkModel.java` | 框架级模型与全局资源 |
| `ApplicationModel` | `dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ApplicationModel.java` | 应用级配置、服务仓库和部署 |
| `ModuleModel` | `dubbo-common/src/main/java/org/apache/dubbo/rpc/model/ModuleModel.java` | 模块级服务、配置和 Spring 容器绑定 |

### 3.2 `dubbo-config`

| 类 | 所属子模块 | 导航用途 |
|---|---|---|
| `DubboBootstrap` | `dubbo-config-api` | 应用启动、等待和销毁门面 |
| `ServiceConfig` | `dubbo-config-api` | Provider 服务暴露配置入口 |
| `ReferenceConfig` | `dubbo-config-api` | Consumer 服务引用配置入口 |
| `DefaultApplicationDeployer` | `dubbo-config-api` | ApplicationModel 初始化/启动/停止 |
| `DefaultModuleDeployer` | `dubbo-config-api` | ModuleModel 的服务暴露与引用启动 |
| `ServiceBean` | `dubbo-config-spring` | Spring Bean 到 ServiceConfig |
| `ReferenceBean` | `dubbo-config-spring` | Spring FactoryBean 到 ReferenceConfig/代理 |
| `ReferenceAnnotationBeanPostProcessor` | `dubbo-config-spring` | `@DubboReference` 注入入口 |

### 3.3 `dubbo-rpc`

| 类 | 所属子模块 | 导航用途 |
|---|---|---|
| `Invoker` | `dubbo-rpc-api` | 统一调用语义 |
| `Invocation` / `RpcInvocation` | `dubbo-rpc-api` | 调用描述及实现 |
| `Result` / `AsyncRpcResult` / `AppResponse` | `dubbo-rpc-api` | 返回值、异常和完成状态 |
| `Protocol` | `dubbo-rpc-api` | export/refer SPI |
| `Exporter` | `dubbo-rpc-api` | 暴露生命周期 |
| `ProxyFactory` | `dubbo-rpc-api` | Proxy 与 Invoker 双向转换 |
| `DubboProtocol` | `dubbo-rpc-dubbo` | Dubbo Protocol 的客户端/服务器实现入口 |
| `DubboCodec` | `dubbo-rpc-dubbo` | Dubbo 请求响应编解码 |

### 3.4 `dubbo-cluster`

| 类 | 导航用途 |
|---|---|
| `Directory` | 按 Invocation 提供候选 Invoker |
| `Cluster` | 把 Directory 包装成虚拟 Invoker |
| `Router` / `RouterChain` | 路由规则和候选过滤 |
| `LoadBalance` | 单次 Provider 选择 SPI |
| `AbstractClusterInvoker` | Cluster Invoker 模板流程 |
| `FailoverClusterInvoker` | failover 选择与重试 |
| `FilterChainBuilder` | Consumer/Provider Filter 链组装 |

### 3.5 `dubbo-remoting`

| 类 | 所属子模块 | 导航用途 |
|---|---|---|
| `Channel` | `dubbo-remoting-api` | 连接端点和 channel attributes |
| `Client` / `RemotingServer` | `dubbo-remoting-api` | 传输客户端和服务器抽象 |
| `ExchangeClient` / `ExchangeServer` | `dubbo-remoting-api` | request-response 交换语义 |
| `HeaderExchangeHandler` | `dubbo-remoting-api` | 请求、响应、心跳分派 |
| `DecodeHandler` | `dubbo-remoting-api` | 解码处理入口 |
| `ChannelEventRunnable` | `dubbo-remoting-api` | I/O 事件向业务线程派发 |
| `NettyClient` / `NettyServer` | `dubbo-remoting-netty4` | Netty4 连接和端口监听实现 |

阶段方案中的通用“Server”概念在 3.3.6 核心 API 中对应 `RemotingServer`；`Server` 名称仍可在兼容层看到，阅读主线应以 `org.apache.dubbo.remoting.RemotingServer` 为准。

### 3.6 `dubbo-serialization`

| 类 | 所属子模块 | 导航用途 |
|---|---|---|
| `Serialization` | `dubbo-serialization-api` | 序列化实现 SPI 与 content type/id |
| `ObjectInput` | `dubbo-serialization-api` | 参数和对象读取抽象 |
| `ObjectOutput` | `dubbo-serialization-api` | 参数和对象写出抽象 |
| `Hessian2Serialization` | `dubbo-serialization-hessian2` | Hessian2 SPI 实现 |
| `Hessian2ObjectInput` | `dubbo-serialization-hessian2` | Hessian2 读取 |
| `Hessian2ObjectOutput` | `dubbo-serialization-hessian2` | Hessian2 写出 |

### 3.7 `dubbo-registry`

| 类 | 所属子模块 | 导航用途 |
|---|---|---|
| `Registry` / `RegistryService` | `dubbo-registry-api` | 接口级注册、订阅和查询抽象 |
| `RegistryProtocol` | `dubbo-registry-api` | Registry 与底层 Protocol 的桥接 |
| `RegistryDirectory` | `dubbo-registry-api` | 接口级地址变更到 Invoker 列表 |
| `ServiceDiscovery` | `dubbo-registry-api` | 应用级服务发现抽象 |
| `ServiceDiscoveryRegistry` | `dubbo-registry-api` | ServiceDiscovery 到 Registry 语义的适配 |
| `NacosServiceDiscovery` | `dubbo-registry-nacos` | Nacos 应用级发现实现 |

### 3.8 `dubbo-configcenter`

| 类 | 所属位置 | 导航用途 |
|---|---|---|
| `DynamicConfiguration` | `dubbo-common` | 动态配置读取和监听 SPI |
| `AbstractDynamicConfiguration` | `dubbo-common` | 实现公共逻辑 |
| `NacosDynamicConfiguration` | `dubbo-configcenter-nacos` | Nacos 配置中心实现 |
| `ZookeeperDynamicConfiguration` | `dubbo-configcenter-zookeeper` | ZooKeeper 配置中心实现 |
| `ApolloDynamicConfiguration` | `dubbo-configcenter-apollo` | Apollo 配置中心实现 |
| `FileSystemDynamicConfiguration` | `dubbo-configcenter-file` | 文件配置实现 |

### 3.9 `dubbo-metadata`

| 类 | 所属子模块 | 导航用途 |
|---|---|---|
| `MetadataInfo` | `dubbo-metadata-api` | 应用导出服务元数据及 revision |
| `MetadataService` | `dubbo-metadata-api` | 元数据服务接口 |
| `MetadataReport` | `dubbo-metadata-api` | 元数据中心读写抽象 |
| `MetadataReportInstance` | `dubbo-metadata-api` | MetadataReport 生命周期/实例入口 |
| `NacosMetadataReport` | `dubbo-metadata-report-nacos` | Nacos 元数据报告实现 |
| `ConfigurableMetadataServiceExporter` | `dubbo-config-api` | 配置层驱动 MetadataService 暴露 |

### 3.10 Spring Boot 与 Spring 集成

| 类 | 所属模块 | 导航用途 |
|---|---|---|
| `DubboAutoConfiguration` | `dubbo-spring-boot-autoconfigure` | Boot 自动配置主入口 |
| `DubboRelaxedBindingAutoConfiguration` | compatible autoconfigure | Boot 属性绑定兼容入口 |
| `DubboConfigConfiguration` | `dubbo-config-spring` | Dubbo Config Bean 注册 |
| `ServiceAnnotationPostProcessor` | `dubbo-config-spring` | 扫描 Provider 注解 |
| `ReferenceAnnotationBeanPostProcessor` | `dubbo-config-spring` | 扫描并注入 Consumer 引用 |
| `ServiceBean` | `dubbo-config-spring` | Spring 生命周期桥接 ServiceConfig |
| `ReferenceBean` | `dubbo-config-spring` | Spring FactoryBean 桥接 ReferenceConfig |

## 4. 阶段 0 调用栈逐模块映射

### 4.1 Consumer 栈

| 栈帧组 | Maven 模块 | 运行时职责 |
|---|---|---|
| `LearningClient`、`LearningConsumerStackFilter` | `source-learning-consumer` | 业务入口与证据采集 |
| `LazyTargetInvocationHandler` | `dubbo-config-spring` | Spring 注入的延迟目标代理 |
| `LearningServiceDubboProxy0`、`InvokerInvocationHandler`、`InvocationUtil` | `dubbo-rpc-api` | Java 接口调用转为 Invocation/Invoker |
| `ScopeClusterInvoker`、`MockClusterInvoker` | `dubbo-cluster` | Cluster 外层包装 |
| `AbstractClusterInvoker`、`FailoverClusterInvoker` | `dubbo-cluster` | 候选列表、选择与 failover |
| `RouterSnapshotFilter` | `dubbo-cluster` | 路由快照横切逻辑 |
| `FilterChainBuilder`、Consumer context/classloader/metrics filters | `dubbo-cluster` | Consumer Filter 链 |
| `ReferenceCountInvokerWrapper`、`FutureFilter` | `dubbo-rpc-api` | Invoker 生命周期与 future 回调 |
| `MonitorFilter`、`MetricsFilter`、`MetricsClusterFilter` | `dubbo-metrics-default` | 指标与监控 |
| `ObservationSenderFilter` | `dubbo-tracing` | Consumer tracing |

Consumer 栈在自定义 Filter 内采集，因此网络发送帧位于采集点的下游，没有出现在当前线程栈快照中；阶段 2、3 将通过源码和更低采集点补齐。

### 4.2 Provider 栈

| 栈帧组 | Maven 模块 | 运行时职责 |
|---|---|---|
| `InternalRunnable` | `dubbo-common` | 内部线程上下文包装 |
| `ChannelEventRunnable`、`DecodeHandler`、`HeaderExchangeHandler` | `dubbo-remoting-api` | 线程派发、解码和 request-response 交换 |
| `DubboProtocol$1.reply`、`TraceFilter` | `dubbo-rpc-dubbo` | Dubbo Protocol 请求入口 |
| `ContextFilter`、`ProfilerServerFilter`、`EchoFilter`、`ClassLoaderFilter`、`GenericFilter`、`AccessLogFilter`、`ExceptionFilter`、`TimeoutFilter`、`ClassLoaderCallbackFilter` | `dubbo-rpc-api` | Provider 通用 Filter 与调用上下文 |
| `FilterChainBuilder` | `dubbo-cluster` | Provider Filter 链节点 |
| `ObservationReceiverFilter` | `dubbo-tracing` | Provider tracing |
| `MetricsProviderFilter`、`MetricsFilter`、`MonitorFilter` | `dubbo-metrics-default` | Provider 指标与监控 |
| `HttpContextFilter`、`HttpContextCallbackFilter`、`RestFilterAdapter` | `dubbo-rpc-triple` | 被依赖带入并激活的 HTTP/REST 横切适配层 |
| `DelegateProviderMetaDataInvoker` | `dubbo-config-api` | Provider 配置元数据包装 |
| `AbstractProxyInvoker`、`JavassistProxyFactory` 生成 Invoker | `dubbo-rpc-api` | Invoker 到业务实现调用 |
| `LearningServiceImplDubboWrap0` | 运行时生成类 | Javassist 方法派发包装 |
| `LearningServiceImpl` | `source-learning-provider` | 业务实现 |

## 5. 源码定位复核命令

查一个类真实属于哪个模块：

```bash
rg --files | rg '/ClassName\.java$'
```

查重点 POM 的直接 Dubbo 依赖：

```bash
rg -n '<artifactId>dubbo-' path/to/pom.xml
```

查 SPI 接口和实现资源：

```bash
rg -n '@SPI|@Adaptive|@Activate' path/to/source
rg -n 'fully.qualified.InterfaceName' --glob 'META-INF/dubbo/**'
```

查阶段 0 栈中的类：

```bash
rg --files | rg '/(FailoverClusterInvoker|DubboProtocol|HeaderExchangeHandler)\.java$'
```
