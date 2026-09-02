# Sosrpc 学习日志 v0.8.0

## 基本信息

- 项目名称：Sosrpc
- 当前版本：v0.8.0
- 日志类型：阶段学习记录
- 更新时间：2026-08-24 13:21:25 +08:00
- 学习阶段：容错机制与服务保护
- 前置版本：v0.7.0 负载均衡；进入本阶段前已完成重试机制
- 面向目标：秋招项目梳理、RPC 可靠性设计、服务治理能力沉淀
- 参考资料：教学文档 10《容错机制》

## 本阶段目标

此前项目已经完成注册中心、服务发现、负载均衡、自定义协议、TCP 调用和重试机制。consumer 可以发现多个 provider、选择一个节点并在调用失败后执行重试。

但是重试只能解决部分瞬时故障。如果服务已经宕机、节点持续不可用或注册中心返回了失效地址，无论重试多少次都可能失败。因此本阶段重点解决：

```text
重试耗尽后，框架应该怎样结束这次调用？
系统压力过大或下游持续故障时，怎样主动保护调用链？
```

本阶段完成的核心内容包括：

1. 抽象统一的 `TolerantStrategy` 容错策略接口。
2. 实现 Fail-Fast、Fail-Safe、Fail-Back、Fail-Over 四种策略。
3. 补完教学文档中两个 `return null`：本地降级和故障转移。
4. 使用 SPI 和工厂模式实现容错策略配置与扩展。
5. 在 `ServiceProxy` 中建立“重试耗尽后进入容错”的调用链。
6. 设计容错上下文，使不同策略能拿到请求、节点列表、返回类型等信息。
7. 额外实现令牌桶限流和三态熔断器。
8. 让限流、熔断和最终容错策略能够组合使用。
9. 编写容错、限流和熔断单元测试，并完成 Fail-Over 实际联调。

## 容错、重试、限流和熔断的职责边界

本阶段最重要的理解是：这些机制都属于广义的故障治理或服务韧性体系，但执行阶段不同。

```text
请求进入 ServiceProxy
  -> 限流判断：当前请求是否允许进入
  -> 熔断判断：当前服务是否允许继续调用
  -> 服务发现
  -> 负载均衡选择节点
  -> TCP 请求
  -> 重试策略处理临时失败
  -> 重试仍失败
  -> Fail-Fast / Fail-Safe / Fail-Back / Fail-Over
```

具体职责如下：

- 重试：调用失败后是否再次尝试。
- 限流：调用前控制进入系统的请求速率。
- 熔断：根据历史失败情况暂时阻止对故障服务的调用。
- 最终容错策略：所有正常调用和重试都失败后，决定如何收尾。

因此当前包结构设计为：

```text
fault
  retry
  tolerant
  ratelimiter
  circuitbreaker
```

它们都在 `fault` 总包中，但限流和熔断没有放进 `fault.tolerant`，因为二者发生在远程调用之前，不是失败结果处理策略。

## 项目实际代码结构

```text
rpc-core/src/main/java/com/achingsoul/myrpc/fault
  tolerant
    TolerantStrategy.java
    TolerantStrategyKeys.java
    TolerantStrategyFactory.java
    TolerantStrategyContextKeys.java
    RpcRequestExecutor.java
    FailFastTolerantStrategy.java
    FailSafeTolerantStrategy.java
    FailBackTolerantStrategy.java
    FailOverTolerantStrategy.java

  ratelimiter
    RateLimiter.java
    RateLimiterFactory.java
    RateLimitException.java
    TokenBucketRateLimiter.java

  circuitbreaker
    CircuitBreaker.java
    CircuitBreakerFactory.java
    CircuitBreakerState.java
    CircuitBreakerOpenException.java
    SimpleCircuitBreaker.java

rpc-core/src/test/java/com/achingsoul/myrpc/fault
  tolerant/TolerantStrategyTest.java
  FaultProtectionTest.java

example-consumer/src/main/java/com/achingsoul/example/consumer
  UserServiceFallback.java
  FaultProtectionConsumerExample.java
```

