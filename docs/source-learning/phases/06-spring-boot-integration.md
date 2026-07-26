# 阶段 6：Spring Boot 3 集成与生命周期

> 前置阶段：阶段 5
> 质量门禁：G6

## 1. 阶段目的

解释 Spring Boot 3 如何加载 Dubbo、绑定配置、扫描注解、创建 ServiceBean 和 ReferenceBean，并通过 Spring 生命周期驱动 DubboBootstrap。

## 2. 核心问题

- Dubbo AutoConfiguration 在哪些条件下生效？
- YAML 和环境属性如何转换成 Dubbo Config 对象？
- `@DubboService` 如何形成 ServiceBean，并在何时触发暴露？
- `@DubboReference` 在 Bean 生命周期哪个阶段注入代理？
- Spring ApplicationContext 与 Dubbo ApplicationModel、ModuleModel 如何关联？
- Spring 事件如何驱动 DubboBootstrap 启动和停止？
- Spring Boot 3 与旧 Spring 集成代码的边界在哪里？

## 3. 重点源码

- `DubboAutoConfiguration`
- `DubboSpringBoot3DependencyCheckAutoConfiguration`
- `ServiceAnnotationPostProcessor`
- `ReferenceAnnotationBeanPostProcessor`
- `ServiceBean`
- `ReferenceBean`
- `DubboBootstrapApplicationListener`
- `DubboBootstrap`
- Spring 6 相关配置模块。

## 4. 实施任务

1. 从 Boot 自动配置导入文件定位生效入口。
2. 记录配置属性源、绑定结果和优先级。
3. 跟踪 `@DubboService` 扫描、BeanDefinition 注册和 ServiceBean 暴露。
4. 跟踪 `@DubboReference` 字段处理、ReferenceBean 创建和代理注入。
5. 记录 Spring 刷新事件与 DubboBootstrap 状态变化。
6. 关闭应用并记录销毁事件与资源释放。
7. 将 Spring 层对象映射到阶段 5 的 Dubbo 核心对象。

## 5. 实验

- YAML 配置正常启动。
- 使用环境变量覆盖一项 Dubbo 配置。
- 同时存在全局与应用级配置，观察优先级。
- 删除必要配置，观察自动配置条件和错误信息。
- 正常关闭应用，验证 Exporter、Invoker、Client 和 Server 释放。

## 6. 计划图示

- Spring Boot 自动配置加载图。
- 注解 → BeanDefinition → ServiceBean/ReferenceBean 图。
- Spring Bean 与 Dubbo 运行时对象关系图。
- 配置源与优先级图。
- 启动和关闭生命周期时序图。

## 7. 交付物

- `06-spring-boot-3-integration.md`。
- 自动配置条件和属性绑定表。
- 注解处理调用栈。
- Spring/Dubbo 生命周期图。
- Spring Boot 3 专用断点清单。

## 8. G6 验收清单

- [ ] 能从 AutoConfiguration 追踪到 DubboBootstrap。
- [ ] 能从 `@DubboService` 追踪到 Exporter。
- [ ] 能从 `@DubboReference` 追踪到 Proxy。
- [ ] 能解释主要配置源和优先级。
- [ ] 能说明 Spring 与 Dubbo 两套生命周期如何衔接。
- [ ] 能用关闭实验证明资源释放顺序。

## 9. 向阶段 7 交接

- 外部配置进入 Dubbo Config 的时机和优先级。
- Spring 启动过程中注册中心、配置中心和元数据中心的初始化位置。
- Provider 暴露和 Consumer 引用的 Spring 入口。
