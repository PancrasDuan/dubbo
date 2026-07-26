# 阶段 7 证据：Nacos 数据转换、迁移与故障保护

> 主笔记：[Nacos 三中心与双服务发现](../07-nacos-and-service-discovery.md)
> 验证日期：2026-07-26
> 版本基线：Apache Dubbo 3.3.6，commit `f1585880bee4ca7776f44380c47c994217721ffe`

## 1. 验证边界

本阶段优先使用仓库已有的确定性测试验证对象链和算法，没有启动外部 Nacos Server。理由和边界如下：

- 接口注册、应用实例、迁移选择、地址刷新、空保护和重试都有可重复的 Mock 测试。
- `NacosDynamicConfigurationTest` 中需要真实 Nacos 的 3 项测试在上游源码中被 `@Disabled`，注释说明正在等待嵌入式 Nacos 问题解决。
- 配置中心和元数据中心的 Client 创建重试仍由各模块的 `RetryTest` 覆盖。
- 因而本文能证明 3.3.6 的转换、选择和保护逻辑，但不声称已经采集真实 Nacos 控制台截图、服务端日志或公司 namespace 数据。

真实环境复验时，应保留 Nacos Server 版本、namespace ID、group、dataId/serviceName、鉴权方式、断线持续时间和 Provider/Consumer 日志。

## 2. 实验 A：接口级注册与应用级发现

命令：

```bash
/Users/pancras/Office/apache-maven-3.9.9/bin/mvn \
  -pl dubbo-registry/dubbo-registry-nacos -am \
  -Dtest=NacosRegistryTest,NacosServiceDiscoveryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：

| 测试类 | 数量 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `NacosRegistryTest` | 5 | 0 | 0 | 0 |
| `NacosServiceDiscoveryTest` | 4 | 0 | 0 | 0 |
| 合计 | 9 | 0 | 0 | 0 |

Reactor 共 35 个模块成功，总耗时 20.478 秒。

覆盖点：

- 接口级 register/unregister；
- 接口级 subscribe/unsubscribe；
- 应用 ServiceInstance register/unregister；
- 获取应用服务列表；
- `NamingEvent` 转成 `ServiceInstancesChangedEvent`；
- 关闭空保护后，空实例列表产生一条 `empty://` URL。

## 3. 实验 B：双发现选择与地址刷新

命令：

```bash
/Users/pancras/Office/apache-maven-3.9.9/bin/mvn \
  -pl dubbo-registry/dubbo-registry-api -am \
  -Dtest=MigrationInvokerTest,MigrationRuleHandlerTest,DefaultMigrationAddressComparatorTest,ServiceInstancesChangedListenerTest,ServiceInstancesChangedListenerWithoutEmptyProtectTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：29 项测试全部通过，0 failure、0 error、0 skip；33 个 Reactor 模块成功，总耗时 46.725 秒，其中 `dubbo-registry-api` 耗时 36.175 秒。

### 3.1 双模式选择快照

`MigrationInvokerTest` 覆盖并确认：

| 场景 | 观察结果 |
|---|---|
| `FORCE_INTERFACE` | 当前调用选择接口级 Invoker |
| `FORCE_APPLICATION` | 当前调用选择应用级 Invoker |
| `APPLICATION_FIRST` 且应用级有地址 | 通过 comparator 后选择应用级 |
| 应用级不可用、接口级可用 | 回退接口级 |
| 两边都不可用 | 保留选择语义，但调用表现为不可用 |
| `promotion < 100` | 一部分调用可按比例走接口级 |
| 地址数量变化 | comparator 重新计算迁移结果 |

这些断言证明 `MigrationInvoker` 是“保留两条候选链、每次选一条”，不是“合并两个 Directory 的地址”。

### 3.2 应用实例到接口 URL 的对象快照

`ServiceInstancesChangedListenerTest` 使用两组应用实例：

```text
app1：3 个实例
app2：4 个实例
```

元数据解析后的可见结果为：

```text
DemoService  → 7 条 URL
DemoService2 → 4 条 URL
```

同一组用例还覆盖 Dubbo 与 Triple 两种 endpoint。实例并不直接等于接口 URL 数量；一个应用实例可依据 MetadataInfo 派生多个接口/协议 URL。

### 3.3 元数据 revision 失败与重试

测试构造两个 revision，其中一个首次获取失败。观察结果：

1. 成功 revision 先参与地址转换。
2. 失败 revision 被记录，提交异步重试任务。
3. 10 秒后收到 `RetryServiceInstancesChangedEvent`。
4. 第二次两个 revision 均可用，地址刷新完成。

当所有 revision 的 MetadataInfo 都为空时，本轮不会向 Directory 应用空结果，而是记录失败并等待重试或下一次实例事件。

### 3.4 空推送保护

同一套 listener 分别运行了保护开启和关闭版本：

| 配置 | 空实例事件行为 |
|---|---|
| 默认开启 | 忽略空列表，保留 Directory 现有地址 |
| 显式关闭 | 构造 `empty://` 标记，Directory 清空 Invoker |

接口级 `NacosRegistryTest` 也观察到关闭保护后生成 empty URL，说明两条发现链在 Directory 边界保持一致语义。

## 4. 实验 C：配置中心与元数据中心创建重试

命令：

