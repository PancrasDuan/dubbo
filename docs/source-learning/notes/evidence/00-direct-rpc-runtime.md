# 阶段 0 运行证据：首次直连 RPC

> 对应主笔记：[稳定基线与最小直连实验](../00-baseline-and-learning-guide.md)
> 执行时间：2026-07-26 15:35—15:38（Asia/Shanghai）

本文件保存 G0 验收需要的关键原始输出。为了控制篇幅，构建日志只保留 reactor 结果，运行日志保留配置、三次调用、首次双端调用栈和关闭证据。

## 1. 构建结果

```text
[INFO] source-learning-api ................................ SUCCESS
[INFO] source-learning-provider ........................... SUCCESS
[INFO] source-learning-consumer ........................... SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  38.869 s
[INFO] Finished at: 2026-07-26T15:35:00+08:00
```

完整 reactor 共 57 个模块，所有模块均为 `SUCCESS`。

## 2. Provider 启动与服务导出

```text
15:38:14.714 INFO  [main] ConfigManager -
<dubbo:protocol preferSerialization="hessian2" serialization="hessian2"
                port="20880" name="dubbo" id="protocolConfig" />

15:38:14.714 INFO  [main] ConfigManager -
<dubbo:registry port="0" id="registryConfig" address="N/A" />

15:38:14.930 INFO  [main] ServiceConfig -
Export dubbo service org.apache.dubbo.sourcelearning.api.LearningService to url
dubbo://10.220.80.142:20880/org.apache.dubbo.sourcelearning.api.LearningService
?application=source-learning-provider
&methods=greet
&prefer.serialization=hessian2
&register=false
&release=3.3.6
&serialization=hessian2
&side=provider

15:38:15.018 INFO  [main] NettyServer -
Start NettyServer bind /0.0.0.0:20880, export /10.220.80.142:20880

15:38:15.059 INFO  [main] ProviderApplication -
LEARNING_BASELINE role=provider javaVersion=21.0.3 dubboVersion=3.3.6
dubboCommit=b7868dbb56181bf04e3b0b6c11a77b25ffd2a951
```

## 3. Consumer 引用与连接

```text
15:38:29.257 INFO  [main] ReferenceAnnotationWithAotBeanPostProcessor -
Register dubbo reference bean: learningService =
ReferenceBean:org.apache.dubbo.sourcelearning.api.LearningService(
  filter=[learningConsumerStack],
  protocol=dubbo,
  retries=0,
  timeout=3000,
  url=dubbo://127.0.0.1:20880?serialization=hessian2)

15:38:29.344 INFO  [main] ConfigManager -
<dubbo:registry port="0" id="registryConfig" address="N/A" />

15:38:29.624 INFO  [main] NettyClient -
Successfully connect to server /10.220.80.142:20880

15:38:29.641 INFO  [main] ConsumerApplication -
LEARNING_BASELINE role=consumer javaVersion=21.0.3 dubboVersion=3.3.6
dubboCommit=b7868dbb56181bf04e3b0b6c11a77b25ffd2a951
```

直连 URL 写的是 `127.0.0.1`；日志中的 `10.220.80.142` 是 Dubbo URL 解析与本机网络地址选择后的连接地址，目标端口仍为固定的 `20880`。

## 4. 三次调用关联

```text
15:38:29.642 Consumer  LEARNING_CONSUMER_SEND
requestId=learning-01 name=Dubbo-1 thread=main
15:38:29.704 Provider  LEARNING_PROVIDER_RECEIVE
requestId=learning-01 name=Dubbo-1 providerId=provider-20880
thread=DubboServerHandler-10.220.80.142:20880-thread-2
remoteAddress=10.220.80.142:63351 localAddress=10.220.80.142:20880
15:38:29.716 Consumer  LEARNING_CONSUMER_RECEIVE
requestId=learning-01 message=你好，Dubbo-1 providerId=provider-20880 thread=main

15:38:29.717 Consumer  LEARNING_CONSUMER_SEND
requestId=learning-02 name=Dubbo-2 thread=main
15:38:29.718 Provider  LEARNING_PROVIDER_RECEIVE
requestId=learning-02 name=Dubbo-2 providerId=provider-20880
thread=DubboServerHandler-10.220.80.142:20880-thread-3
remoteAddress=10.220.80.142:63351 localAddress=10.220.80.142:20880
15:38:29.719 Consumer  LEARNING_CONSUMER_RECEIVE
requestId=learning-02 message=你好，Dubbo-2 providerId=provider-20880 thread=main

15:38:29.719 Consumer  LEARNING_CONSUMER_SEND
requestId=learning-03 name=Dubbo-3 thread=main
15:38:29.720 Provider  LEARNING_PROVIDER_RECEIVE
requestId=learning-03 name=Dubbo-3 providerId=provider-20880
thread=DubboServerHandler-10.220.80.142:20880-thread-4
remoteAddress=10.220.80.142:63351 localAddress=10.220.80.142:20880
15:38:29.721 Consumer  LEARNING_CONSUMER_RECEIVE
requestId=learning-03 message=你好，Dubbo-3 providerId=provider-20880 thread=main

15:38:29.721 Consumer  LEARNING_CALLS_COMPLETE count=3
```

