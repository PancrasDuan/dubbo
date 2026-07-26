# 阶段 7：Nacos 三中心与双服务发现

> 前置阶段：阶段 5、6
> 质量门禁：G7

## 1. 阶段目的

在公司技术栈场景中，解释同一个 Nacos 集群作为注册中心、配置中心和元数据中心时的三条逻辑链路，以及接口级与应用级服务发现并存时的地址选择和刷新过程。

## 2. 三个逻辑身份

| 身份 | 主要数据 | 运行时影响 |
|---|---|---|
| 注册中心 | Provider URL 或应用实例 | 决定可调用地址列表 |
| 配置中心 | 外部配置、路由和治理规则 | 改变启动配置与运行行为 |
| 元数据中心 | 服务映射、接口元数据 | 支撑应用级服务发现恢复接口 URL |

## 3. 核心问题

- 三个中心在何时初始化，是否复用 Nacos Client？
- 本地 YAML 和 Nacos 外部配置如何形成最终有效配置？
- 接口级模式在 Nacos 中注册、订阅什么？
- 应用级模式如何通过实例、映射和元数据恢复接口 URL？
- 双注册和双订阅为什么不代表一次调用使用两套地址？
- 两种地址都存在时如何迁移、选择和回退？
- Provider 上下线如何驱动 Directory 更新？
- Invoker 如何新增、复用、失效和销毁？
- `metadata-type=local` 与 `remote` 的数据路径有何不同？
- Nacos 断开、恢复和空推送时如何保护调用链？

## 4. 重点源码

- `NacosRegistry`
- `NacosServiceDiscovery`
- `NacosDynamicConfiguration`
- `NacosMetadataReport`
- `RegistryProtocol`
- `RegistryDirectory`
- `ServiceDiscoveryRegistryDirectory`
- 服务迁移、映射、监听和地址刷新相关实现。

## 5. 对照实验

| 实验 | 配置模式 | 主要观察 |
|---|---|---|
| 接口级 | `interface` | Provider URL 如何进入 RegistryDirectory |
| 应用级 | `instance` | 实例、映射和元数据如何恢复 URL |
| 双模式 | `all` | 双注册、双订阅、选择和回退 |
| Provider 上下线 | 保持当前模式 | Directory 和 Invoker 如何刷新 |
| 配置变化 | Nacos 配置中心 | 配置监听与运行时对象更新 |
| 元数据模式 | local/remote | Consumer 如何取得接口元数据 |
| Nacos 中断 | 断开后恢复 | 重连、补偿注册和本地地址行为 |

实际迁移配置目前未知，实施时同时采集本地配置、Nacos 外部配置、启动日志、Nacos 实际数据和断点对象，不能只根据某一份 YAML 下结论。

## 6. 实施任务

1. 为实验 B 提供可重复的本地 Nacos 环境和数据清理方式。
2. 分别运行 `interface`、`instance`、`all`。
3. 保存三个逻辑中心使用的 namespace、group、dataId 或 serviceName。
4. 跟踪 Provider 注册与 Consumer 订阅回调。
5. 跟踪地址到 URL、Directory、DubboInvoker 的转换。
6. 动态上下线 Provider，记录对象增删和连接变化。
7. 修改治理配置，记录监听、解析和生效路径。
8. 执行断开与恢复实验，记录补偿和保护行为。

## 7. 计划图示

- Nacos 三中心逻辑架构图。
- 接口级与应用级数据模型对比图。
- Provider 双注册时序图。
- Consumer 双订阅、迁移与选择图。
- Nacos 数据 → URL → Directory → Invoker 转换图。
- 地址刷新和 Invoker 生命周期图。

## 8. 交付物

- `07-nacos-and-service-discovery.md`。
- 三种注册模式实验记录。
- Nacos 数据布局说明。
- 地址刷新调用栈和对象快照。
- 迁移配置与实际生效策略说明。

## 9. G7 验收清单

- [ ] 能区分同一 Nacos 集群的三个逻辑职责。
- [ ] 能说明接口级和应用级发现的数据差异。
- [ ] 能从一条 Nacos 数据追踪到可调用 DubboInvoker。
- [ ] 能用实验证明双模式下的地址选择行为。
- [ ] 能说明 Provider 上下线如何刷新 Directory。
- [ ] 能说明 Nacos 中断与恢复时的主要保护和补偿行为。

## 10. 向阶段 8 交接

- 动态地址、配置和元数据变更的事件入口。
- Router、Configurator、Directory 和 Invoker 刷新链路。
- Nacos 故障、空推送和迁移失败的实验插入点。
