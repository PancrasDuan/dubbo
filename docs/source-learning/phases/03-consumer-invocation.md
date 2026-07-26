# 阶段 3：Consumer 调用链

> 前置阶段：阶段 2
> 质量门禁：G3

## 1. 阶段目的

放大 RPC 全景图的 Consumer 半链路，解释代理、Invocation、Filter、Cluster、Directory、Router、LoadBalance 和 DubboInvoker 如何协作选出最终 Provider。

## 2. 核心问题

- 动态代理持有的 Invoker 实际类型和包装层次是什么？
- `RpcInvocation` 的方法、参数、service key、model 和 attachments 分别从哪里来？
- Consumer Filter 与 Cluster Filter 的执行位置有何差异？
- Directory、Router 和 LoadBalance 各自负责什么？
- 单 Provider 时哪些选择逻辑会短路？
- 多 Provider 时如何选择、重选和避免重复 Invoker？
- Failover 的重试次数和业务异常边界如何确定？
- `RpcContext` 如何绑定、传递和恢复？

## 3. 重点源码

- `InvokerInvocationHandler.invoke`
- `InvocationUtil.invoke`
- `ProtocolFilterWrapper.refer`
- `DefaultFilterChainBuilder.buildInvokerChain`
- `DefaultFilterChainBuilder.buildClusterInvokerChain`
- `AbstractClusterInvoker.invoke`
- `AbstractDirectory.list`
- `StaticDirectory.doList`
- `RouterChain.route`
- `FailoverClusterInvoker.doInvoke`
- `AbstractClusterInvoker.select`
- `AbstractLoadBalance.select`

## 4. 实施任务

1. 打印代理类和完整 Invoker 包装树。
2. 记录 `RpcInvocation` 创建前后的字段变化。
3. 记录 Consumer Filter、Cluster Filter 的名称和顺序。
4. 跟踪 Directory 返回候选 Invoker 的过程。
5. 观察单 Provider 时的路由与选择短路。
6. 增加多 Provider 实验，观察 Router 和 LoadBalance。
7. 设置 `retries=0` 与默认重试分别观察 Failover 循环。
8. 验证业务异常与网络异常的重试差异。

## 5. 计划图示

- Consumer Invoker 包装树。
- RpcInvocation 字段来源图。
- Filter 与 Cluster Filter 执行顺序图。
- Directory → Router → LoadBalance 筛选漏斗图。
- Failover 选择与重选流程图。

## 6. 交付物

- `03-consumer-invocation.md`。
- Invoker 包装树与实际类型快照。
- Filter 顺序表。
- Provider 选择实验记录。
- Consumer 专用断点清单。

## 7. G3 验收清单

- [ ] 能解释接口代理如何变成 Invoker 调用。
- [ ] 能指出 Invocation 关键字段来源。
- [ ] 能说明 Directory、Router、LoadBalance 的边界。
- [ ] 能用断点证明最终 Provider 的选择原因。
- [ ] 能解释 Failover 的循环、重选和异常分类。
- [ ] 能区分直连 StaticDirectory 与后续动态 Directory。

## 8. 向阶段 4、5、8 交接

- 最终进入 `DubboInvoker` 前的对象和上下文状态。
- Consumer Invoker 包装树。
- Filter、路由、负载均衡和重试的待深入问题。
