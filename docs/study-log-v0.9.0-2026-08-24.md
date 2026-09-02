# Sosrpc 学习日志 v0.9.0

## 基本信息

- 项目名称：Sosrpc
- 当前版本：v0.9.0
- 日志类型：阶段学习记录
- 更新时间：2026-08-24 13:21:25 +08:00
- 学习阶段：启动机制与 Spring Boot 注解驱动
- 前置版本：v0.8.0 容错机制与服务保护
- 面向目标：秋招项目梳理、框架易用性设计、Spring 扩展点理解、Starter 工程实践
- 参考资料：教学文档 11《启动机制和注解驱动》

## 本阶段目标

前面几个阶段已经让 Sosrpc 具备配置、SPI、注册中心、服务发现、缓存、心跳、TCP 协议、负载均衡、重试和容错等功能，但使用框架仍然需要手写较多启动代码。

provider 原本需要手动完成：

```text
RpcApplication.init
  -> LocalRegistry.register
  -> 构造 ServiceMetaInfo
  -> 注册到 Etcd / ZooKeeper
  -> 启动 VertxTcpServer
```

consumer 也需要手动初始化框架并调用 `ServiceProxyFactory.getProxy`。

本阶段目标是优化框架易用性：

1. 为普通 Java 项目封装 provider 和 consumer 启动器。
2. 新增独立 Spring Boot Starter 模组。
3. 实现 `@EnableRpc`、`@RpcService`、`@RpcReference` 三个注解。
4. 使用 Spring 扩展点自动初始化 RPC 框架。
5. 自动注册 provider 服务。
6. 自动为 consumer 字段注入 RPC 动态代理。
7. 在现有 example-provider 和 example-consumer 中完成 Spring 联调。
8. 解决 Spring Boot 4 与 rpc-core 旧 Logback 的依赖冲突。

## 为什么要设计启动机制

框架不仅要“功能完整”，还要“容易使用”。如果每个 provider 都复制同一段注册中心和服务器初始化代码，会产生几个问题：

- 使用成本高，开发者必须了解框架内部细节。
- 相同代码散落在多个业务项目中，容易遗漏步骤。
- 框架初始化流程发生变化时，每个业务项目都要修改。
- provider 和 consumer 的职责容易混淆。

启动器的本质是把框架内部流程封装为稳定入口：

```text
业务项目声明“我要提供或消费什么服务”
框架负责“怎样初始化、注册、发现和启动”
```

## 普通 Java 启动机制

### ServiceRegisterInfo

`ServiceRegisterInfo<T>` 封装服务注册信息：

```java
private String serviceName;

private Class<? extends T> implClass;
```

这样 provider 可以先声明要发布的服务列表，再统一交给启动器处理。

### ProviderBootstrap

`ProviderBootstrap.init` 完成：

```text
RpcApplication.init
  -> 获取 RpcConfig 和 Registry
  -> 遍历 ServiceRegisterInfo
  -> 注册到 LocalRegistry
  -> 构造 ServiceMetaInfo
  -> 注册到远程注册中心
  -> 按配置端口启动 VertxTcpServer
```

provider 不再需要复制整段框架初始化代码。

### ConsumerBootstrap

consumer 不需要注册服务或启动 TCP Server，因此 `ConsumerBootstrap.init()` 只负责执行公共初始化：

```java
RpcApplication.init();
```

provider 和 consumer 分开设计，可以避免 consumer 错误地占用服务端口。

## Spring Initializr 目录差异

本阶段开始时已经通过 `spring.io` 创建了：

```text
sosrpc-spring-boot-starter
```

教学文档使用 `start.aliyun.com` 和 Spring Boot 2.6。二者只是 Initializr 服务和默认版本不同，不影响 Starter 的核心设计。

真正需要调整的是模块性质：Initializr 默认生成的是一个“可运行 Spring Boot 应用”，包含：

```text
Application 主类
application.properties
SpringBootTest 空测试
spring-boot-maven-plugin
```

而我们需要的是一个“被其他项目依赖的 Starter 库”。因此项目中进行了以下整理：

