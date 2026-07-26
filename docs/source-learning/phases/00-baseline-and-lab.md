# 阶段 0：稳定基线与实验台

> 前置阶段：无
> 质量门禁：G0

## 1. 阶段目的

建立后续所有源码分析共用的稳定版本、运行环境和最小实验，确保每个结论都能在同一条可重复调用链上验证。

## 2. 已完成准备

- 稳定 tag：`dubbo-3.3.6`。
- 基线 commit：`f1585880bee4ca7776f44380c47c994217721ffe`。
- 学习分支：`codex/learn-dubbo-source-3.3`，已经从稳定 tag 创建并切换。
- 学习 JDK：21。

## 3. 范围

本阶段只建立直连实验，不接入 Nacos，不分析复杂源码链路。

```text
Spring Boot 3
+ Dubbo Protocol
+ Hessian2
+ 单 Consumer
+ 单 Provider
+ 同步调用
+ 直连 URL
```

## 4. 核心问题

- 如何证明实验运行的是当前仓库源码，而不是外部 Dubbo 制品？
- 如何固定 JDK、Maven、Spring Boot 和 Dubbo 版本？
- 如何让一次请求在 Consumer 和 Provider 日志中准确关联？
- 如何在不依赖注册中心的情况下暴露和引用服务？
- 哪些配置必须显式声明，避免默认值掩盖学习重点？

## 5. 实验结构

```text
dubbo-demo/dubbo-demo-source-learning/
├── pom.xml
├── source-learning-api/
├── source-learning-provider/
└── source-learning-consumer/
```

业务契约：

```text
LearningService.greet(LearningRequest)
    → LearningResponse
```

请求至少携带 `requestId` 和 `name`；响应至少携带 `requestId`、`message` 和 `providerId`。

## 6. 实施任务

1. 记录分支、tag、commit、JDK 和 Maven 版本。
2. 创建三个学习实验模块，并接入根构建的合适位置。
3. 配置 Provider 使用 `dubbo`、固定端口和 `hessian2`。
4. 配置 Consumer 使用直连 URL，设置明确 timeout 和 `retries=0`。
5. Provider 和 Consumer 日志打印 request ID、线程、地址和关键参数。
6. 编写构建、启动、停止和调用说明。
7. 连续运行多次请求并保存预期日志样例。
8. 记录第一次完整调用栈，暂不解释所有内部细节。

## 7. 计划图示

- 实验进程与网络拓扑图。
- 三模块依赖图。
- 一次请求的最小交互图。

## 8. 交付物

- `00-baseline-and-learning-guide.md`。
- 可运行的 API、Provider 和 Consumer。
- 基线环境清单。
- 构建与运行命令。
- 成功调用日志与第一份调用栈。

## 9. G0 验收清单

- [ ] 当前工作基于指定 tag 和学习分支。
- [ ] JDK 21 与 Maven 信息已记录。
- [ ] 实验使用当前仓库构建产物。
- [ ] Provider 和 Consumer 可以独立启动、停止。
- [ ] 连续调用结果稳定，request ID 可以关联两端日志。
- [ ] 协议为 Dubbo Protocol，序列化为 Hessian2。
- [ ] 直连场景不依赖任何注册中心。
- [ ] 新开发者按照文档可以独立复现。

## 10. 向阶段 1、2 交接

- 稳定源码和运行环境。
- 可设置断点的最小 RPC 实验。
- 一份未经解释的真实调用栈，供后续建立模块地图和 RPC 全景。
