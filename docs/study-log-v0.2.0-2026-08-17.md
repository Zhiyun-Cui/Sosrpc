# Sosrpc 学习日志 v0.2.0

## 基本信息

- 项目名称：Sosrpc
- 当前版本：v0.2.0
- 日志类型：阶段学习记录
- 更新时间：2026-08-17 15:29:38 +08:00
- 学习阶段：全局配置加载
- 前置版本：v0.1.0 简易 RPC 调用链路跑通
- 面向目标：秋招项目梳理、Java 后端配置体系理解、RPC 框架演进记录
- 参考资料：教学文档 2《全局配置加载》

## 本阶段目标

v0.1.0 阶段已经完成了简易 RPC 的最小调用闭环：consumer 通过动态代理调用接口，provider 通过 HTTP 接收请求、反射执行服务实现并返回结果。

v0.2.0 阶段的目标是在不破坏原有 RPC 调用链路的前提下，引入全局配置加载能力，解决上一阶段代码中配置硬编码的问题。重点理解：

1. RPC 框架为什么需要统一配置对象。
2. 如何从 `application.properties` 中读取配置。
3. 如何把配置文件中的 `rpc.*` 属性映射到 Java 配置类。
4. 如何维护一个全局可访问的配置实例。
5. provider 和 consumer 如何复用同一套配置读取逻辑。
6. 配置项命名必须和配置类字段保持一致。

## 当前模块结构

本阶段开始将 RPC 核心能力从简易版模块迁移到更适合后续扩展的 `rpc-core` 模块。

当前主要模块职责：

- `example-common`：公共模型和服务接口，例如 `User`、`UserService`。
- `example-provider`：服务提供者，注册 `UserServiceImpl` 并按配置端口启动 HTTP 服务。
- `example-consumer`：服务消费者，读取配置、获取动态代理、调用远程服务。
- `rpc-core`：RPC 框架核心模块，维护配置类、配置加载工具、全局配置 holder、动态代理、序列化、服务端处理器等。

## 已完成内容

### 1. 新增 RPC 配置类

在 `rpc-core` 中新增 `RpcConfig`，用于承载 RPC 框架的全局配置。

当前配置项包括：

- `name`：RPC 框架名称，默认值为 `achingsoul`。
- `version`：版本号，默认值为 `1.0`。
- `serverHost`：服务地址，默认值为 `localhost`。
- `serverPort`：服务端口，默认值为 `8080`。

这一层的意义是将原先散落在代码里的配置收拢成一个对象，为后续增加序列化方式、注册中心地址、负载均衡策略、超时时间等配置做准备。

### 2. 新增配置常量

在 `rpc-core` 中新增 `RpcConstant`，当前核心常量是：

```java
String DEFAULT_CONFIG_PREFIX = "rpc";
```

这表示默认从配置文件中读取 `rpc` 前缀下的配置项，例如：

```properties
rpc.name=Sosrpc
rpc.version=2.0
rpc.serverPort=8081
```

### 3. 新增配置加载工具

在 `rpc-core` 中新增 `ConfigUtils`，基于 Hutool 的 `Props` 实现配置读取。

核心能力：

- 默认读取 `application.properties`。
- 支持传入配置前缀，例如 `rpc`。
- 支持传入环境名，后续可以扩展为读取 `application-dev.properties`、`application-prod.properties` 等文件。
- 通过 `props.toBean(tClass, prefix)` 将配置项映射到 Java 对象。

本阶段要特别注意属性命名：

```properties
rpc.serverPort=8081
```

而不是：

```properties
rpc.server.port=8081
```

因为当前配置类字段是 `serverPort`，不是嵌套对象 `server.port`。如果写成 `rpc.server.port`，Hutool 会尝试寻找 `RpcConfig` 中的 `server` 字段，从而导致绑定失败。

### 4. 新增全局配置 holder

在 `rpc-core` 中新增 `RpcApplication`，用于维护全局配置对象。

当前设计：

- `private static volatile RpcConfig rpcConfig` 保存全局配置。
- `init(RpcConfig newRpcConfig)` 支持外部传入自定义配置。
- `init()` 默认从配置文件读取配置。
- `getRpcConfig()` 使用双重检查，在首次获取配置时进行懒加载。

这个类的作用类似框架入口或上下文 holder。后续框架内部只需要调用：

```java
RpcApplication.getRpcConfig()
```

就可以获取统一配置，避免每个地方重复读取配置文件。