```bash
/Users/pancras/Office/apache-maven-3.9.9/bin/mvn \
  -pl dubbo-configcenter/dubbo-configcenter-nacos,dubbo-metadata/dubbo-metadata-report-nacos -am \
  -Dtest=NacosDynamicConfigurationTest,RetryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：37 个 Reactor 模块成功，总耗时 15.471 秒。

| 模块 | 通过 | 跳过 | 说明 |
|---|---:|---:|---|
| `dubbo-configcenter-nacos` | 3 | 3 | `RetryTest` 通过；真实 Nacos 动态配置测试被上游禁用 |
| `dubbo-metadata-report-nacos` | 3 | 0 | `RetryTest` 通过 |
| 合计 | 6 | 3 | 无失败、无错误 |

两类 RetryTest 均验证：

- Client 状态持续 DOWN 时，按 `nacos.retry + 1` 次尝试后抛出 `IllegalStateException`；
- Mock 状态在后续变为 UP 后，下一次构造成功；
- `nacos.check=false` 时，即使状态 DOWN 也允许创建 Client；
- 试探请求失败与 server status DOWN 都会使默认检查失败。

这组证据只覆盖 Client 初始化。运行时动态配置的 publish/get/listener 没有在本机连接真实 Nacos，不能据此声称“配置变更实测成功”。

## 5. 实验 D：Naming Client 与单次请求重试

命令：

```bash
/Users/pancras/Office/apache-maven-3.9.9/bin/mvn \
  -pl dubbo-registry/dubbo-registry-nacos -am \
  -Dtest=NacosNamingServiceWrapperTest,NacosConnectionsManagerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

第一次在受限沙箱内运行时，Surefire 创建分叉进程所需的本机 socket bind 被拒绝，测试实际运行数为 0；在允许本机测试进程绑定后用相同命令重跑成功。该失败属于执行环境限制，不是产品测试失败。

成功结果：

| 测试类 | 数量 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `NacosNamingServiceWrapperTest` | 10 | 0 | 0 | 0 |
| `NacosConnectionsManagerTest` | 6 | 0 | 0 | 0 |
| 合计 | 16 | 0 | 0 | 0 |

35 个 Reactor 模块成功，总耗时 17.892 秒。

覆盖点：

- Client 创建时检查 server status 与试探请求；
- `nacos.check=false` 跳过启动可用性门禁；
- `accept`/`apply` 对写请求和读请求执行有限重试；
- 第一次调用耗尽重试后，后续新调用仍可成功；
- 同一服务多实例优先批量注册；服务端不支持批量时回退多连接逐个注册；
- subscription 与 registration 状态在 wrapper 内分别维护；
- 注销最后一个实例后移除对应注册状态。

需要避免一个过度结论：这些测试没有证明 Dubbo wrapper 在网络恢复时主动创建新 NamingService 并重新订阅。wrapper 把订阅绑定到原 NamingService；底层断线重连主要由 Nacos SDK 负责。Dubbo 额外提供的是当前请求有限重试，以及接口级 `FailbackRegistry` 对失败操作的周期补偿。

## 6. 从事件到 Invoker 的源码断点表

| 观察目的 | 建议断点 | 关键对象 |
|---|---|---|
| 接口级 Nacos 事件 | `NacosRegistry.NacosAggregateListener.onEvent` | `NamingEvent.instances` |
| Instance 转 URL | `NacosRegistry.buildURLs` | `Instance.metadata`、`List<URL>` |
| 应用级 Nacos 事件 | `NacosServiceDiscovery.NacosEventListener.onEvent` | `ServiceInstancesChangedEvent` |
| revision 聚合 | `ServiceInstancesChangedListener.doOnEvent` | `revisionToInstances`、`MetadataInfo` |
| 接口级 Directory | `RegistryDirectory.refreshInvoker` | `urlInvokerMap` |
| 应用级 Directory | `ServiceDiscoveryRegistryDirectory.refreshInvoker` | `urlInvokerMap`、instance key |
| 双发现选择 | `MigrationInvoker.decideInvoker` | `invoker`、`serviceDiscoveryInvoker`、`currentAvailableInvoker` |
| 迁移阈值 | `DefaultMigrationAddressComparator.shouldMigrate` | old/new address count、threshold |

推荐在刷新前后保存：URL 数量、Invoker identity、Client identity、available 状态和被销毁 Invoker 列表。只看日志中的地址数无法判断是否复用了旧 Client。

## 7. 真实 Nacos 复验脚本

本阶段没有把 Nacos Server 分发包或容器编排文件写进仓库。进入已有 Nacos 环境后，按以下顺序复验即可：

1. 使用专用 namespace，清理同名应用、接口 serviceName、mapping 和 revision 数据。
2. 分别以 `register-mode=interface`、`instance`、`all` 启动 Provider。
3. 在 Nacos Naming 和 Config 页面记录 serviceName、group、dataId、Instance metadata。
4. 启动 Consumer，在上述断点记录两个 Directory 和 `MigrationInvoker`。
5. 增加、停止一个 Provider，比较 Invoker identity 与连接 identity。
6. 暂停 Nacos 网络，持续发起 RPC；记录已有地址是否可调用、新事件是否停止。
7. 恢复网络，确认 SDK 连接、失败任务和元数据重试各自的日志。
8. 注入真实空推送前确认实验 namespace 隔离，避免影响共享环境。

数据清理属于破坏性操作，必须限定到实验 namespace 和精确 dataId/serviceName；不能对共享 namespace 批量删除。

## 8. 证据结论

本阶段的本地确定性证据足以确认：

- 接口级与应用级使用不同 Nacos 数据模型；
- 应用实例通过映射和 revision 元数据恢复接口 URL；
- 双发现模式由 MigrationInvoker 选择单条调用链，并能按可用性回退；
- 地址更新会驱动 Directory 复用、新建和销毁 Invoker；
- 空列表、元数据失败和 Nacos 请求失败分别有不同的保护层。

尚未完成的环境特定证据是：真实 Nacos namespace 的数据截图、服务端断线窗口和公司实际 migration rule。它们应作为部署环境附录补充，而不应回写成与环境无关的框架结论。
