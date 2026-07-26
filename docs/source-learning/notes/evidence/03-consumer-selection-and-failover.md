# 阶段 3 证据：Invoker 选择与 Failover 实验

> 主笔记：[Consumer 调用链](../03-consumer-invocation.md)
> 实验模块：`dubbo-demo/dubbo-demo-source-learning`

## 1. 实验增量

为阶段 3 增加以下可控观察点：

| 位置 | 能力 |
|---|---|
| Provider | 用 `learning.provider-port` 和 `learning.provider-id` 启动多个实例 |
| Provider | `biz-failure` 请求抛出可识别业务异常 |
| Consumer | `learning.call-count` 控制调用次数 |
| Consumer | `learning.scenario` 切换基线与业务异常 |
| Consumer | `learning.retry-override` 通过 attachment 覆盖单次调用 retries |
| Consumer | `learning.startup-delay-ms` 在建连后、调用前留出停机窗口 |
| Consumer | `loadbalance=roundrobin` 固定实验算法 |
| Consumer Filter | 每次输出目标地址、Invoker 类型、mode 与 attachment keys |
| InvokerTreeFormatter | 反射打印已知 Wrapper 字段，并标识重复实例 |

代码保持 Java 8 source 兼容；使用 JDK 21 构建和运行。全仓 reactor 构建结果为 57 个模块全部成功。

## 2. 双直连地址与实际包装树

Consumer 使用分号连接两个直连 URL：

```text
dubbo://127.0.0.1:20880?serialization=hessian2;
dubbo://127.0.0.1:20881?serialization=hessian2
```

关键运行快照压缩如下：

```text
root -> ScopeClusterInvoker
  directory -> StaticDirectory
    candidate[0] -> ReferenceCountInvokerWrapper [20880]
      -> CallbackRegistrationInvoker
        -> CopyOfFilterChainNode [LearningConsumerStackFilter]
          -> CopyOfFilterChainNode [RpcExceptionFilter]
            -> ListenerInvokerWrapper
              -> DubboInvoker
    candidate[1] -> ReferenceCountInvokerWrapper [20881]
      -> 同构 protocol chain -> DubboInvoker
  invoker -> MockClusterInvoker
    -> AbstractCluster$ClusterFilterInvoker
      -> ClusterCallbackRegistrationInvoker
        -> CopyOfClusterFilterChainNode ...
          -> FailoverClusterInvoker
            directory -> 同一个 StaticDirectory
```

反射树会同时经过 `originalInvoker`、`nextNode`、`directory` 等字段，因此同一对象可从多个字段到达；输出中的 `(same instance)` 用 identity 去重，主笔记的语义树已合并这些重复边。

## 3. Consumer Filter 顺序

真实栈给出的 Cluster 外到内顺序：

```text
ConsumerContextFilter
→ ObservationSenderFilter
→ ConsumerClassLoaderFilter
→ MetricsConsumerFilter
→ FutureFilter
→ MetricsClusterFilter
→ MonitorClusterFilter
→ RouterSnapshotFilter
→ FailoverClusterInvoker
```

每次具体地址尝试的 protocol 链：

```text
LearningConsumerStackFilter
→ RpcExceptionFilter
→ ListenerInvokerWrapper
→ DubboInvoker
```

首次选择栈中可直接看到：

```text
LearningConsumerStackFilter.invoke
→ ReferenceCountInvokerWrapper.invoke
→ AbstractClusterInvoker.invokeWithContext
→ FailoverClusterInvoker.doInvoke
→ AbstractClusterInvoker.invoke
→ RouterSnapshotFilter.invoke
→ ... Cluster Filters ...
→ ScopeClusterInvoker.invoke
→ InvocationUtil.invoke
→ InvokerInvocationHandler.invoke
→ LearningServiceDubboProxy0.greet
```

## 4. 等权 RoundRobin 实验

配置两个等权 Provider，连续调用六次：

| requestId | Consumer 选择 | Provider 响应 |
|---|---|---|
| `learning-01` | `127.0.0.1:20880` | `provider-20880` |
| `learning-02` | `127.0.0.1:20881` | `provider-20881` |
| `learning-03` | `127.0.0.1:20880` | `provider-20880` |
| `learning-04` | `127.0.0.1:20881` | `provider-20881` |
| `learning-05` | `127.0.0.1:20880` | `provider-20880` |
| `learning-06` | `127.0.0.1:20881` | `provider-20881` |

Provider 日志独立确认奇数请求由 `20880` 接收，偶数请求由 `20881` 接收。

## 5. 业务异常不重试