### 5. provider 使用全局配置启动

provider 侧启动流程更新为：

1. 调用 `RpcApplication.init()` 初始化 RPC 框架配置。
2. 使用 `LocalRegistry.register` 注册服务实现。
3. 使用 `RpcApplication.getRpcConfig().getServerPort()` 读取端口并启动 HTTP 服务。

这样 provider 不再把端口固定写死为 `8080`，而是由配置文件决定。

### 6. consumer 使用全局配置请求服务

consumer 侧通过 `ConsumerExample` 验证配置读取：

```java
RpcConfig rpc = ConfigUtils.loadConfig(RpcConfig.class, "rpc");
System.out.println(rpc);
```

动态代理侧也读取全局配置，使用 `serverHost` 和 `serverPort` 拼接请求地址：

```java
RpcConfig rpcConfig = RpcApplication.getRpcConfig();
String url = String.format("http://%s:%s", rpcConfig.getServerHost(), rpcConfig.getServerPort());
```

这一步让配置真正参与 RPC 调用链路，而不只是被打印出来。

## 验收结果

本阶段目标已经完成。

配置读取输出：

```text
RpcConfig(name=Sosrpc, version=2.0, serverHost=localhost, serverPort=8081)
```

provider 启动输出：

```text
rpc init, config: RpcConfig(name=Sosrpc, version=2.0, serverHost=localhost, serverPort=8081)
HTTP server started on port 8081
```

consumer 调用成功输出：

```text
achingsoul
```

provider 侧收到请求并执行服务：

```text
Received request: POST /
User: achingsoul
```

本阶段验收指标：

- `application.properties` 中配置 `rpc.serverPort=8081`。
- `ConfigUtils` 能正确读取配置并映射为 `RpcConfig`。
- `RpcApplication` 能初始化并维护全局配置对象。
- provider 能按配置端口 `8081` 启动。
- consumer 能读取配置并向配置端口发起请求。
- 最终仍能输出 `achingsoul`，说明全局配置加载没有破坏原有 RPC 调用链路。

## 问题复盘

### 1. 配置项命名错误

曾出现过类似提示：

```text
Ignore property: [rpc.server.port]
Field [server] is not exist in [RpcConfig]
```

原因是配置文件中写成了：

```properties
rpc.server.port=8081
```

但当前 `RpcConfig` 中的字段是：

```java
private Integer serverPort = 8080;
```

正确写法应该是：

```properties
rpc.serverPort=8081
```

复盘点：

- Java Bean 属性名和配置文件 key 必须匹配。
- `serverPort` 是一个字段，`server.port` 表示嵌套结构。
- 后续如果要写 `rpc.server.port`，需要把 `RpcConfig` 设计成嵌套配置对象，而不是当前这种平铺结构。

### 2. 只读取配置不等于使用配置

一开始 consumer 能打印出 `RpcConfig`，但如果动态代理仍然请求硬编码地址：

```java
http://localhost:8080
```

那么配置文件中的 `serverPort=8081` 并没有真正参与调用链路。

复盘点：

- 配置加载只是第一步。
- 关键是框架内部要统一从全局配置对象取值。
- provider 和 consumer 必须使用同一套配置约定，否则端口容易不一致。

### 3. consumer 卡住不退出

调试过程中 consumer 曾出现只打印配置、不输出 `achingsoul`、也不退出的情况。

直接原因是服务端收到请求后没有写回响应，consumer 一直等待 HTTP 响应。

复盘点：

- RPC 调用链路必须有请求和响应两个方向。
- provider 端处理完请求后必须调用 `doResponse`。
- 如果 consumer 长时间不结束，优先检查 provider 是否启动、端口是否一致、服务端是否写回响应。

### 4. 代码清理要保持教学一致性

修注释或排查问题时，不能随意改动教学文档对应的核心代码流程。

后续原则：

- 注释乱码只改注释。
- 业务逻辑只在明确定位后做最小修改。
- 和教学文档不一致的改动必须先说明原因。
- 每次改动后都要确认不会破坏后续学习路径。

## 秋招表达版本

可以这样描述本阶段项目：