## 容错策略接口

容错策略统一实现以下接口：

```java
public interface TolerantStrategy {

    RpcResponse doTolerant(Map<String, Object> context, Exception e);
}
```

接口接收两个参数：

1. `context`：容错上下文，用于传递策略执行所需的信息。
2. `e`：触发容错的原始异常。

返回值仍然是 `RpcResponse`，因此容错结果可以无缝回到动态代理原有的响应处理逻辑。

这里使用 `Map<String, Object>` 是一种教学阶段的灵活设计。不同策略需要的数据不一样，如果把所有字段写死到方法参数中，接口会迅速膨胀。它的代价是编译期类型安全较弱，生产化时可以进一步封装为强类型 `TolerantContext`。

## 容错上下文设计

项目使用 `TolerantStrategyContextKeys` 统一维护上下文 key，当前包括：

```text
RPC_REQUEST
SERVICE_META_INFO_LIST
SELECTED_SERVICE_META_INFO
REQUEST_PARAMS
LOAD_BALANCER
METHOD_RETURN_TYPE
FALLBACK_TASK
RPC_REQUEST_EXECUTOR
```

这些上下文数据分别支持不同策略：

- Fail-Safe 需要方法返回类型，才能为基本类型返回安全默认值。
- Fail-Back 需要本地降级任务。
- Fail-Over 需要原始请求、全部节点、失败节点、负载均衡器和请求执行器。
- 测试通过注入 `RPC_REQUEST_EXECUTOR` 替代真实 TCP 调用，避免单元测试依赖网络。

## 四种容错策略

### 1. Fail-Fast 快速失败

Fail-Fast 不吞掉异常，也不尝试构造成功结果，而是立即向调用方抛出：

```java
throw new RuntimeException("RPC service failed", e);
```

适用场景：

- 核心业务不能接受错误结果。
- 上层调用方具备统一异常处理能力。
- 希望快速暴露问题，不允许静默降级。

测试时需要注意：如果 Debug 停在 `catch` 内、还没有真正执行 `doTolerant`，控制台不会提前出现 `RPC service failed`。只有执行到 Fail-Fast 的抛异常语句后，异常信息才会输出。

### 2. Fail-Safe 静默处理

Fail-Safe 记录异常，并返回一个可用的 `RpcResponse`。

当前实现不是简单 `new RpcResponse()`，而是根据方法返回类型生成默认值，例如：

```text
short / int / long -> 0
boolean            -> false
引用类型            -> null
```

这样可以避免代理方法返回基本类型时因为 `null` 自动拆箱触发 `NullPointerException`。

适用场景：

- 非核心功能失败不应影响主链路。
- 日志、埋点、推荐等允许丢失或返回空结果。
- 调用方明确接受默认值语义。

### 3. Fail-Back 本地降级

教学文档中的 Fail-Back 只保留了：

```java
// todo 可自行扩展，获取降级的服务并调用
return null;
```

项目中已经完整实现：

1. `RpcConfig.fallbackClass` 指定本地降级实现类。
2. `ServiceProxy` 根据当前调用方法构造 `Callable<RpcResponse>`。
3. 容错上下文通过 `FALLBACK_TASK` 传入该任务。
4. `FailBackTolerantStrategy` 执行本地实现，并把结果包装为 `RpcResponse`。
5. 未配置本地实现时，返回类型安全的默认值。

示例降级实现 `UserServiceFallback`：

```text
getUser  -> 返回 fallback-user
getNumber -> 返回 -1
```

当前实现还会验证降级类必须实现原服务接口，避免配置了不兼容类型后在运行时产生难以理解的反射错误。

### 4. Fail-Over 故障转移

教学文档中的 Fail-Over 同样只给出了 `return null`。项目中的完整流程是：

```text
第一次选中 provider A
  -> 请求失败并且重试耗尽
  -> 从节点列表中过滤 provider A
  -> 对剩余节点再次执行负载均衡
  -> 选中 provider B
  -> 重新发送原 RpcRequest
```