- 删除 Starter 自己的 `Application` 主类。
- 删除无实际配置意义的 `application.properties`。
- 移除重打包用的 `spring-boot-maven-plugin`。
- 引入 `rpc-core`。
- 将 Java 版本统一为 21。
- 在根工程 `pom.xml` 中加入 Starter 模组。
- 把源码整理到 `annotation` 和 `bootstrap` 子包。

整理后的核心结构：

```text
sosrpc-spring-boot-starter
  src/main/java/com/achingsoul/myrpc/springboot/starter
    annotation
      EnableRpc.java
      RpcService.java
      RpcReference.java
    bootstrap
      RpcInitBootstrap.java
      RpcProviderBootstrap.java
      RpcConsumerBootstrap.java
  src/test/java/com/achingsoul/myrpc/springboot/starter
    RpcAnnotationTest.java
```

## 三个核心注解

### 1. @EnableRpc

`@EnableRpc` 用在 Spring Boot 启动类上，用于启用 RPC 框架：

```java
@EnableRpc
@SpringBootApplication
public class SpringBootProviderApplication {
}
```

它包含：

```java
boolean needServer() default true;
```

provider 使用默认值 `true`，consumer 使用：

```java
@EnableRpc(needServer = false)
```

注解通过 `@Import` 注册三个启动处理器：

```text
RpcInitBootstrap
RpcProviderBootstrap
RpcConsumerBootstrap
```

只有显式添加 `@EnableRpc` 的 Spring 应用才会加载这些能力。

### 2. @RpcService

`@RpcService` 标注在 provider 的服务实现类上：

```java
@RpcService
public class UserServiceImpl implements UserService {
}
```

它同时被 `@Component` 标注，因此 Spring 组件扫描会把服务实现注册为 Bean。

注解支持：

```java
Class<?> interfaceClass() default void.class;

String serviceVersion() default RpcConstant.DEFAULT_SERVICE_VERSION;
```

没有显式指定接口时，框架默认使用实现类的第一个接口。

### 3. @RpcReference

`@RpcReference` 标注在 consumer 的字段上：

```java
@RpcReference
private UserService userService;
```

注解定义了接口、版本、负载均衡、重试、容错和 Mock 等属性。本阶段与教学文档保持一致，自动注入的核心是接口类型和 Mock 代理；其余策略仍主要由全局 `RpcConfig` 控制。

## 三个 Spring 启动处理器

### RpcInitBootstrap

`RpcInitBootstrap` 实现 `ImportBeanDefinitionRegistrar`。

Spring 处理 `@EnableRpc` 时，它会：

1. 从 `AnnotationMetadata` 读取 `needServer`。
2. 调用 `RpcApplication.init()` 初始化配置和注册中心。
3. provider 模式启动 `VertxTcpServer`。
4. consumer 模式只记录日志，不启动服务器。

consumer 实际日志：

```text
Consumer mode: RPC TCP server will not be started
```

这证明 `@EnableRpc(needServer = false)` 的属性读取已经生效。

### RpcProviderBootstrap

`RpcProviderBootstrap` 实现 `BeanPostProcessor`，在 Bean 初始化后检查 `@RpcService`。

处理流程：

```text
获取 Bean 的用户类
  -> 查找 @RpcService
  -> 获取服务接口和版本
  -> 验证实现类确实实现该接口
  -> 注册到 LocalRegistry
  -> 构造 ServiceMetaInfo
  -> 注册到 Etcd / ZooKeeper
```

项目使用 `ClassUtils.getUserClass(bean)`，比直接使用 `bean.getClass()` 更适合处理可能被 Spring 代理包装的 Bean。

如果 `@RpcService` 类没有实现任何接口，或者指定的接口与实现类不兼容，启动时会抛出清晰异常，而不是等到第一次 RPC 调用时才失败。

### RpcConsumerBootstrap

`RpcConsumerBootstrap` 同样实现 `BeanPostProcessor`。

处理流程：

```text
遍历 Spring Bean 字段
  -> 查找 @RpcReference
  -> 默认使用字段类型作为接口
  -> 验证接口和字段可赋值关系
  -> 通过 ServiceProxyFactory 创建代理
  -> ReflectionUtils.setField 注入 Bean
```

