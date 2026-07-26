# 阶段 1：全局架构与模块地图

> 前置阶段：阶段 0
> 质量门禁：G1

## 1. 阶段目的

建立源码导航能力，先理解模块、抽象和依赖方向，再进入方法级调用分析。

## 2. 范围与边界

重点覆盖：

- `dubbo-common`
- `dubbo-config`
- `dubbo-rpc`
- `dubbo-cluster`
- `dubbo-remoting`
- `dubbo-serialization`
- `dubbo-registry`
- `dubbo-configcenter`
- `dubbo-metadata`
- `dubbo-spring-boot-project`

Triple、REST、MCP、Native、Reactive 等模块只标记边界，不深入实现。

## 3. 核心问题

- Dubbo 的核心稳定抽象有哪些？
- 为什么 `Invoker` 可以统一 Consumer、Provider、Cluster 和 Protocol 的调用语义？
- `URL` 如何同时承担配置载体和扩展决策上下文？
- `ScopeModel` 如何组织 Framework、Application 和 Module 资源？
- 数据面、治理面、配置生命周期和 Spring 集成如何协作？
- Maven 模块边界和运行时对象边界有哪些差异？
- 哪些模块位于核心调用路径，哪些只在对象创建或地址变化时参与？

## 4. 实施任务

1. 从根 `pom.xml` 提取模块清单和聚合关系。
2. 从子模块 POM 提取核心依赖方向。
3. 为每个重点模块选取 3～8 个代表类。
4. 建立核心概念词典：URL、Invocation、Result、Invoker、Exporter、Protocol、Directory。
5. 使用阶段 0 调用栈标注一次请求经过的模块。
6. 区分稳定抽象、具体实现、兼容层和插件层。
7. 建立“问题类型 → 模块 → 入口类”的导航表。

## 5. 源码入口

| 主题 | 第一入口 |
|---|---|
| SPI | `ExtensionLoader` |
| 模型与作用域 | `FrameworkModel`、`ApplicationModel`、`ModuleModel` |
| 配置启动 | `DubboBootstrap` |
| RPC 抽象 | `Invoker`、`Invocation`、`Result`、`Protocol` |
| 集群治理 | `Directory`、`Router`、`LoadBalance`、`Cluster` |
| 网络抽象 | `Channel`、`Client`、`Server`、`ExchangeClient` |
| 服务发现 | `Registry`、`ServiceDiscovery` |
| Spring 集成 | `DubboAutoConfiguration`、`ServiceBean`、`ReferenceBean` |

## 6. 计划图示

- 系统上下文图。
- 四平面教学架构图。
- Maven 模块依赖图。
- 核心抽象关系图。
- RPC 调用模块穿行图。
- 源码目录导航图。

## 7. 交付物

- `01-architecture-overview.md`。
- 模块职责与依赖表。
- 核心概念词典第一版。
- 核心类索引第一版。
- 问题导航表。

## 8. G1 验收清单

- [ ] 所有重点模块都有职责、边界和代表类。
- [ ] 依赖图来自真实 POM，而不是仅凭目录名称推断。
- [ ] 阶段 0 调用栈中的核心帧都能映射到模块。
- [ ] 能解释数据面与治理面的区别。
- [ ] 能解释 Invoker、Protocol、Directory、Exporter 的关系。
- [ ] 能在五分钟内为一个核心问题找到候选模块和入口类。

## 9. 向阶段 2 交接

- 全景架构图和模块导航表。
- 核心术语与对象关系。
- 已完成模块标注的阶段 0 调用栈。
