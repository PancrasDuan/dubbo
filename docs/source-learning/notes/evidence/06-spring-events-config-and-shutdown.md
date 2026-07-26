# 阶段 6 证据：自动配置、属性覆盖与关闭事件

> 主笔记：[Spring Boot 3 集成与生命周期](../06-spring-boot-3-integration.md)
> 演示模块：`dubbo-demo/dubbo-demo-source-learning`
> 聚焦测试：`DubboAutoConfigurationOnSingleConfigTest`、`DubboAutoConfigurationOnMultipleConfigTest`
> 实验日期：2026-07-26

## 1. 实验边界

本阶段故意分成两类证据：

| 证据 | 入口 | 能证明什么 |
|---|---|---|
| 自动配置聚焦测试 | `dubbo-spring-boot-autoconfigure` | `dubbo.*` 单/多配置可经 Boot Binder 形成 Config Bean |
| 学习 Demo 运行 | 显式 `@EnableDubbo` + 程序化 Config Bean | Spring 6 注解处理、Module 启停、属性优先级和资源释放 |

学习 Demo 的 POM 没有 `dubbo-spring-boot-starter`，运行日志不能作为 `DubboAutoConfiguration` 已加载的证据。

## 2. 自动配置导入清单

通用 Boot 自动配置文件：

```text
dubbo-spring-boot-project/
  dubbo-spring-boot-autoconfigure/
    src/main/resources/META-INF/spring/
      org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

其中与主线直接相关的条目为：

```text
org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration
org.apache.dubbo.spring.boot.autoconfigure.DubboListenerAutoConfiguration
org.apache.dubbo.spring.boot.autoconfigure.DubboRelaxedBinding2AutoConfiguration
```

Boot 3 专用模块的同名导入文件只包含：

```text
org.apache.dubbo.spring.boot.autoconfigure.DubboTriple3AutoConfiguration
```

源码全仓搜索没有 `DubboSpringBoot3DependencyCheckAutoConfiguration`。因此阶段方案中该类名不适用于 3.3.6 基线，实际阅读对象已更正为通用自动配置与 Spring 6 兼容选择器。

## 3. 聚焦测试

### 3.1 首次单模块尝试

直接执行目标模块失败于依赖解析：

```text
Could not find artifact
org.apache.dubbo:dubbo-test-check:jar:3.3.6
```

这是没有把仓库内 `dubbo-test-check` 加入 Reactor 的构建选择问题，测试尚未开始，不属于产品代码或断言失败。

### 3.2 正确命令

```text
JAVA_HOME=/Users/pancras/Office/jdk-21.0.3.jdk/Contents/Home \
mvn -pl dubbo-spring-boot-project/dubbo-spring-boot-autoconfigure -am \
  -Dtest=DubboAutoConfigurationOnSingleConfigTest,DubboAutoConfigurationOnMultipleConfigTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`-am` 把目标模块所需的仓库内依赖放入同一 Reactor。执行结果：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
  DubboAutoConfigurationOnSingleConfigTest

Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
  DubboAutoConfigurationOnMultipleConfigTest

Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 55.988 s
```

79 个 Reactor 项目全部为 `SUCCESS`，目标模块耗时 `5.640 s`。

### 3.3 单配置断言覆盖

`DubboAutoConfigurationOnSingleConfigTest` 启动 Spring Boot 上下文并验证：

| Config | 绑定值举例 |
|---|---|
| Application | `dubbo-demo-single-application` |
| Module | 单模块配置值 |
| Registry | address 等单注册中心属性 |
| Protocol | protocol name、port `20880` |
| Monitor | monitor 属性 |
| Provider | host 等 Provider 默认值 |
| Consumer | client 等 Consumer 默认值 |

测试结束后日志显示 Module、Application、Framework、global resources 依次销毁，说明测试上下文没有遗留默认模型。

### 3.4 多配置断言覆盖

`DubboAutoConfigurationOnMultipleConfigTest` 设置 `dubbo.config.multiple=true`，用复数前缀绑定带 ID 的对象：

```text
dubbo.applications.application1.*
dubbo.modules.module1.*
dubbo.registries.registry1.*
dubbo.protocols.protocol1.*
```

测试还覆盖 monitor、provider、consumer 等多配置 Map。通过结果证明不是把复数配置覆盖到一个默认 Bean，而是按 ID 形成独立 Config Bean。

## 4. Demo 的显式 Spring 入口

Provider 启动日志：

```text
Use default application: Dubbo Application[1.1](unknown)
Use default module model of target application: Dubbo Module[1.1.1]
Bind Dubbo Module[1.1.1] to spring container: DefaultListableBeanFactory@...
```

随后 Spring 6 AOT 处理器扫描：

```text
ServiceAnnotationWithAotPostProcessor
Found 1 classes annotated by Dubbo @Service:
  org.apache.dubbo.sourcelearning.provider.LearningServiceImpl

Register ServiceBean[
  ServiceBean:org.apache.dubbo.sourcelearning.api.LearningService::
]
```

Config 提交点：

```text
DubboConfigBeanInitializer - loading dubbo config beans ...
DubboConfigBeanInitializer - dubbo config beans are loaded.
```

这些日志顺序对应：绑定 Module → 扫描注解/注册 BeanDefinition → 实例化并装入 ConfigManager。

## 5. 三层属性覆盖实验

### 5.1 输入

YAML 已包含：

```yaml
learning:
  provider-port: 20880
  provider-id: provider-${learning.provider-port}