实现中还增加了防御性校验：

- `@RpcReference` 字段不能是 `static`。
- `@RpcReference` 字段不能是 `final`。
- 注入目标必须是接口。
- 代理接口必须能赋值给字段类型。

当 `mock=true` 时，注入 Mock 代理；否则注入真实 RPC 代理。

## Spring 示例项目接入

为了不额外创建两个与现有 example 重复的模块，本阶段直接在已有 `example-provider` 和 `example-consumer` 中加入 Spring 测试入口。

### Provider

新增：

```text
SpringBootProviderApplication
```

启动类使用：

```java
@EnableRpc
@SpringBootApplication
```

现有 `UserServiceImpl` 增加：

```java
@RpcService
```

因此启动 Spring 应用后，框架会自动初始化、注册 `UserService` 并启动 8080 TCP Server。

### Consumer

新增：

```text
SpringBootConsumerApplication
SpringRpcConsumerService
```

启动类使用：

```java
@EnableRpc(needServer = false)
```

`SpringRpcConsumerService` 中通过：

```java
@RpcReference
private UserService userService;
```

获得远程代理。`SpringBootConsumerApplication` 实现 `CommandLineRunner`，Spring 启动完成后自动调用测试方法。

## 完整注解驱动链路

### Provider 启动链路

```text
SpringApplication.run
  -> 解析 @EnableRpc
  -> @Import 加载 RpcInitBootstrap
  -> RpcApplication.init
  -> 启动 VertxTcpServer
  -> Spring 创建 UserServiceImpl Bean
  -> RpcProviderBootstrap 发现 @RpcService
  -> LocalRegistry.register
  -> Registry.register
```

### Consumer 启动与调用链路

```text
SpringApplication.run
  -> 解析 @EnableRpc(needServer = false)
  -> RpcApplication.init
  -> 不启动 TCP Server
  -> Spring 创建 SpringRpcConsumerService
  -> RpcConsumerBootstrap 发现 @RpcReference
  -> ServiceProxyFactory 创建 JDK 动态代理
  -> 反射写入 userService 字段
  -> CommandLineRunner.run
  -> userService.getUser
  -> ServiceProxy.invoke
  -> 服务发现、负载均衡、TCP 调用
```

## 单元测试结果

Starter 新增 `RpcAnnotationTest`，覆盖两项行为：

1. `@EnableRpc` 正确导入三个启动组件。
2. `RpcConsumerBootstrap` 能向 `@RpcReference(mock = true)` 字段注入 JDK 动态代理。

测试结果：

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全模块跳过旧集成测试后打包结果：

```text
example-common                  SUCCESS
rpc-core                        SUCCESS
sosrpc-spring-boot-starter       SUCCESS
example-provider                SUCCESS
example-consumer                SUCCESS
Sosrpc                           SUCCESS
```

## Spring 端到端联调结果

测试顺序：

1. 启动 Etcd `localhost:2379`。
2. 启动 `SpringBootProviderApplication`。
3. 启动 `SpringBootConsumerApplication`。

consumer 实际输出：

```text
Consumer mode: RPC TCP server will not be started
Started SpringBootConsumerApplication
Selected service provider: http://localhost:8080
Spring RPC result: spring-achingsoul
```

provider 执行 `UserServiceImpl#getUser`，返回用户信息。

这说明以下环节均已跑通：

- `@EnableRpc` 初始化框架。
- consumer 未启动 TCP Server。
- `@RpcService` 完成服务注册。
- `@RpcReference` 完成代理注入。
- Etcd 服务发现正常。
- 自定义 TCP RPC 调用正常。
- provider 返回的 `User` 成功在 consumer 输出。

## 为什么 Spring Consumer 没有原来的 number 输出

本次 Spring 示例严格调用了：

```java
userService.getUser(user);
```

因此输出是：

```text
Spring RPC result: spring-achingsoul
```

旧的 `ConsumerExample` 还额外调用了两次 `getNumber()`，所以旧入口会输出 number。新的 Spring 示例没有调用该方法，并不代表代理注入或 RPC 调用失败。