节点过滤使用 `ServiceMetaInfo.getServiceNodeKey()`，避免再次选择刚刚失败的同一个节点。

如果没有剩余节点，则抛出：

```text
No alternative service node for fail-over
```

这体现了故障转移的前提：注册中心必须至少返回两个不同的 provider。

## SPI 与配置扩展

四种策略通过 SPI 注册：

```properties
failBack=com.achingsoul.sosrpc.fault.tolerant.FailBackTolerantStrategy
failFast=com.achingsoul.sosrpc.fault.tolerant.FailFastTolerantStrategy
failOver=com.achingsoul.sosrpc.fault.tolerant.FailOverTolerantStrategy
failSafe=com.achingsoul.sosrpc.fault.tolerant.FailSafeTolerantStrategy
```

`TolerantStrategyFactory` 根据配置 key 获取实例。consumer 可以通过以下配置切换：

```properties
rpc.tolerantStrategy=failFast
rpc.tolerantStrategy=failSafe
rpc.tolerantStrategy=failBack
rpc.tolerantStrategy=failOver
```

如果新增一种策略，只需要：

1. 实现 `TolerantStrategy`。
2. 增加策略 key。
3. 在 SPI 文件中注册映射。
4. 修改 consumer 配置。

主调用链不需要硬编码新的 `if/else`。

## ServiceProxy 中的真实调用链

当前 `ServiceProxy` 把多种治理能力组合到一次调用中：

```text
构造 RpcRequest
  -> 获取 RpcConfig
  -> 获取 LoadBalancer
  -> 获取 RateLimiter 和 CircuitBreaker
  -> 限流判断
  -> 熔断判断
  -> Registry.serviceDiscovery
  -> LoadBalancer.select
  -> RetryStrategy.doRetry
  -> TCP 调用成功：recordSuccess
  -> 调用失败：recordFailure
  -> TolerantStrategy.doTolerant
  -> 返回 rpcResponse.data
```

重试和容错采用“先重试，再最终容错”的顺序：

```text
瞬时网络抖动 -> 重试尝试恢复
持续故障     -> 最终容错收尾
```

限流异常和已经打开的熔断器拒绝，不会再次计入熔断失败次数，因为它们是保护机制的结果，不是新的远程调用失败。

## 令牌桶限流扩展

教学文档将限流列为可扩展方向。本项目实现了 `TokenBucketRateLimiter`：

```text
桶容量 capacity
可用令牌 availableTokens
每秒补充速率 refillTokensPerSecond
每次请求消耗 1 个令牌
令牌不足时拒绝请求
```

令牌补充根据 `System.nanoTime()` 计算经过时间，并允许使用小数令牌，使低速率配置也能平滑恢复。

`allowRequest()` 使用 `synchronized` 保证单实例并发安全。`RateLimiterFactory` 按服务和配置缓存实例，使令牌桶不会在每次 RPC 调用时重新创建。

相关配置：

```properties
rpc.rateLimiterEnabled=false
rpc.rateLimitCapacity=10
rpc.rateLimitRefillRate=10.0
```

限流被触发后会抛出 `RateLimitException`，随后仍然可以交给 Fail-Fast、Fail-Safe 或 Fail-Back 决定最终结果。

## 三态熔断器扩展

项目实现的熔断器包含三种状态：

```text
CLOSED
  -> 正常放行请求
  -> 连续失败达到阈值
OPEN
  -> 在窗口期内快速拒绝请求
  -> 等待 openDuration 后
HALF_OPEN
  -> 只允许一个探测请求
  -> 探测成功达到阈值：回到 CLOSED
  -> 探测失败：重新 OPEN
```

相关配置：

```properties
rpc.circuitBreakerEnabled=false
rpc.circuitBreakerFailureThreshold=3
rpc.circuitBreakerOpenDuration=5000
rpc.circuitBreakerHalfOpenSuccessThreshold=1
```

