# 阶段 2：一次同步 RPC 调用全景

> 前置阶段：阶段 0、1
> 质量门禁：G2

## 1. 阶段目的

在暂不追究对象创建来源的前提下，建立一次同步 RPC 从业务代理到 Provider 实现、再返回 Consumer 的端到端运行地图。

## 2. 固定场景

- 单 Consumer、单 Provider。
- 直连。
- Dubbo Protocol。
- Hessian2。
- 普通 Java 接口同步调用。
- 正常返回。
- `retries=0`。

Nacos、异步 API、泛化调用、多 Provider 和复杂异常在后续阶段展开。

## 3. 核心问题

1. 普通接口调用如何进入 `InvokerInvocationHandler`？
2. Java Method 和参数如何变成 `RpcInvocation`？
3. 为什么单 Provider 仍经过 Cluster 和 Directory？
4. `DubboInvoker` 如何创建 Request？
5. 请求如何经过 Exchange、Transport、Codec 和 Netty？
6. Provider 如何找到对应 Exporter 和 Invoker？
7. 结果如何编码、发送并匹配原请求？
8. 同步调用表象下有哪些异步机制？

## 4. 主链路源码锚点

```text
InvokerInvocationHandler.invoke
→ InvocationUtil.invoke
→ AbstractClusterInvoker.invoke
→ FailoverClusterInvoker.doInvoke
→ DubboInvoker.doInvoke
→ HeaderExchangeChannel.request
→ DefaultFuture.newFuture
→ DubboCodec.encodeRequestData
→ NettyServerHandler.channelRead
→ HeaderExchangeHandler.handleRequest
→ DubboProtocol.getInvoker
→ DubboProtocol.requestHandler.reply
→ DefaultFuture.received
```

实际运行中会出现 Filter、Listener、Adaptive 和 Wrapper。文档同时保存“稳定抽象链路”和“真实对象链路”，不能用简化图替代调用栈证据。

## 5. 实施任务

1. 在阶段 0 实验中设置全链路断点。
2. 保存正常请求的完整调用栈。
3. 记录关键对象实际类型、线程、URL、service key、Request ID 和 attachments。
4. 将调用栈分组为 Proxy、Cluster、Protocol、Exchange、Transport、Codec、Provider。
5. 绘制全景时序图和对象转换图。
6. 回答核心问题，明确哪些细节推迟到阶段 3～5。
7. 创建全局导航图，后续专题在图上标记当前放大位置。

## 6. 计划图示

- RPC 全景导航图。
- Consumer/Provider 端到端时序图。
- Method → Invocation → Request → Result 转换图。
- 线程与网络边界概览图。
- Request ID 与 Future 匹配概览图。
- Maven 模块穿行图。

## 7. 交付物

- `02-rpc-call-overview.md`。
- 一份完整同步调用栈。
- 全景图和对象转换图。
- 第一版全链路断点手册。
- 核心问题答案和复习题。

## 8. G2 验收清单

- [ ] 全景图中的每个关键节点都有源码锚点。
- [ ] 真实调用栈与简化链路的差异已经解释。
- [ ] 能脱离源码在十分钟内讲清端到端过程。
- [ ] 能说明 Invocation、Request、Result 和 Future 的关系。
- [ ] 能指出网络边界、序列化边界和主要线程边界。
- [ ] 未将对象创建过程与运行调用过程混为一谈。

## 9. 向阶段 3、4 交接

- RPC 全景导航图。
- 按 Consumer 和 Provider 两侧分组的真实调用栈。
- 需要进一步解释的对象类型、Wrapper 和线程切换清单。