如果要继续验证，可以在 `SpringRpcConsumerService.test()` 中增加：

```java
short number = userService.getNumber();
System.out.println("Spring RPC number: " + number);
```

## 本阶段问题复盘

### 1. Spring.io 和 start.aliyun.com 是否有本质区别

没有。二者都是 Spring Initializr 服务，主要差别是可选版本和默认模板。真正影响项目的是最终生成的 Spring Boot 版本、Java 版本和依赖，而不是 Server URL 本身。

### 2. Starter 为什么不应该保留自己的 Application

Starter 是提供给其他应用依赖的库，不应该自行启动 Spring Boot。真正的入口应该位于 provider 或 consumer 业务应用中。

如果 Starter 保留可运行主类和重打包插件，会让模块职责变得模糊，也可能影响打包产物。

### 3. Spring Boot 4 启动时报 `StatusPrinter2` 缺失

最初 provider 启动时报错：

```text
NoClassDefFoundError: ch/qos/logback/core/util/StatusPrinter2
```

原因是：

```text
Spring Boot 4.0.8 需要 Logback 1.5.x
rpc-core 固定依赖 Logback 1.3.12
Maven 最终加载旧版 logback-core
```

`StatusPrinter2` 不存在于旧版本中，因此 Spring Boot 在进入 RPC 初始化前就失败。

最终修复方式：

- `rpc-core` 不再依赖具体的 `logback-classic`。
- 改为只依赖 `slf4j-api 2.0.18`。
- 具体日志实现交给最终应用管理。
- Spring Boot provider 最终使用 `logback-classic/core 1.5.38`。

这个问题体现了库设计的重要原则：

```text
框架库依赖日志门面，
最终应用决定日志实现和版本。
```

### 4. 为什么 provider 的 User 输出不在 consumer 控制台

`UserServiceImpl#getUser` 在 provider JVM 中执行，所以：

```text
User: spring-achingsoul
```

应当在 provider 控制台观察。consumer 控制台只负责输出远程响应：

```text
Spring RPC result: spring-achingsoul
```

分清两个进程的日志，是理解 RPC 跨进程调用的重要部分。

### 5. 全量 Maven 测试失败不等于本阶段失败

执行全量测试时，项目中原有两个集成测试会失败：

- `RegistryTest` 多次初始化 Etcd 心跳，触发 Hutool Cron 已启动异常。
- `VertxTcpClientTest` 没有配套 TCP provider，等待响应超时。

这些问题不是 Starter 编译或注解测试失败。针对性测试和全模块编译均已通过。后续应把依赖外部环境的测试与普通单元测试进一步隔离。

## 推荐调试断点

### Provider

1. `RpcInitBootstrap.registerBeanDefinitions`：观察 `needServer=true`。
2. `RpcApplication.init`：观察配置和注册中心初始化。
3. `VertxTcpServer.doStart`：确认端口为 8080。
4. `RpcProviderBootstrap.postProcessAfterInitialization`：观察 `@RpcService`。
5. `LocalRegistry.register`：确认服务接口名和实现类。
6. `registry.register(serviceMetaInfo)`：确认注册到 Etcd 的元信息。

### Consumer

1. `RpcInitBootstrap.registerBeanDefinitions`：观察 `needServer=false`。
2. `RpcConsumerBootstrap.injectReference`：观察字段类型和注解。
3. `ServiceProxyFactory.getProxy`：确认创建 JDK 动态代理。
4. `ReflectionUtils.setField`：确认代理写入字段。
5. `SpringBootConsumerApplication.run`：确认启动后执行测试。
6. `ServiceProxy.invoke`：继续观察服务发现、负载均衡和 TCP 调用。

测试结束时先停止 consumer，再停止 provider。provider 的 TCP Server 和注册中心连接会让进程持续运行，这是正常现象。

## 秋招表达版本

### 30 秒版本