条件：两个节点均在线，`scenario=biz-failure`，`retryOverride=2`。

```text
LEARNING_CONSUMER_SELECTED requestId=learning-01 target=127.0.0.1:20880
LEARNING_EXPECTED_BIZ_FAILURE requestId=learning-01
```

Provider 结果：

```text
20880: LEARNING_PROVIDER_RECEIVE requestId=learning-01
20880: LEARNING_PROVIDER_BIZ_FAILURE requestId=learning-01
20881: 无 LEARNING_PROVIDER_RECEIVE
```

尽管覆盖为两次重试（最多三次尝试），业务实现只执行一次。远端业务异常随 `Result` 返回，Failover 已把这次具体 Invoker 调用视作返回成功，最终由 `InvocationUtil.recreate()` 在 Cluster 外抛出。

## 6. 停机后的成功重选

条件：

- Consumer 先连接 `20880`、`20881`。
- `retryOverride=1`，总尝试数为 2。
- `cluster.availablecheck=false`，保留首个断开候选供第一次选择。
- 建连后等待 20 秒，在业务调用前向 `20880` 发起优雅停机。

关键 Consumer 日志：

```text
LEARNING_CONSUMER_SEND requestId=learning-01 retryOverride=1
LEARNING_CONSUMER_SELECTED requestId=learning-01 target=127.0.0.1:20880
LEARNING_CONSUMER_SELECTED requestId=learning-01 target=127.0.0.1:20881
Although retry the method greet ... was successful by the provider 127.0.0.1:20881
LEARNING_CONSUMER_RECEIVE requestId=learning-01 providerId=provider-20881
```

首节点处于优雅停机窗口，连接仍可读写但业务 exporter 已撤销，Provider 报：

```text
Service not found: org.apache.dubbo.sourcelearning.api.LearningService, greet
```

Consumer 将该协议错误观察为非 Biz `RpcException`，Failover 第二轮重新 `Directory.list` 并避开 `invoked=[20880]`，重选 `20881`。第二个 Provider 只记录一次：

```text
LEARNING_PROVIDER_RECEIVE requestId=learning-01 providerId=provider-20881
```

这比单纯“端口不存在”多验证了一层：即使 TCP 连接暂时存在，只要协议调用以可重试 `RpcException` 失败，Failover 仍按异常语义重试。

## 7. lazy 连接拒绝负向对照

另一组实验让首 URL 使用 `lazy=true`，调用时 `20880` 从未启动，`20881` 在线，`retryOverride=1`。结果进程失败退出，未选择 `20881`。

原因链：

```text
lazy client 首次连接拒绝
→ 下层抛 IllegalStateException
→ AbstractInvoker 将 Throwable 包进异常 Result
→ FailoverClusterInvoker 收到并返回 Result
→ InvocationUtil.recreate() 在 Failover 外抛异常
```

这组结果不是 Failover 成功用例，而是异常封装位置影响容错语义的负向对照，说明不能只按“根因属于网络”判断是否会重试。

## 8. 源码锚点

| 主题 | 类与方法 |
|---|---|
| 代理与 Invocation | `InvokerInvocationHandler.invoke`、`InvocationUtil.invoke` |
| 直连 URL 与 StaticDirectory | `ReferenceConfig.parseUrl/createInvoker` |
| Protocol Filter | `ProtocolFilterWrapper.refer`、`DefaultFilterChainBuilder.buildInvokerChain` |
| Cluster Filter | `DefaultFilterChainBuilder.buildClusterInvokerChain` |
| 候选与路由 | `AbstractDirectory.list`、`StaticDirectory.doList`、`SingleRouterChain.route` |
| Cluster 主入口 | `AbstractClusterInvoker.invoke` |
| 初选与重选 | `AbstractClusterInvoker.select/doSelect/reselect` |
| 负载均衡 | `AbstractLoadBalance.select`、`RoundRobinLoadBalance.doSelect` |
| 容错循环 | `FailoverClusterInvoker.doInvoke` |
| 每次远端上下文 | `AbstractClusterInvoker.invokeWithContext/setRemote` |
| Context 合并与清理 | `ConsumerContextFilter.invoke/onResponse/onError` |

## 9. 实验清理观察

长时间运行的 fat jar 在退出阶段出现 Netty worker 的 `NoClassDefFoundError: DefaultPromise$1`，导致其中一个 Provider 未能自然退出，最后精确定位并结束该实验进程。这发生在业务验证完成后的类加载器/线程清理阶段，不影响请求选择结论，但应作为后续阶段 6 生命周期与优雅停机的待查问题保留。
