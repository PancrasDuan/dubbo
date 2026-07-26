# 阶段 5 证据：创建、复用与销毁实验

> 主笔记：[服务暴露与服务引用](../05-service-export-and-reference.md)
> 演示模块：`dubbo-demo/dubbo-demo-source-learning`
> 聚焦测试：`ReferenceCountExchangeClientTest`
> 实验日期：2026-07-26

## 1. Provider 配置与 ServiceBean 创建

Spring 扫描日志：

```text
Found 1 classes annotated by Dubbo @Service:
org.apache.dubbo.sourcelearning.provider.LearningServiceImpl

Register ServiceBean[
  ServiceBean:org.apache.dubbo.sourcelearning.api.LearningService::
]
```

生效配置：

```text
application name=source-learning-provider
application executorManagementMode=isolation
protocol name=dubbo
protocol port=20880
protocol serialization=hessian2
registry address=N/A
```

这组日志把注解实现、`ServiceBean`、`ApplicationConfig` 和 `ProtocolConfig` 连在一起；其后的 export 日志来自这个配置对象，而不是手工调用 `DubboProtocol`。

## 2. Provider URL 形成

业务服务实际导出两个 URL：

```text
injvm://127.0.0.1/org.apache.dubbo.sourcelearning.api.LearningService
  ?application=source-learning-provider
  &bind.ip=10.220.80.142
  &bind.port=20880
  &dubbo=2.0.2
  &executor-management-mode=isolation
  &interface=org.apache.dubbo.sourcelearning.api.LearningService
  &methods=greet
  &prefer.serialization=hessian2
  &serialization=hessian2
  &side=provider

dubbo://10.220.80.142:20880/org.apache.dubbo.sourcelearning.api.LearningService
  ?同一组主要 Provider 参数
```

URL 中 `application` 来自 ApplicationConfig，port/serialization 来自 ProtocolConfig，interface/methods 来自 ServiceConfig 与接口反射，pid/timestamp/release 属于 runtime 参数，bind 信息来自地址解析。

## 3. 同端口多服务、单 Server

业务服务之后，Dubbo 又导出内部元数据服务：

```text
dubbo://10.220.80.142:20880/org.apache.dubbo.metadata.MetadataService
  ?group=source-learning-provider
  &version=1.0.0
  &register=false
```

启动段只有一条 Server 创建日志：

```text
Start NettyServer bind /0.0.0.0:20880,
export /10.220.80.142:20880
```

没有第二次 bind，也没有端口冲突。两项服务的区别至少包括 path、group、version，因而产生不同 service key；两者 address 相同，命中同一个 `serverMap[10.220.80.142:20880]`。

## 4. Consumer 引用创建

字段扫描阶段：

```text
Register dubbo reference bean: learningService =
ReferenceBean:org.apache.dubbo.sourcelearning.api.LearningService(
  filter=[learningConsumerStack],
  loadbalance=roundrobin,
  protocol=dubbo,
  retries=0,
  timeout=3000,
  url=dubbo://127.0.0.1:20880?serialization=hessian2)
```

Module 启动期间：

```text
ReferenceConfig has been built
→ Successfully connect to server ...:20880
→ Start NettyClient ...:20880
→ Referred dubbo service: [LearningService]
→ Dubbo Module has started
→ Dubbo Application is ready
```

调用后打印的对象树保留了如下关键骨架：

```text
ScopeClusterInvoker
→ StaticDirectory
→ ReferenceCountInvokerWrapper
→ Consumer protocol Filter chain
→ ListenerInvokerWrapper
→ DubboInvoker
```

真实代理栈最外还包含 Spring `LazyTargetInvocationHandler`，内部接口代理是 `LearningServiceDubboProxy0`；Spring 包装细节在阶段 6 展开。

## 5. Client 共享聚焦测试

执行命令：

```text
mvn -pl dubbo-rpc/dubbo-rpc-dubbo \
  -Dtest=ReferenceCountExchangeClientTest#\
test_share_connect+test_not_share_connect+test_multi_share_connect+test_multi_destroy \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 6.114 s
```

### 5.1 每个测试实际断言

| 测试 | 构造 | 核心断言 |
|---|---|---|
| `test_share_connect` | 同端口 `demo`、`hello`；`connections=0, shareconnections=1` | 两者底层 Client 相等，local address 相等 |
| `test_not_share_connect` | 同端口两个接口；`connections=1` | 两者 Client 不同，local address 不同 |
| `test_multi_share_connect` | `connections=0, shareconnections=3` | 两个 Invoker 都持有相同的 3 个引用计数 Client |
| `test_multi_destroy` | 默认共享 | 同一 Invoker destroy 两次后，另一接口仍调用成功，无计数错误日志 |

测试 `init` 每次在一个可用随机端口导出 `demo`、`hello` 两个服务并各自 refer。共享三连接用例的运行日志在同一端口建立 3 个 Netty Client；独占用例建立 2 个不同 local port 的 Client。测试结束关闭 channel、Server 并 unexport 两个 service URL。

### 5.2 源码状态变化

默认共享：

