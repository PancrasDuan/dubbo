# 阶段 4 证据：报文、线程与 Future 实验

> 主笔记：[Dubbo Protocol、网络与 Provider 调用链](../04-protocol-remoting-provider.md)
> 实验模块：`dubbo-demo/dubbo-demo-source-learning`
> 实验日期：2026-07-26

## 1. 实验设计

阶段 4 增加了默认关闭的 `ProtocolFrameProbe`。它不手工伪造 Hessian 字节，而是调用仓库内真实 `DubboCodec`：

1. 构造固定 Request ID `0x0102030405060708`。
2. 构造调用 `LearningService.greet(LearningRequest)` 的 `RpcInvocation`。
3. 使用 Hessian2 URL 与 `DubboCodec.encode` 生成完整帧。
4. 手动解析 16 字节头，验证 magic、flags、ID 和 body length。
5. 通过 serialization ID 取得真实 ObjectInput，按 Dubbo body 顺序读回所有字段和 DTO。
6. 探针完成后再执行一次真实跨进程 RPC，避免把本地 round-trip 当成网络证明。

开关位于 Consumer：

```text
--learning.frame-probe=true
```

默认配置为 `false`，不会影响普通学习实验。

## 2. 构建结果

使用 JDK 21 与 Maven 3.9.9：

```text
mvn -Dspotless.action=apply \
  -pl dubbo-demo/dubbo-demo-source-learning/source-learning-consumer \
  -am package -DskipTests
```

结果：Reactor 56/56 模块成功，Consumer 可执行 fat jar 生成成功，源码仍按 Java 8 release 编译。

曾先用默认 JDK 执行聚合目录上的 `spotless:apply` 前缀命令，因当前 profile 中无法解析该 plugin prefix 而失败；改用 JDK 21 和项目已有的 `spotless.action=apply` 绑定后构建成功。这是构建命令选择问题，不是代码编译失败。

## 3. 确定性帧输出

关键日志：

```text
LEARNING_DUBBO_FRAME
headerHex=dabbc200010203040506070800000173
frameBytes=387
bodyBytes=371
magic=dabb
flags=c2
request=true
twoWay=true
event=false
serializationId=2
statusOrReserved=0
requestId=72623859790382856
bodyPrefixHex=05322e302e3230336f72672e6170616368652e647562626f2e736f757263656c6561726e696e672e6170692e4c6561726e696e675365727669636505302e302e3005677265657430354c6f72672f6170616368652f647562626f2f736f757263
```

头字段独立换算：

| 字节 | 值 | 验证 |
|---|---|---|
| 0–1 | `dabb` | Dubbo magic |
| 2 | `c2` | request `0x80` + twoWay `0x40` + Hessian2 `0x02` |
| 3 | `00` | Request 保留字节 |
| 4–11 | `0102030405060708` | 十进制 `72623859790382856` |
| 12–15 | `00000173` | 十进制 371，与 `bodyBytes` 相等 |
| frame | 387 | 16 + 371，长度一致 |

这份样例固定了 ID 和参数，因此可用于源码调试与后续回归；实际线上 Request ID 仍由 `AtomicLong` 生成。

## 4. Hessian2 body 读回结果

第二条探针日志：

```text
LEARNING_DUBBO_BODY_DECODED
dubboVersion=2.0.2
path=org.apache.dubbo.sourcelearning.api.LearningService
serviceVersion=0.0.0
method=greet
parameterTypesDesc=Lorg/apache/dubbo/sourcelearning/api/LearningRequest;
requestId=frame-01
name=Protocol-Probe
attachmentKeys=[path, interface, version, timeout]
```

DTO 的 `requestId` 与 `name` 均能从生成的 body 恢复。body 前缀可辨认出短字符串 `2.0.2`、接口全名、`0.0.0`、`greet` 与 JVM 类型描述符，但不应依赖肉眼读 hex；可靠验证来自按 serialization ID 走真实 ObjectInput。

## 5. 真实网络调用结果

本地 probe 后，Consumer 发起独立调用：

```text
LEARNING_CONSUMER_SEND requestId=learning-01 name=Dubbo-1 thread=main
LEARNING_CONSUMER_SELECTED requestId=learning-01 target=127.0.0.1:20880
LEARNING_CONSUMER_RECEIVE requestId=learning-01
message=你好，Dubbo-1
providerId=provider-20880
thread=main
```

Provider 对同一请求记录：

```text
LEARNING_PROVIDER_RECEIVE
requestId=learning-01
name=Dubbo-1
providerId=provider-20880
thread=DubboServerHandler-10.220.80.142:20880-thread-2
remoteAddress=10.220.80.142:51921
localAddress=10.220.80.142:20880
```

