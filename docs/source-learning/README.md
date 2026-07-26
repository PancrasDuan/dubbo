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

当前执行阶段和状态以本页下表为唯一来源；总纲、路线图和阶段方案不重复维护状态。

## 阶段状态

| 编号 | 文档 | 状态 |
|---|---|---|
| 00 | [学习基线与实验台](phases/00-baseline-and-lab.md) | 方案已设计，实施待开始 |
| 01 | [Dubbo 全局架构与模块地图](phases/01-architecture-map.md) | 方案已设计，实施待开始 |
| 02 | [一次同步 RPC 调用全景](phases/02-rpc-overview.md) | 方案已设计，实施待开始 |
| 03 | [Consumer 调用链](phases/03-consumer-invocation.md) | 方案已设计，实施待开始 |
| 04 | [Dubbo Protocol、网络与 Provider 调用链](phases/04-protocol-remoting-provider.md) | 方案已设计，实施待开始 |
| 05 | [服务暴露与服务引用](phases/05-export-and-reference.md) | 方案已设计，实施待开始 |
| 06 | [Spring Boot 3 集成与生命周期](phases/06-spring-boot-integration.md) | 方案已设计，实施待开始 |
| 07 | [Nacos 三中心与双服务发现](phases/07-nacos-service-discovery.md) | 方案已设计，实施待开始 |
| 08 | [SPI、集群治理、异常与安全](phases/08-spi-governance-failure-security.md) | 方案已设计，实施待开始 |
| 09 | [总结、索引与复习材料](phases/09-knowledge-consolidation.md) | 方案已设计，实施待开始 |

## 阅读顺序

首次学习依次阅读总体方案、路线图和当前阶段方案；查阅问题时可直接从阶段表进入对应专题。
