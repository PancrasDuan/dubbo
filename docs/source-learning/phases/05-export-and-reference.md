# 阶段 5：服务暴露与服务引用

> 前置阶段：阶段 3、4
> 质量门禁：G5

## 1. 阶段目的

反向解释前面已经运行的 Proxy、ClusterInvoker、DubboInvoker、ExchangeClient、Exporter、Server 和 Provider Invoker 是如何创建、缓存、复用和销毁的。

## 2. Provider 服务暴露主线

```text
ServiceConfig
→ 配置检查与 URL 构建
→ ProxyFactory.getInvoker
→ Protocol.export
→ Filter / Listener Wrapper
→ DubboProtocol.export
→ DubboExporter
→ openServer
→ ExporterMap
```

重点源码：

- `ServiceConfig.export`
- `ServiceConfig.doExport`
- `ServiceConfig.doExportUrls`
- `ServiceConfig.doExportUrlsFor1Protocol`
- `ServiceConfig.exportUrl`
- `ServiceConfig.doExportUrl`
- `DubboProtocol.export`
- `DubboProtocol.openServer`
- `DubboExporter`

## 3. Consumer 服务引用主线

```text
ReferenceConfig.get
→ ReferenceConfig.init
→ ReferenceConfig.createProxy
→ Protocol.refer
→ DubboProtocol.refer
→ ExchangeClient
→ DubboInvoker
→ StaticDirectory / ClusterInvoker
→ ProxyFactory.getProxy
```

重点源码：

- `ReferenceConfig.get`
- `ReferenceConfig.init`
- `ReferenceConfig.createProxy`
- `DubboProtocol.refer`
- `DubboProtocol.getSharedClient`
- `DubboProtocol.initClient`
- Cluster join 和 ProxyFactory 相关入口。

## 4. 注册中心插入点

本阶段仅确认接口，不深入 Nacos：

```text
Provider：RegistryProtocol.export → 本地暴露 + 注册
Consumer：RegistryProtocol.refer → Directory + 订阅 + Cluster
```

重点入口：

- `RegistryProtocol.export`
- `RegistryProtocol.doLocalExport`
- `RegistryProtocol.refer`
- `RegistryProtocol.doRefer`

## 5. 核心问题

- `ServiceConfig` 和 `ReferenceConfig` 如何把配置转换成 URL？
- Provider Invoker 与 Consumer Invoker 的语义有何不同？
- 一个端口如何承载多个服务？
- 多个引用如何共享 Client 和连接？
- Filter、Listener、Protocol Wrapper 在什么时机组装？
- 直连、注册中心引用和 injvm 调用在哪里分支？
- Proxy、Invoker、Client、Server、Exporter 的生命周期如何衔接？

## 6. 实施任务

1. 从 `ServiceConfig` 跟踪 Provider URL 形成过程。
2. 保存 Protocol Adaptive 与 Wrapper 的实际类型。
3. 跟踪 ExporterMap key、Server 创建与复用。
4. 从 `ReferenceConfig` 跟踪 Consumer URL、Invoker 和 Proxy 创建。
5. 验证多个引用时 Client 共享和引用计数行为。
6. 执行关闭流程，记录对象销毁顺序和资源状态。
7. 标出 RegistryProtocol 插入点，供阶段 7 继续。

## 7. 计划图示

- 服务暴露时序图。
- 服务引用时序图。
- URL 参数来源与传播图。
- Proxy/Invoker/Exporter/Client/Server 对象关系图。
- 创建、复用与销毁生命周期图。

## 8. 交付物

- `05-service-export-and-reference.md`。
- 服务暴露与引用调用栈。
- URL 参数来源表。
- 运行时对象创建和销毁图。
- RegistryProtocol 插入点说明。

## 9. G5 验收清单

- [ ] 能从 Proxy 反向追踪到 ReferenceConfig。
- [ ] 能从 Exporter 反向追踪到 ServiceConfig。
- [ ] 能解释一个端口承载多个服务的定位机制。
- [ ] 能说明 Client 和 Server 的复用边界。
- [ ] 能解释 URL 如何驱动 Protocol 和其他扩展选择。
- [ ] 能用运行证据证明主要对象的创建与销毁顺序。

## 10. 向阶段 6、7 交接

- ServiceConfig、ReferenceConfig 与 DubboBootstrap 的核心入口。
- RegistryProtocol 的暴露和引用插入点。
- 对象生命周期图和 URL 形成过程。
