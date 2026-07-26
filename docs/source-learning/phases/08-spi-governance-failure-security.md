# 阶段 8：SPI、集群治理、异常与安全

> 前置阶段：阶段 3、4、7
> 质量门禁：G8

## 1. 阶段目的

回到前面已经观察到的真实扩展点，系统解释 SPI、Adaptive、Activate 和 Wrapper；再通过动态治理与异常实验验证框架在变化和失败条件下的行为。

## 2. SPI 核心问题

- ExtensionLoader 如何发现、创建、缓存和销毁扩展？
- Adaptive Extension 如何读取 URL 并选择实现？
- Protocol Wrapper 为什么能透明插入 Filter 和 Listener？
- Activate 如何决定 Filter、Router、Listener 的启用和顺序？
- ScopeModel 如何决定扩展实例的作用域？
- 自动生成类与手写实现如何在调用栈中区分？

重点源码：

- `ExtensionLoader`
- Adaptive Extension 生成和加载流程。
- `ProtocolFilterWrapper`
- `ProtocolListenerWrapper`
- `DefaultFilterChainBuilder`
- `RouterChain`
- LoadBalance 与 Cluster 扩展入口。

## 3. 治理与容错问题

- Filter、Router、LoadBalance、Cluster 的执行次序是什么？
- 动态路由规则如何进入 RouterChain？
- LoadBalance 如何选择和重选 Invoker？
- Failover 如何计算重试次数与剩余超时预算？
- 业务异常、网络异常、超时和序列化异常如何分类？
- 地址列表为空、路由为空和 Provider 不可用有何区别？

## 4. 异常实验矩阵

| 类别 | 实验 | 预期观察 |
|---|---|---|
| 地址 | Provider 未启动、动态下线 | 无 Provider 与地址刷新行为 |
| 注册中心 | Nacos 中断、恢复 | 本地缓存、重连与补偿 |
| 路由 | 路由结果为空 | Router 与 Cluster 异常边界 |
| 网络 | 连接断开 | RemotingException 与重试 |
| 超时 | Provider 延迟 | timeout 计算、Future 超时与 Failover |
| 业务 | Provider 抛业务异常 | Biz 异常封装与不重试边界 |
| 序列化 | 不可信或不兼容类型 | Hessian2 类检查和异常传播 |
| 配置 | 动态修改治理规则 | 监听、解析、生效与回滚 |

## 5. 安全边界

- Hessian2 黑白名单、类检查和自动信任。
- 反序列化输入的信任边界。
- 注册中心、配置中心和元数据中心的信任假设。
- Token、认证和传输加密能力的边界。
- 将结论与 `docs/source-learning/threat-model.md` 的安全属性、非目标和下游责任对应。
- 明确区分有效漏洞、可信输入误用、被声明不提供的安全属性和已知非问题。

## 6. 实施任务

1. 从 Protocol、Filter、Router、LoadBalance 的真实使用点反查 SPI。
2. 保存 Adaptive 类和 Wrapper 链的运行类型。
3. 记录 Activate 扩展列表、排序和实际执行顺序。
4. 执行异常矩阵，每次只改变一个变量。
5. 保存异常类型、错误码、调用栈、重试次数和最终结果。
6. 绘制动态规则进入调用链的路径。
7. 对照 threat model 编写安全边界和常见误判说明。

## 7. 计划图示

- ExtensionLoader 创建与缓存图。
- Adaptive Extension 决策图。
- Protocol Wrapper 和 Filter 链包装图。
- Router/LoadBalance/Cluster 执行图。
- 超时、重试和异常传播图。
- 安全信任边界图。

## 8. 交付物

- `08-spi-governance-failure-security.md`。
- SPI 扩展索引和实际对象链。
- 动态治理事件链。
- 异常实验矩阵与结果。
- 安全边界和常见误判说明。
- 故障专用断点手册。

## 9. G8 验收清单

- [ ] 能从 URL 参数解释 Adaptive 实现选择。
- [ ] 能说明 Activate 扩展列表和执行顺序。
- [ ] 能区分 Filter、Router、LoadBalance、Cluster 的职责。
- [ ] 能预测主要异常是否重试以及在哪里转译。
- [ ] 异常矩阵有真实运行证据，而不是仅阅读 catch 分支。
- [ ] 安全结论与 threat model 一致，并明确下游责任。

## 10. 向阶段 9 交接

- 完整扩展索引。
- 动态治理和异常矩阵。
- 安全边界与误判清单。
- 已验证的排查入口、断点和错误传播路径。
