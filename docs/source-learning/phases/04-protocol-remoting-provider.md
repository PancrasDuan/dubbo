# 阶段 4：Dubbo Protocol、网络与 Provider 调用链

> 前置阶段：阶段 2、3
> 质量门禁：G4

## 1. 阶段目的

从 `DubboInvoker` 开始，解释请求如何编码成字节、跨越网络、切换到 Provider 线程、定位服务、执行实现，并通过响应 ID 回到原 Consumer 调用。

## 2. 核心问题

- 同步调用为什么在协议层使用 `AsyncRpcResult` 和 `CompletableFuture`？
- Request ID、twoWay、timeout 和 payload 在哪里设置？
- `DefaultFuture` 为什么必须在发送前注册？
- Dubbo 报文头如何表达协议与序列化信息？
- Hessian2 如何写入和读取方法、类型、参数与 attachments？
- Netty IO 线程如何切换到 Dubbo 业务线程池？
- Provider 如何生成 service key 并找到 Exporter？
- Provider Filter 和业务实现分别在什么位置执行？
- Response 如何匹配 Future 并恢复成接口返回值？

## 3. 重点源码

- `DubboInvoker.doInvoke`
- `HeaderExchangeChannel.request`
- `DefaultFuture.newFuture`、`sent`、`received`
- `ExchangeCodec`
- `DubboCodec.encodeRequestData`、`decodeBody`
- `Hessian2Serialization`
- `Hessian2ObjectOutput`、`Hessian2ObjectInput`
- `NettyCodecAdapter`
- `NettyClientHandler`、`NettyServerHandler`
- `ChannelHandlers.wrap`
- `AllChannelHandler.received`
- `HeaderExchangeHandler.received`、`handleRequest`
- `DubboProtocol.getInvoker`
- `DubboProtocol` 内部 `requestHandler.reply`

## 4. 实施任务

1. 记录 Request 创建、ID、超时和 twoWay 状态。
2. 观察 Future 注册、发送、完成和清理。
3. 记录 Consumer、Netty IO、Provider 业务和回调线程。
4. 生成或捕获一份可解释的 Dubbo 请求字节。
5. 将 16 字节报文头与源码字段逐项对应。
6. 跟踪 Hessian2 对 DTO、方法名、参数类型和 attachments 的处理。
7. 跟踪 Provider service key、Exporter 和 Invoker 定位。
8. 沿响应方向重复记录编解码和 Future 匹配。

## 5. 计划图示

- Dubbo 报文结构图。
- Invocation → 字节 → Invocation 数据转换图。
- Netty Pipeline 与 Handler 包装链。
- Consumer/Provider 线程切换图。
- Request ID 与 DefaultFuture 生命周期图。
- Provider service key 与 ExporterMap 查找图。

## 6. 交付物

- `04-protocol-remoting-provider.md`。
- 报文头字段说明与字节样例。
- 线程切换实测记录。
- Hessian2 编解码图。
- 响应匹配和 Future 生命周期图。
- 网络与 Provider 断点清单。

## 7. G4 验收清单

- [ ] 能从 DTO 参数追踪到网络字节，再恢复成 Provider 参数。
- [ ] 能解释 Request ID 和 DefaultFuture 的关系。
- [ ] 能用线程名证明主要线程切换。
- [ ] 能说明 service key 如何定位 Exporter。
- [ ] 能沿响应链追踪到 Consumer 接口返回值。
- [ ] 能区分 Exchange、Transport、Codec 和 Netty 的职责。

## 8. 向阶段 5、8 交接

- DubboInvoker、Client、Server、Exporter 等需要反向解释来源的对象清单。
- 网络、超时、线程和序列化异常的插入点。
- 可复用的报文与线程观测方法。