`SimpleCircuitBreaker` 使用同步方法保护状态、失败计数和半开探测标记。`CircuitBreakerFactory` 按服务和配置缓存熔断器，使连续请求能够共享失败历史。

## 单元测试结果

容错策略测试：

```text
TolerantStrategyTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

覆盖内容：

1. Fail-Fast 会抛出异常。
2. Fail-Safe 为 `short` 返回 `0`。
3. Fail-Back 会执行本地 fallback task。
4. Fail-Over 会过滤失败节点并调用另一个节点。
5. SPI 工厂能够加载全部四种策略。

限流和熔断测试：

```text
FaultProtectionTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

覆盖内容：

- 容量为 2 的令牌桶放行前两次请求并拒绝第三次。
- 熔断器在连续失败两次后进入 OPEN。
- OPEN 窗口结束后进入 HALF_OPEN。
- 探测成功后恢复 CLOSED。

本阶段相关容错测试合计：

```text
7 tests passed
```

加上此前重试测试：

```text
RetryStrategyTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

重试、容错、限流和熔断针对性测试共 11 个，全部通过。

## Fail-Over 实际联调结果

实际联调环境中启动了两个 provider：

```text
http://localhost:8080
http://localhost:8085
```

第一次请求选中 `8080` 后停止该 provider，consumer 最终输出：

```text
故障转移：http://localhost:8080 -> http://localhost:8085
```

之后调用成功返回用户 `achingsoul`，`getNumber()` 返回 `1`。

这说明实际链路已经完成：

```text
选中 8080
  -> 调用失败
  -> 进入 Fail-Over
  -> 过滤 8080
  -> 选择 8085
  -> TCP 调用成功