## 5. Consumer 首次调用栈

采集点：自定义 Consumer Filter，进入 Dubbo 调用链后、调用下一个 Invoker 前。

```text
LEARNING_CONSUMER_STACK requestId=learning-01 target=127.0.0.1:20880 thread=main
    at org.apache.dubbo.sourcelearning.consumer.LearningConsumerStackFilter.invoke(LearningConsumerStackFilter.java:45)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CallbackRegistrationInvoker.invoke(FilterChainBuilder.java:197)
    at org.apache.dubbo.rpc.protocol.ReferenceCountInvokerWrapper.invoke(ReferenceCountInvokerWrapper.java:106)
    at org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker.invokeWithContext(AbstractClusterInvoker.java:412)
    at org.apache.dubbo.rpc.cluster.support.FailoverClusterInvoker.doInvoke(FailoverClusterInvoker.java:82)
    at org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker.invoke(AbstractClusterInvoker.java:366)
    at org.apache.dubbo.rpc.cluster.router.RouterSnapshotFilter.invoke(RouterSnapshotFilter.java:46)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.monitor.support.MonitorFilter.invoke(MonitorFilter.java:109)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.cluster.filter.support.MetricsClusterFilter.invoke(MetricsClusterFilter.java:57)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.protocol.dubbo.filter.FutureFilter.invoke(FutureFilter.java:53)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.metrics.filter.MetricsFilter.invoke(MetricsFilter.java:86)
    at org.apache.dubbo.rpc.cluster.filter.support.MetricsConsumerFilter.invoke(MetricsConsumerFilter.java:38)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.cluster.filter.support.ConsumerClassLoaderFilter.invoke(ConsumerClassLoaderFilter.java:40)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.tracing.filter.ObservationSenderFilter.invoke(ObservationSenderFilter.java:60)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.cluster.filter.support.ConsumerContextFilter.invoke(ConsumerContextFilter.java:119)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CallbackRegistrationInvoker.invoke(FilterChainBuilder.java:197)
    at org.apache.dubbo.rpc.cluster.support.wrapper.AbstractCluster$ClusterFilterInvoker.invoke(AbstractCluster.java:101)
    at org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterInvoker.invoke(MockClusterInvoker.java:107)
    at org.apache.dubbo.rpc.cluster.support.wrapper.ScopeClusterInvoker.invoke(ScopeClusterInvoker.java:156)
    at org.apache.dubbo.rpc.proxy.InvocationUtil.invoke(InvocationUtil.java:64)
    at org.apache.dubbo.rpc.proxy.InvokerInvocationHandler.invoke(InvokerInvocationHandler.java:81)
    at org.apache.dubbo.sourcelearning.api.LearningServiceDubboProxy0.greet(LearningServiceDubboProxy0.java)
    at org.apache.dubbo.config.spring.util.LazyTargetInvocationHandler.invoke(LazyTargetInvocationHandler.java:54)
    at org.apache.dubbo.sourcelearning.api.LearningServiceDubboProxy0.greet(LearningServiceDubboProxy0.java)
    at org.apache.dubbo.sourcelearning.consumer.LearningClient.runCalls(LearningClient.java:52)
    at org.apache.dubbo.sourcelearning.consumer.ConsumerApplication.main(ConsumerApplication.java:45)
```

## 6. Provider 首次调用栈

采集点：业务实现 `LearningServiceImpl.greet` 入口。