```

启动时同时设置环境变量与命令行参数：

```text
LEARNING_PROVIDER_PORT=20882 \
java -jar target/source-learning-provider-3.3.6.jar \
  --learning.provider-port=20883 \
  --learning.provider-id=priority-cli
```

### 5.2 生效结果

ConfigManager 打印：

```text
<dubbo:protocol
  preferSerialization="hessian2"
  serialization="hessian2"
  port="20883"
  name="dubbo"
  id="protocolConfig" />
```

业务服务 URL：

```text
dubbo://10.220.80.142:20883/
  org.apache.dubbo.sourcelearning.api.LearningService
  ?bind.port=20883
```

网络层：

```text
Start NettyServer bind /0.0.0.0:20883,
export /10.220.80.142:20883
```

业务 `LearningService` 与内部 `MetadataService` 都使用 `20883`，Server 仍只启动一次。结论为：

```text
CLI 20883 > env 20882 > YAML 20880
```

### 5.3 结论适用范围

这里的 key 是 Demo 自定义的 `learning.provider-port`，通过 `@Value` 注入 `ProtocolConfig`。它直接验证 Spring `Environment` 优先级；Starter 的 `dubbo.protocol.port` 也读取同一 Environment，但由 `BinderDubboConfigBinder` 完成对象绑定。

## 6. 启动状态证据

启动阶段日志顺序：

```text
Dubbo Module[1.1.1] is starting
Dubbo Application[1.1](source-learning-provider) is starting
Export LearningService to injvm://...
Export LearningService to dubbo://...:20883/...
Start NettyServer ...:20883
Export MetadataService ...:20883/...
Dubbo Module[1.1.1] has started
Dubbo Application[1.1] is ready
Dubbo Module[1.1.1] has completed
Dubbo Application[1.1] has completed
Started ProviderApplication
```

这组运行结果能区分四个词：

| 日志状态 | 含义 |
|---|---|
| initialized | 配置与扩展初始化完成 |
| starting | 正在导出/引用服务 |
| started | Module 核心启动动作完成 |
| completed / ready | 引用检查、回调和应用就绪条件完成 |

## 7. 正常关闭事件

发送一次 `SIGINT`，Java 进程最终返回 `130`；这是终端中断的预期退出码，不是关闭失败。

### 7.1 两个 Hook 的协调

同一毫秒先出现：

```text
[DubboShutdownHook]
Run shutdown hook now.
Waiting for modules(Dubbo Application[1.1](source-learning-provider))
managed by Spring to be shutdown.

[SpringApplicationShutdownHook]
Dubbo Module[1.1.1] is stopping.
```

关键证据是 `Waiting for modules ... managed by Spring`：Dubbo Hook 没有抢先销毁 Spring 管理的 Module。

### 7.2 网络与服务资源

Spring Hook 随后打印：

```text
Destroying protocol [DubboProtocol]
Closing dubbo server: /10.220.80.142:20883
Close NettyServer bind /0.0.0.0:20883
```

约 2.08 秒后：

```text
Unexport service: dubbo://...:20883/org.apache.dubbo.metadata.MetadataService
Unexport service: injvm://127.0.0.1/...MetadataService
```

这个等待来自 Dubbo 的优雅停机流程；日志时间不能解释为线程卡死。

### 7.3 模型与外围资源

后续顺序：

```text
internal Dubbo Module stopping → stopped
Close all registries []
destroying application executor repository
Application has stopped
Framework is destroying
internal Application/Module stopping → stopped
Destroying global resources
Dubbo is completely destroyed
destroying framework executor repository
Unbind Dubbo Module[1.1.1] from spring container
ReferenceAnnotationWithAotBeanPostProcessor was destroying
```

本实验使用 `RegistryConfig(address=N/A)`，所以 `Close all registries []` 中集合为空；它仍证明 ApplicationDeployer 执行了注册中心管理器的关闭步骤。阶段 7 接入 Nacos 后再验证真实 Registry/Config Center/Metadata Report Client 的释放。

## 8. 与阶段 5 证据拼接

阶段 5 已在一次真实 Provider/Consumer 调用中观察到：

```text
Consumer：unrefer → close client/channel → module/application stop
Provider：等待优雅停机 → close server → unexport services
```

本阶段补上 Spring 外层：

```text
ContextClosedEvent
→ DubboDeployApplicationListener
→ ModuleModel.destroy
→ 阶段 5 的 unexport/unrefer/protocol destroy
→ ApplicationModel / FrameworkModel destroy
→ 从 BeanFactory unbind Module
```

因此 G6 的“关闭应用验证 Exporter、Invoker、Client、Server 释放”由两阶段证据共同闭环，而不是仅靠一行 `Application stopped` 推断。

## 9. 复现实验检查表

- [x] 记录 Demo 与 Starter 两种入口的依赖差异。
- [x] 从 `AutoConfiguration.imports` 找到通用自动配置。
- [x] 通过单配置和多配置聚焦测试。
- [x] 同时设置 YAML、环境变量和 CLI，确认最终端口为 20883。
- [x] 正常启动并看到 `ServiceBean` 注册、Module/Application ready。
- [x] 正常关闭并看到 Hook 协调、Server 关闭、服务撤销、模型与执行器销毁。
- [x] 确认实验进程退出，没有遗留 20883 监听端口。
