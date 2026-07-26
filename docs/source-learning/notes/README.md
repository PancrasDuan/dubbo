# Dubbo 源码学习笔记

本目录保存按照[整体学习路线图](../roadmap.md)实施后形成的学习笔记。阶段方案回答“准备怎么学”，本目录回答“实际运行后确认了什么”。

## 阅读约定

- 每个阶段先阅读主笔记，再按需进入 `evidence/` 查看原始运行证据。
- 主笔记保留结论、复现步骤、图示和待验证问题；过长日志与完整调用栈单独保存。
- 阶段实际状态仍以[学习入口](../README.md)为唯一来源。

## 已生成笔记

| 阶段 | 主笔记 | 运行证据 |
|---|---|---|
| 00 | [稳定基线与最小直连实验](00-baseline-and-learning-guide.md) | [首次直连 RPC 运行记录](evidence/00-direct-rpc-runtime.md) |
| 01 | [Dubbo 全局架构与模块地图](01-architecture-overview.md) | [模块依赖和调用栈映射](evidence/01-pom-and-stack-map.md) |
| 02 | [一次同步 RPC 调用全景](02-rpc-call-overview.md) | [端到端源码锚点和复合调用链](evidence/02-end-to-end-call-chain.md) |
| 03 | [Consumer 调用链](03-consumer-invocation.md) | [Invoker 选择与 Failover 实验](evidence/03-consumer-selection-and-failover.md) |
| 04 | [Dubbo Protocol、网络与 Provider 调用链](04-protocol-remoting-provider.md) | [报文、线程与 Future 实验](evidence/04-frame-threads-and-future.md) |
| 05 | [服务暴露与服务引用](05-service-export-and-reference.md) | [创建、复用与销毁实验](evidence/05-creation-sharing-and-destroy.md) |
| 06 | [Spring Boot 3 集成与生命周期](06-spring-boot-3-integration.md) | [自动配置、属性覆盖与关闭事件](evidence/06-spring-events-config-and-shutdown.md) |
| 07 | [Nacos 三中心与双服务发现](07-nacos-and-service-discovery.md) | [数据转换、迁移与故障保护](evidence/07-nacos-data-refresh-and-migration.md) |
| 08 | [SPI、集群治理、异常与安全](08-spi-governance-failure-security.md) | [SPI、治理与异常实验](evidence/08-spi-governance-and-failures.md)；[威胁模型](../threat-model.md) |
| 09 | [Dubbo 3.3.6 源码知识地图](09-knowledge-consolidation.md) | [概念、源码、排障、复习与升级附录](../appendix/README.md) |