> 为了降低 RPC 框架的使用成本，我先把 provider 的框架初始化、服务注册和 TCP Server 启动封装为 `ProviderBootstrap`，把 consumer 初始化封装为 `ConsumerBootstrap`。之后开发了独立 Spring Boot Starter，通过 `@EnableRpc` 初始化框架，通过 `@RpcService` 自动注册服务，通过 `@RpcReference` 自动向 Spring Bean 注入 JDK 动态代理。底层使用 `ImportBeanDefinitionRegistrar` 读取启用注解，使用 `BeanPostProcessor` 监听服务 Bean 和消费字段，目前已经完成 Spring provider、consumer 到 Etcd 和 TCP 调用的端到端联调。

### Spring 扩展点追问

如果面试官问“为什么使用 BeanPostProcessor”，可以回答：

> provider 和 consumer 的处理对象本身就是 Spring Bean。BeanPostProcessor 可以在 Bean 初始化后获得实例和类型信息，检查 `@RpcService` 并注册服务，或者检查字段上的 `@RpcReference` 并注入代理。相比手动扫描 class 文件，它可以直接复用 Spring 的 Bean 生命周期和组件扫描结果。

如果面试官问“`@EnableRpc` 为什么使用 `@Import`”，可以回答：

> `@Import` 可以让 RPC 启动组件只在用户显式启用时进入 Spring 容器，避免引入依赖后自动产生端口占用或注册中心连接。它体现了 Starter 的可选加载能力。

## 当前能力边界

当前实现符合教学目标，但仍有以下工程边界：

- `@RpcReference` 的版本、负载均衡、重试、容错属性尚未全部覆盖到单引用级代理配置，当前主要使用全局 `RpcConfig`。
- `LocalRegistry` 保存的是实现类，服务端仍通过反射创建对象，没有真正调用 Spring 容器中的 Bean 实例。
- 因此服务实现类内部的 Spring 依赖注入能力还没有完整传递到 RPC 服务端实例。
- `RpcInitBootstrap` 在 BeanDefinition 注册阶段启动网络服务器，生命周期管理还可以进一步优化。
- TCP Server 尚未作为 Spring Bean 托管，也没有通过 `DisposableBean` 或生命周期接口优雅停止。
- 当前通过 `@EnableRpc` 显式启用，尚未实现标准 Boot 自动配置文件。
- provider 和 consumer 示例复用了原有模块，没有按教学文档单独拆成两个 Spring 示例模块。
- 外部依赖型集成测试尚未与普通单元测试隔离。

## 后续可以扩展的方向

1. 将服务实例注册到 `LocalRegistry`，让 RPC 服务端调用 Spring Bean 而不是反射新建对象。
2. 为 `@RpcReference` 构建独立的引用配置对象，使版本、重试、容错等注解属性真正按字段生效。
3. 使用 `SmartLifecycle` 管理 TCP Server 的启动和停止。
4. 增加标准 `AutoConfiguration.imports`，支持真正的自动配置 Starter。
5. 增加 `@ConfigurationProperties` 和 IDE 配置提示。
6. 增加 Spring Context 集成测试，并使用 Mock Registry 隔离 Etcd。
7. 把外部环境测试标记为 integration test，通过 Maven profile 单独执行。
8. 增加多个 `@RpcService` 和多个 `@RpcReference` 的组合测试。

## 本阶段总结

v0.9.0 的核心变化不是增加新的网络或治理算法，而是把已有能力包装成更容易使用的框架接口。

项目现在支持两种使用方式：

```text
普通 Java：
  ProviderBootstrap / ConsumerBootstrap

Spring Boot：
  @EnableRpc
  @RpcService
  @RpcReference
```

通过这一阶段，Sosrpc 已经从“需要开发者了解内部初始化细节的教学框架”向“可以通过 Starter 和注解接入的框架”迈进了一步。

本阶段最重要的收获有三点：

1. 框架易用性同样是架构设计的一部分。
2. Spring 的扩展点可以把 RPC 初始化、服务注册和代理注入接入 Bean 生命周期。
3. 框架库要控制依赖边界，例如依赖日志门面而不是强制携带具体实现。

最终已完成 Spring Boot 4 环境下 provider 与 consumer 的真实 RPC 调用，consumer 成功输出 `Spring RPC result: spring-achingsoul`，说明注解驱动链路已经跑通。
