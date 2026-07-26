# 分阶段学习方案

本目录保存各阶段的任务、实验、图示、证据和验收细则。阶段依赖与质量门禁见[路线图](../roadmap.md)，实际状态只在[学习入口](../README.md)维护。

1. [阶段 0：稳定基线与实验台](00-baseline-and-lab.md)
2. [阶段 1：全局架构与模块地图](01-architecture-map.md)
3. [阶段 2：一次同步 RPC 调用全景](02-rpc-overview.md)
4. [阶段 3：Consumer 调用链](03-consumer-invocation.md)
5. [阶段 4：Dubbo Protocol、网络与 Provider](04-protocol-remoting-provider.md)
6. [阶段 5：服务暴露与服务引用](05-export-and-reference.md)
7. [阶段 6：Spring Boot 3 集成与生命周期](06-spring-boot-integration.md)
8. [阶段 7：Nacos 三中心与双服务发现](07-nacos-service-discovery.md)
9. [阶段 8：SPI、集群治理、异常与安全](08-spi-governance-failure-security.md)
10. [阶段 9：知识体系收口](09-knowledge-consolidation.md)

## 维护规则

1. 开始或完成阶段时，只更新根 `README.md` 的状态。
2. 实际分析结果写入对应主题文档，不回填到阶段方案。
3. 阶段方案只在任务、实验、源码入口或验收标准变化时更新。
4. 一个阶段未通过路线图中的质量门禁时，不启动依赖它的下一阶段。