```text
LEARNING_PROVIDER_STACK requestId=learning-01
    at org.apache.dubbo.sourcelearning.provider.LearningServiceImpl.greet(LearningServiceImpl.java:59)
    at org.apache.dubbo.sourcelearning.provider.LearningServiceImplDubboWrap0.invokeMethod(LearningServiceImplDubboWrap0.java)
    at org.apache.dubbo.rpc.proxy.javassist.JavassistProxyFactory$1.doInvoke(JavassistProxyFactory.java:89)
    at org.apache.dubbo.rpc.proxy.AbstractProxyInvoker.invoke(AbstractProxyInvoker.java:100)
    at org.apache.dubbo.config.invoker.DelegateProviderMetaDataInvoker.invoke(DelegateProviderMetaDataInvoker.java:55)
    at org.apache.dubbo.rpc.filter.ClassLoaderCallbackFilter.invoke(ClassLoaderCallbackFilter.java:38)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.protocol.tri.rest.filter.RestFilterAdapter.invoke(RestFilterAdapter.java:38)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.protocol.tri.h12.HttpContextCallbackFilter.invoke(HttpContextCallbackFilter.java:37)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.protocol.dubbo.filter.TraceFilter.invoke(TraceFilter.java:78)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.filter.TimeoutFilter.invoke(TimeoutFilter.java:45)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.monitor.support.MonitorFilter.invoke(MonitorFilter.java:109)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.filter.ExceptionFilter.invoke(ExceptionFilter.java:55)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.filter.AccessLogFilter.invoke(AccessLogFilter.java:120)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.filter.GenericFilter.invoke(GenericFilter.java:223)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.protocol.tri.h12.HttpContextFilter.invoke(HttpContextFilter.java:38)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.filter.ClassLoaderFilter.invoke(ClassLoaderFilter.java:54)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.filter.EchoFilter.invoke(EchoFilter.java:41)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.metrics.filter.MetricsFilter.invoke(MetricsFilter.java:86)
    at org.apache.dubbo.metrics.filter.MetricsProviderFilter.invoke(MetricsProviderFilter.java:37)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.tracing.filter.ObservationReceiverFilter.invoke(ObservationReceiverFilter.java:59)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.filter.ProfilerServerFilter.invoke(ProfilerServerFilter.java:66)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.filter.ContextFilter.invoke(ContextFilter.java:191)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CopyOfFilterChainNode.invoke(FilterChainBuilder.java:349)
    at org.apache.dubbo.rpc.cluster.filter.FilterChainBuilder$CallbackRegistrationInvoker.invoke(FilterChainBuilder.java:197)
    at org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol$1.reply(DubboProtocol.java:167)
    at org.apache.dubbo.remoting.exchange.support.header.HeaderExchangeHandler.handleRequest(HeaderExchangeHandler.java:110)
    at org.apache.dubbo.remoting.exchange.support.header.HeaderExchangeHandler.received(HeaderExchangeHandler.java:205)
    at org.apache.dubbo.remoting.transport.DecodeHandler.received(DecodeHandler.java:52)
    at org.apache.dubbo.remoting.transport.dispatcher.ChannelEventRunnable.run(ChannelEventRunnable.java:64)
    at org.apache.dubbo.common.threadlocal.InternalRunnable.run(InternalRunnable.java:39)
```

## 7. Provider 正常关闭

```text
15:38:40.914 INFO  DubboProtocol - Closing dubbo server: /10.220.80.142:20880
15:38:40.915 INFO  NettyServer - Close NettyServer bind /0.0.0.0:20880
15:38:42.993 INFO  DubboProtocol - Unexport service: dubbo://10.220.80.142:20880/...
15:38:42.995 INFO  RegistryManager - Close all registries []
15:38:43.004 INFO  GlobalResourcesRepository - Dubbo is completely destroyed
```

## 8. 环境限制下的失败证据

第一次在受限沙箱内启动 Provider 时，服务导出到创建服务器的链路已经执行，但系统拒绝端口绑定：

```text
java.net.SocketException: Operation not permitted
    at sun.nio.ch.Net.bind0(Native Method)
    at io.netty.channel.socket.nio.NioServerSocketChannel.doBind(...)
    at org.apache.dubbo.remoting.transport.netty4.NettyServer.doOpen(...)
    at org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol.createServer(...)
    at org.apache.dubbo.config.ServiceConfig.doExportUrl(...)
```

在允许本地监听端口的环境中使用相同 JAR 重跑后成功。该失败不计入 G0 成功链路，但它提供了“服务导出如何落到 Netty bind”的第一份异常路径证据，可在阶段 4、5 继续使用。