```text
第一次同 address refer
→ referenceClientMap.compute
→ new SharedClientsProvider
→ ReferenceCountExchangeClient.count = 1

第二次同 address refer
→ originValue.increaseCount()
→ 同一 Client.count = 2

第一个 Invoker.destroy
→ close
→ count = 1，底层连接保留

第二个 Invoker.destroy
→ close
→ count = 0，底层连接关闭并安排移除 map entry
```

`test_multi_destroy` 特别证明 Invoker 自身的 destroyed 检查阻止第二次 destroy 重复减计数。

## 6. 一次真实调用闭环

Consumer：

```text
LEARNING_CONSUMER_SEND requestId=learning-01 thread=main
LEARNING_CONSUMER_SELECTED target=127.0.0.1:20880
LEARNING_CONSUMER_RECEIVE
  requestId=learning-01
  providerId=provider-stage5
  thread=main
```

Provider：

```text
LEARNING_PROVIDER_RECEIVE
  requestId=learning-01
  providerId=provider-stage5
  localAddress=10.220.80.142:20880
  remoteAddress=10.220.80.142:52323
```

这证明刚创建的 Proxy、Invoker、Client、Server 和 Exporter 确实参与了调用，而不只是初始化后闲置。

## 7. Consumer 销毁顺序

业务完成后 `context.close()` 的关键顺序：

```text
Dubbo Module[1.1.1] is stopping
→ Close netty channel [local :52323 → remote :20880]
→ NettyClientHandler disconnected
→ Module stopped
→ Application stopping
→ Destroying protocol [DubboProtocol]
→ internal Module stopped
→ RegistryManager close []
→ executor repository destroyed
→ FrameworkModel destroyed
→ GlobalResourcesRepository destroyed
→ Spring container unbound
```

Consumer 进程退出码为 0。只有一个引用，因此引用销毁后共享 Client 计数归零，channel 当场关闭。

## 8. Provider 优雅销毁顺序

Consumer 刚调用完成即向 Provider 发送 `Ctrl+C`：

```text
16:43:57.970 Run shutdown hook now
16:43:57.971 Module is stopping
16:43:57.972 LearningService has idle for 0 ms,
             wait for 3332 ms to un-export
16:44:01.312 Module has stopped
16:44:01.313 Application is stopping
16:44:01.314 Destroying protocol [DubboProtocol]
16:44:01.314 Closing dubbo server: /10.220.80.142:20880
16:44:01.317 Close NettyServer bind /0.0.0.0:20880
16:44:03.412 Unexport MetadataService
16:44:03.419 Application has stopped
16:44:03.421 Framework destroyed
16:44:03.422 Global resources destroyed
```

退出码 `130` 来自人为 `Ctrl+C`。从时间戳可见：最近调用后的 idle wait 实际生效；Server 只关闭一次；内部 MetadataService 的剩余 exporter 在 `AbstractProtocol.destroy` 阶段清理。

## 9. RegistryProtocol 源码边界

本实验禁用注册中心，没有伪造 Nacos 运行证据。当前只记录可供阶段 7 继续的源码入口：

| 方向 | 入口 | 下一关键状态 |
|---|---|---|
| Provider | `RegistryProtocol.export` | 原 Provider URL 在 `EXPORT_KEY` attribute，先 local export，再 register/subscribe |
| Consumer | `RegistryProtocol.refer` | reference parameters 在 `REFER_KEY` attribute，生成 consumer URL 和 MigrationInvoker |
| 地址通知 | Registry Directory | 对每个 Provider URL 调用具体 `protocol.refer` |

## 10. 源码证据索引

| 事实 | 源码位置 |
|---|---|
| Provider 参数合并与 URL 构建 | `ServiceConfig.buildAttributes/buildUrl` |
| Provider Invoker 与 Exporter 创建 | `ServiceConfig.doExportUrl` |
| Consumer URL 与 Invoker/Proxy 创建 | `ReferenceConfig.createProxy/createInvoker` |
| Protocol scheme 自适应 | `Protocol` 的 `@Adaptive export/refer` |
| Proxy key 自适应与默认 javassist | `ProxyFactory` 的 `@SPI`、`@Adaptive` |
| Exporter/Server 两种 key | `AbstractProtocol.serviceKey`、`DubboProtocol.openServer` |
| address 级 Client 共享 | `DubboProtocol.getClients/getSharedClient` |
| 引用计数关闭 | `SharedClientsProvider`、`ReferenceCountExchangeClient` |
| 服务和引用清理 | `ServiceConfig.unexport`、`ReferenceConfig.destroy`、`DubboProtocol.destroy` |

## 11. 实验边界

- 演示运行采用直连和 `N/A` registry，RegistryProtocol 仅有源码边界，没有 Nacos 运行证据。
- Client 共享测试直接针对 `DubboProtocol`，隔离了 Spring 和 Cluster 干扰，适合证明连接语义；演示运行补足完整配置链。
- “同地址共享”限定在同一 DubboProtocol/FrameworkModel 范围，不能跨 JVM。
- 运行日志能证明对象行为与顺序；具体 map 内容主要由源码和测试断言证明，未通过反射修改内部状态。
