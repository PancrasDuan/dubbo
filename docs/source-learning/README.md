# Dubbo 源码学习

本目录用于沉淀基于 Apache Dubbo 3.3.6 的源码学习材料。内容面向希望理解框架设计、核心链路和运行机制的开发者，强调问题驱动、源码证据、运行验证和图示表达。

## 学习基线

| 项目 | 基线 |
|---|---|
| Dubbo | `dubbo-3.3.6` |
| 基线 commit | `f1585880bee4ca7776f44380c47c994217721ffe` |
| 学习分支 | `codex/learn-dubbo-source-3.3` |
| JDK | 21 |
| Spring Boot | 3.x |
| RPC 协议 | Dubbo Protocol |
| 序列化 | Hessian2 |
| 注册中心 | Nacos |
| 配置中心 | Nacos |
| 元数据中心 | Nacos |
| 服务发现 | 接口级与应用级并存 |

## 当前文档

- [源码学习总体方案](00-source-learning-plan.md)
- [整体学习路线图](roadmap.md)
- [分阶段方案索引](phases/README.md)
- [分阶段学习笔记](notes/README.md)
- [知识索引、排障与复习附录](appendix/README.md)
- [Dubbo 3.3.6 学习范围威胁模型](threat-model.md)

当前执行阶段和状态以本页下表为唯一来源；总纲、路线图和阶段方案不重复维护状态。

## 阶段状态

| 编号 | 文档 | 状态 |
|---|---|---|
| 00 | [学习基线与实验台](notes/00-baseline-and-learning-guide.md) | 已完成 |
| 01 | [Dubbo 全局架构与模块地图](notes/01-architecture-overview.md) | 已完成 |
| 02 | [一次同步 RPC 调用全景](notes/02-rpc-call-overview.md) | 已完成 |
| 03 | [Consumer 调用链](notes/03-consumer-invocation.md) | 已完成 |
| 04 | [Dubbo Protocol、网络与 Provider 调用链](notes/04-protocol-remoting-provider.md) | 已完成 |
| 05 | [服务暴露与服务引用](notes/05-service-export-and-reference.md) | 已完成 |
| 06 | [Spring Boot 3 集成与生命周期](notes/06-spring-boot-3-integration.md) | 已完成 |
| 07 | [Nacos 三中心与双服务发现](notes/07-nacos-and-service-discovery.md) | 已完成 |
| 08 | [SPI、集群治理、异常与安全](notes/08-spi-governance-failure-security.md) | 已完成 |
| 09 | [Dubbo 3.3.6 源码知识地图](notes/09-knowledge-consolidation.md) | 已完成 |

## 阅读顺序

首次学习依次阅读总体方案、路线图和阶段笔记；查阅问题时可直接从阶段表进入对应专题，或从附录按类名与故障现象检索。