请求字段、Provider ID 和网络端口相互吻合，Consumer 最终得到业务响应。

## 6. 线程切换证据

同一个请求在 Provider 的日志顺序为：

```text
[NettyServerWorker-3-1] DubboCodec
Because thread pool isolation is enabled ... body can only be decoded on the io thread

[DubboServerHandler-10.220.80.142:20880-thread-2] LearningServiceImpl
LEARNING_PROVIDER_RECEIVE requestId=learning-01
```

可确认：

- TCP 读取和当前配置下的 request body 解码发生在 `NettyServerWorker`。
- `AllChannelHandler` 把 received 事件提交给业务 executor。
- Provider Filter、proxy wrapper 与实现执行在 `DubboServerHandler`。
- Consumer 业务发送和接口返回日志都在 `main`；Netty client event loop 处理网络事件。

Provider 业务栈尾部：

```text
LearningServiceImpl.greet
→ LearningServiceImplDubboWrap0.invokeMethod
→ JavassistProxyFactory$1.doInvoke
→ AbstractProxyInvoker.invoke
→ DelegateProviderMetaDataInvoker.invoke
→ Provider Filter chain
→ DubboProtocol$1.reply
→ HeaderExchangeHandler.handleRequest
→ HeaderExchangeHandler.received
→ DecodeHandler.received
→ ChannelEventRunnable.run
```

## 7. Request / Future 源码事实

| 事实 | 源码位置 |
|---|---|
| `Request` 用静态 `AtomicLong` 生成 ID，默认 twoWay | `dubbo-remoting/.../exchange/Request.java` |
| `DubboInvoker` 设置 payload、协议版本、mode 与 timeout | `dubbo-rpc/.../dubbo/DubboInvoker.java:90` |
| `DefaultFuture.newFuture` 严格先于 `channel.send` | `dubbo-remoting/.../HeaderExchangeChannel.java:148` |
| future 构造时写入 `FUTURES`、`CHANNELS` 并登记 timeout | `dubbo-remoting/.../DefaultFuture.java:94` |
| `received` 按 ID remove，取消 timeout，再完成结果 | `dubbo-remoting/.../DefaultFuture.java:196` |
| 未找到 Future 的 response 会作为迟到响应记录 | 同上 |

发送失败分支会取消刚创建的 future；正常响应、超时和 channel 关闭都必须走清理路径，避免静态 map 积累请求状态。

## 8. Provider 服务定位源码事实

`DubboProtocol.getInvoker` 的定位输入与输出：

```text
channel.localAddress.port
+ invocation.path
+ invocation.version
+ invocation.group
→ serviceKey
→ exporterMap.get(serviceKey)
→ DubboExporter.getInvoker
```

真实栈中的 `DubboProtocol$1.reply` 位于 Provider Filter 之前，说明 exporter 查找先得到入口 Invoker，之后才执行 Filter 与实现。此前停机实验的 `Service not found` 则证明 exporter 被撤销时，仍存活连接上的请求会在这里失败。

## 9. Response 与 Future 的可验证不变量

本实验没有把生产 Request ID 额外写入日志，但源码与固定帧可建立以下调试不变量：

1. Provider `HeaderExchangeHandler` 创建的 Response ID 必须等于 Request ID。
2. 编码后的响应头 4–11 字节必须仍是该 ID。
3. Consumer 解码出的 Response 以同一 ID 调用 `DefaultFuture.received`。
4. 正常完成后 `FUTURES[id]` 与 `CHANNELS[id]` 都不存在。
5. Consumer 得到的 `LearningResponse` 必须来自该 future 中的 `AppResponse`。

若断点发现 1–3 任一不相等，问题属于报文关联；若 ID 相等但 future 不存在，应检查超时、提前取消、连接关闭或重复响应。

## 10. 实验边界

- 帧样例是生产 codec 的本地 round-trip，不是 tcpdump 抓包。
- 线程名证明本次默认 dispatcher 和 isolation 配置，不代表所有 dispatcher 配置都相同。
- Future 生命周期由源码与成功调用共同确认；要直接观察 map 大小和迟到响应，可在阶段 8 加超时注入或 debugger watch。
- 本次只验证 Hessian2；切换 serialization 时 flags 低 5 位、ObjectInput/Output 和 body 字节都会变化。

## 11. 变更文件

- `source-learning-consumer/.../ProtocolFrameProbe.java`
- `source-learning-consumer/.../LearningClient.java`
- `source-learning-consumer/src/main/resources/application.yml`

Provider 正常优雅退出，进程退出码 `130` 来自实验结束时发送 `Ctrl+C`，不是运行失败。
