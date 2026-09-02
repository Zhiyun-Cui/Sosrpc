# Sosrpc

Sosrpc 是一个基于 Java 21 的轻量级、可扩展 RPC 框架。项目从服务注册、动态代理和序列化出发，逐步实现了自定义 TCP 协议、服务发现、负载均衡、容错与 Spring Boot 注解接入，适合学习 RPC 核心原理与工程化设计。

## 核心能力

- 基于 Vert.x 的 TCP 客户端与服务端
- 自定义消息协议、编解码与半包/粘包处理
- JDK、JSON、Kryo、Hessian 序列化
- Etcd 与 ZooKeeper 注册中心
- 随机、轮询与一致性哈希负载均衡
- 固定间隔、指数退避与不重试策略
- Fail Fast、Fail Safe、Fail Over、Fail Back 容错策略
- 令牌桶限流与简单熔断器
- SPI 扩展加载机制
- Spring Boot Starter：`@EnableRpc`、`@RpcService`、`@RpcReference`

## 环境要求

- JDK 21
- Maven 3.9+
- Etcd 3.5+ 或 ZooKeeper 3.8+

## 项目结构

```text
Sosrpc
├── rpc-core                     # RPC 核心实现
├── sosrpc-spring-boot-starter   # Spring Boot 注解与自动接入
├── example-common              # 示例共享模型和服务接口
├── example-provider            # 服务提供者示例
├── example-consumer            # 服务消费者示例
└── docs                        # 分阶段学习与实现记录
```

## 快速开始

1. 启动 Etcd（默认地址为 `http://localhost:2379`）。如需使用 ZooKeeper，请在示例配置中把 `rpc.registryConfig.registry` 改为 `zookeeper` 并配置对应地址。
2. 编译项目：

```bash
mvn clean package -DskipTests
```

3. 在 IDE 中运行服务提供者：

```text
example-provider/src/main/java/com/achingsoul/example/provider/SpringBootProviderApplication.java
```

4. 再运行服务消费者：

```text
example-consumer/src/main/java/com/achingsoul/example/consumer/SpringBootConsumerApplication.java
```

默认情况下，Provider 在 `localhost:8080` 提供 RPC 服务，Consumer 会通过注册中心发现并调用它。

## 配置示例

```properties
rpc.name=Sosrpc
rpc.version=2.0
rpc.serializer=kryo
rpc.loadBalancer=roundRobin
rpc.retryStrategy=no
rpc.tolerantStrategy=failFast
rpc.registryConfig.registry=etcd
rpc.registryConfig.address=http://localhost:2379
```

完整配置可参考：

- `example-provider/src/main/resources/application.properties`
- `example-consumer/src/main/resources/application.properties`

## 使用方式

普通 Java 项目可通过 `ProviderBootstrap` 与 `ConsumerBootstrap` 启动。Spring Boot 项目可以使用：

- `@EnableRpc`：启用 Sosrpc；Consumer 使用 `@EnableRpc(needServer = false)`。
- `@RpcService`：声明并注册服务实现。
- `@RpcReference`：向字段注入远程服务代理。

## 测试

大部分核心能力都有单元测试。注册中心和 TCP 联调测试依赖本地外部服务，运行全量测试前请先启动相应基础设施。

```bash
mvn test
```

实现过程与各阶段设计说明见 [`docs/`](docs/)。

## 当前定位

Sosrpc 当前主要面向学习、实验和作品展示，尚未以生产级 RPC 框架为目标。后续可继续完善连接复用、优雅停机、指标监控、配置中心和独立集成测试 Profile。