```

## 限流和熔断测试方法

### 限流测试

建议 VM options：

```text
-Drpc.rateLimiterEnabled=true
-Drpc.rateLimitCapacity=1
-Drpc.rateLimitRefillRate=0.2
-Drpc.circuitBreakerEnabled=false
-Drpc.retryStrategy=no
-Drpc.tolerantStrategy=failBack
```

预期现象：第一个请求正常调用；后续令牌不足时触发限流，并由 Fail-Back 返回降级结果；约 5 秒补充一个令牌后可再次调用 provider。

### 熔断测试

建议 VM options：

```text
-Drpc.rateLimiterEnabled=false
-Drpc.circuitBreakerEnabled=true
-Drpc.circuitBreakerFailureThreshold=2
-Drpc.circuitBreakerOpenDuration=5000
-Drpc.circuitBreakerHalfOpenSuccessThreshold=1
-Drpc.retryStrategy=no
-Drpc.tolerantStrategy=failBack
```

测试步骤：

1. 启动 provider 和 consumer。
2. 停止 provider，连续触发两次失败。
3. 观察熔断器进入 OPEN，后续请求不再发起网络调用。
4. 在 5 秒窗口结束前重新启动 provider。
5. 下一次请求作为 HALF_OPEN 探测请求。
6. 探测成功后观察熔断器恢复 CLOSED。

限流和熔断当前已经通过单元测试，真实联调可按以上步骤继续完成。

## 本阶段问题复盘

### 1. 为什么看不到 `RPC service failed`

Fail-Fast 的异常信息只有在真正执行 `doTolerant` 并抛出异常后才会出现。Debug 停在 `catch` 内部时，程序只是已经捕获了前面的网络异常，还没有执行快速失败策略。

### 2. 容错策略在哪里切换

容错策略由 consumer 配置决定：

```properties
rpc.tolerantStrategy=failOver
```

修改 provider 配置不会改变 consumer 的失败处理方式。

### 3. Fail-Over 测试必须有两个 provider

如果注册中心中只有一个节点，过滤失败节点后列表为空，故障转移无法成立。Fail-Over 不是凭空恢复服务，而是在已有候选节点之间切换。

### 4. Debug 暂停可能触发 Vert.x blocked thread 告警

在 Vert.x 线程断点暂停较久时，控制台可能出现 blocked thread warning。这通常是调试器冻结线程导致，不代表正常运行时一定发生性能问题。测试业务逻辑时应尽量缩短事件循环线程上的断点停留时间。

### 5. 限流和熔断不是互斥的最终容错策略

限流、熔断负责决定“要不要发请求”，Fail-Back 等负责决定“拒绝或失败后返回什么”。因此可以组合：

```text
熔断拒绝 + Fail-Back 本地降级
限流拒绝 + Fail-Safe 默认值
```

这种组合能力比把所有机制都做成互斥的 `tolerantStrategy` 更灵活。

## 秋招表达版本

### 30 秒版本

> 我在 RPC 框架的重试机制之后增加了可插拔容错层，抽象了 `TolerantStrategy`，实现 Fail-Fast、Fail-Safe、Fail-Back 和 Fail-Over 四种策略，并通过 SPI 和配置动态切换。其中 Fail-Back 支持调用本地降级实现，Fail-Over 会从服务列表中过滤失败节点后重新负载均衡。除此之外，我还在调用前增加了令牌桶限流和 CLOSED、OPEN、HALF_OPEN 三态熔断器，使流量保护、故障隔离和最终降级可以组合使用。

### 深入追问版本

如果面试官问“重试和故障转移有什么区别”，可以回答：

> 普通重试可能仍然请求同一个节点，适合网络抖动等瞬时故障；故障转移会显式排除本次失败节点，再从剩余 provider 中选择新节点。我的调用链是先通过重试解决短暂问题，重试耗尽后再进入可配置容错策略。

如果面试官问“为什么熔断器需要 HALF_OPEN”，可以回答：

> 如果 OPEN 状态永远拒绝请求，框架无法知道服务是否恢复；如果时间一到直接 CLOSED，又可能瞬间放入大量请求。HALF_OPEN 只放少量探测请求，成功后恢复，失败则重新熔断，用较小风险判断下游状态。

## 当前能力边界

当前实现已经适合教学和项目表达，但距离生产级仍有边界：

- 容错上下文使用 `Map<String, Object>`，缺少编译期类型安全。
- Fail-Over 当前只额外尝试一个替代节点，没有遍历全部节点或设置总超时预算。
- 本地降级类通过反射创建，尚未接入依赖注入容器。
- 限流器和熔断器状态只保存在当前 consumer JVM，不支持分布式共享。
- 熔断统计使用连续失败次数，尚未支持滑动窗口、失败率和慢调用比例。
- 限流只实现单机令牌桶，尚未支持 Redis/Lua 等分布式限流。
- 缺少统一指标、告警和管理接口。
- 尚未实现 RPC 级超时预算与取消传播。

## 后续可以扩展的方向

1. 将 `Map` 上下文重构为强类型 `TolerantContext`。
2. 为 Fail-Over 增加最大转移次数和全链路超时预算。
3. 将 fallback 对象交给 Spring 容器管理。
4. 引入基于时间窗口的失败率熔断。
5. 增加慢调用统计和并发隔离。
6. 对外暴露限流、熔断状态和指标。
7. 增加超时控制，并区分连接超时、读取超时和总调用超时。
8. 为限流、熔断和降级补充真实端到端测试。

## 本阶段总结

v0.8.0 让 Sosrpc 从“调用失败后只能抛错或机械重试”演进为“能够根据故障类型和业务要求选择处理方式”。

项目已经形成较清晰的可靠性链路：

```text
限流控制入口流量
  -> 熔断隔离持续故障
  -> 重试恢复瞬时异常
  -> Fail-Over 切换节点
  -> Fail-Back 提供本地降级
  -> Fail-Safe 或 Fail-Fast 决定最终结果
```

本阶段不仅完成了教学文档中的 Fail-Fast、Fail-Safe、SPI 和配置接入，还补完了文档留给读者的 Fail-Back、Fail-Over，并继续实现了限流和熔断扩展。更重要的是，已经理解这些机制在调用链中的不同位置以及它们之间的组合关系。
