# 阶段 2 证据：端到端源码锚点和复合调用链

> 主笔记：[一次同步 RPC 调用全景](../02-rpc-call-overview.md)
> 原始双端栈：[阶段 0 运行记录](00-direct-rpc-runtime.md)

## 1. 为什么是复合调用链

RPC 跨越进程、socket、线程和 CompletableFuture，不可能由一份 Java `Thread.getStackTrace()` 覆盖。完整证据由 Consumer 真实栈、Provider 真实栈，以及两者之间和响应方向的固定源码锚点组成。业务 `requestId` 关联双端事件；协议 `Request.id`/`Response.id` 证明 Future 匹配。

## 2. 端到端源码锚点

| 顺序 | 模块 | 类与方法 | 观察对象 |
|---|---|---|---|
| 1 | `dubbo-rpc-api` | `InvokerInvocationHandler.invoke` | Method、args、RpcInvocation |
| 2 | `dubbo-rpc-api` | `InvocationUtil.invoke` | service key、Result.recreate |
| 3 | `dubbo-cluster` | `AbstractClusterInvoker.invoke` | Directory、候选、LoadBalance |
| 4 | `dubbo-cluster` | `FailoverClusterInvoker.doInvoke` | invoke times、selected Invoker |
| 5 | `dubbo-rpc-api` | `AbstractInvoker.prepareInvocation` | InvokeMode、serialization ID |
| 6 | `dubbo-rpc-dubbo` | `DubboInvoker.doInvoke` | ExchangeClient、Request、AsyncRpcResult |
| 7 | `dubbo-remoting-api` | `HeaderExchangeChannel.request` | 先注册 Future，后 send |
| 8 | `dubbo-remoting-api` | `DefaultFuture.newFuture` | `request.id → future/channel` |
| 9 | `dubbo-rpc-dubbo` | `DubboCodec.encodeRequestData/decodeBody` | method、args、attachments、序列化 ID |
| 10 | `dubbo-remoting-netty4` | `NettyServerHandler.channelRead` | Netty channel、Request |
| 11 | `dubbo-remoting-api` | `HeaderExchangeHandler.handleRequest` | 同 ID Response、CompletionStage |
| 12 | `dubbo-rpc-dubbo` | `DubboProtocol.getInvoker` | serviceKey、exporterMap、Invoker |
| 13 | `dubbo-rpc-dubbo` | request handler `reply` | Provider Invoker、Result |
| 14 | 学习实验 | `LearningServiceImpl.greet` | 业务 ID、线程、地址 |
| 15 | `dubbo-rpc-dubbo` | `DubboCodec.encodeResponseData` | value/exception、attachments |
| 16 | `dubbo-remoting-netty4` | `NettyClientHandler.channelRead` | Response、Consumer channel |
| 17 | `dubbo-remoting-api` | `HeaderExchangeHandler.handleResponse` | response.id |
| 18 | `dubbo-remoting-api` | `DefaultFuture.received/doReceived` | 移除 Future、取消超时、完成结果 |
| 19 | `dubbo-rpc-api` | `AbstractInvoker.waitForResultIfSync` | ThreadlessExecutor、timeout |
| 20 | `dubbo-rpc-api` | `InvocationUtil.invoke` 返回 | Java value/exception |

## 3. 关键源码路径

```text
dubbo-rpc/dubbo-rpc-api/.../proxy/InvokerInvocationHandler.java
dubbo-rpc/dubbo-rpc-api/.../proxy/InvocationUtil.java
dubbo-cluster/.../support/AbstractClusterInvoker.java
dubbo-cluster/.../support/FailoverClusterInvoker.java
dubbo-rpc/dubbo-rpc-api/.../protocol/AbstractInvoker.java
dubbo-rpc/dubbo-rpc-dubbo/.../dubbo/DubboInvoker.java
dubbo-rpc/dubbo-rpc-dubbo/.../dubbo/DubboCodec.java
dubbo-rpc/dubbo-rpc-dubbo/.../dubbo/DubboProtocol.java
dubbo-remoting/dubbo-remoting-api/.../header/HeaderExchangeChannel.java
dubbo-remoting/dubbo-remoting-api/.../header/HeaderExchangeHandler.java
dubbo-remoting/dubbo-remoting-api/.../support/DefaultFuture.java
dubbo-remoting/dubbo-remoting-netty4/.../NettyServerHandler.java
dubbo-remoting/dubbo-remoting-netty4/.../NettyClientHandler.java
dubbo-common/.../threadpool/ThreadlessExecutor.java
```

## 4. 运行时事实

| 项目 | 阶段 0 实际值 |
|---|---|
| Consumer 业务线程 | `main` |
| Consumer Netty 线程 | `NettyClientWorker-1-1` |
| Provider Netty I/O 线程 | `NettyServerWorker-3-1` |
| Provider 首次业务线程 | `DubboServerHandler-10.220.80.142:20880-thread-2` |
| 连接 | `10.220.80.142:63351 → 10.220.80.142:20880` |
| 协议/序列化 | `dubbo` / `hessian2` |
| retries / timeout | `0` / `3000ms` |
| 首次业务 ID | `learning-01` |

## 5. 稳定角色与真实类型

| 稳定角色 | 本次可见类型/包装 |
|---|---|
| 业务 Proxy | `LearningServiceDubboProxy0`，外有 Spring lazy proxy |
| Cluster Invoker | `ScopeClusterInvoker` → `MockClusterInvoker` → Filter → `FailoverClusterInvoker` |
| Protocol Invoker | `DubboInvoker`（由固定源码锚点补齐） |
| Future | `DefaultFuture` → `CompletableFuture<AppResponse>` → `AsyncRpcResult` |
| Provider Exporter | `DubboExporter`，位于 `DubboProtocol.exporterMap` |
| Provider Invoker | Filter → `DelegateProviderMetaDataInvoker` → Javassist `AbstractProxyInvoker` |
| 业务分派/实现 | `LearningServiceImplDubboWrap0` → `LearningServiceImpl` |

## 6. 三个关键等式

```text
retries=0 → invokeTimes=retries+1=1

Request.id → FUTURES.put(id, future)
Response.id → FUTURES.remove(id) → complete(future)

Provider port + path + version + group
→ serviceKey → exporterMap → DubboExporter → Provider Invoker
```

本实验 path 为 `org.apache.dubbo.sourcelearning.api.LearningService`，端口为 `20880`，未设置 group/version。

## 7. 后续放大点

- 阶段 3：具体 Directory、Invoker 包装树和 LoadBalance 对象。
- 阶段 4：Dubbo header、Hessian2 字段、Netty pipeline、Dispatcher、ThreadlessExecutor。
- 阶段 5：Proxy、Directory、DubboInvoker、DubboExporter、Client/Server 的创建和销毁。