> 在简易 RPC 调用链路跑通后，我进一步为框架增加了全局配置加载能力。通过定义 `RpcConfig` 统一维护框架名称、版本、服务地址和端口等配置，并基于 Hutool `Props` 从 `application.properties` 读取 `rpc` 前缀下的配置项。随后使用 `RpcApplication` 作为全局配置 holder，通过懒加载和双重检查保证框架内部可以统一获取配置。provider 启动时从全局配置读取端口，consumer 动态代理发起请求时也从全局配置读取服务地址和端口，从而将原先硬编码的 `localhost:8080` 改造成可配置项。目前已验证 provider 在 `8081` 端口启动，consumer 能通过配置地址完成远程调用并返回 `achingsoul`。

这个表达适合在秋招中说明项目从“能跑通”到“可配置化”的演进过程。

## 当前能力边界

当前全局配置加载仍然是教学阶段实现，有以下边界：

- 只支持 `.properties` 文件。
- 默认读取 `application.properties`。
- 环境隔离能力已有方法预留，但尚未实际验证 `application-dev.properties`、`application-prod.properties`。
- 配置加载失败时使用默认值，但异常信息没有细分。
- 配置对象暂时是平铺结构，没有按应用、服务、注册中心、序列化器等维度分组。
- 配置加载后不会自动热更新。
- provider 和 consumer 仍然需要各自准备配置文件。
- 服务发现尚未实现，当前只是通过配置替代硬编码地址。

## 面试深挖问题

### 配置体系

1. 为什么 RPC 框架需要全局配置对象？
2. 直接在代码里写死端口和地址有什么问题？
3. `RpcConfig` 中应该放哪些配置项？
4. 配置项应该平铺还是分组？两种方式各有什么优缺点？
5. 如果配置项越来越多，如何避免 `RpcConfig` 变得臃肿？

### 配置加载

1. `ConfigUtils` 是如何从 `application.properties` 读取配置的？
2. Hutool `Props#toBean` 的作用是什么？
3. 为什么 `rpc.serverPort` 能绑定成功，而 `rpc.server.port` 不行？
4. 如果要支持 YAML 配置文件，你会怎么扩展？
5. 如果配置文件不存在，当前框架会怎么处理？
6. 如何支持不同环境配置，例如 dev、test、prod？

### 全局 holder

1. `RpcApplication` 在项目中承担什么角色？
2. 为什么使用 `volatile` 修饰 `rpcConfig`？
3. 双重检查锁解决了什么问题？
4. `init()` 和 `getRpcConfig()` 的关系是什么？
5. 如果多线程同时第一次调用 `getRpcConfig()`，会发生什么？
6. 如果用户想手动传入配置，当前设计是否支持？

### provider 与 consumer

1. provider 为什么要先调用 `RpcApplication.init()`？
2. provider 端口从配置读取后，对部署有什么好处？
3. consumer 动态代理为什么也需要读取全局配置？
4. 如果 provider 配置端口是 `8081`，consumer 仍请求 `8080`，会发生什么？
5. provider 和 consumer 的配置如何保持一致？

### 框架演进

1. 当前配置化和服务发现有什么区别？
2. 为什么有了配置文件后，仍然需要注册中心？
3. 后续如何把 `serverHost/serverPort` 改造成服务发现结果？
4. 如何支持配置热更新？
5. 如何设计配置优先级，例如代码默认值、配置文件、环境变量、启动参数？
6. 如何保护敏感配置，例如注册中心账号密码？

## 下一版本计划

建议后续新建 `study-log-v0.3.0-日期.md`，不要追加写入本文件。

v0.3.0 可以考虑聚焦以下方向之一：

1. Mock 调用能力：当服务不可用时返回模拟结果，方便测试 consumer。
2. 序列化器可插拔：支持根据配置选择 JDK、JSON、Kryo 等序列化方式。
3. SPI 机制：让扩展点通过配置动态加载实现类。
4. 服务注册与发现：从本地注册表过渡到远程注册中心。
5. 配置分组：将配置拆分为服务配置、注册中心配置、序列化配置等。
6. 环境配置：验证 `application-dev.properties` 和 `application-prod.properties`。
7. 端到端测试：固定 provider 启动、consumer 调用、响应校验的自动化测试流程。

## 阶段结论

v0.2.0 阶段完成了 RPC 框架的全局配置加载能力。项目从 v0.1.0 的“硬编码可运行”演进到“配置化可运行”：provider 启动端口、consumer 请求地址都可以由配置文件控制。

这一阶段的核心收获不是配置文件本身，而是理解框架应该如何集中管理配置，并让内部组件通过统一入口获取配置。这个能力会成为后续扩展注册中心、序列化器、负载均衡、超时控制等功能的基础。
