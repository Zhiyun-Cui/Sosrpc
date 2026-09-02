# Sosrpc 学习日志 v0.4.0

## 基本信息

- 项目名称：Sosrpc
- 当前版本：v0.4.0
- 日志类型：阶段学习记录
- 更新时间：2026-08-17 22:03:01 +08:00
- 学习阶段：序列化器与 SPI 机制
- 前置版本：v0.3.0 接口 Mock
- 面向目标：秋招项目梳理、RPC 框架可扩展性理解、序列化协议与 SPI 扩展点沉淀
- 参考资料：教学文档 4《序列化器与 SPI 机制》

## 本阶段目标

v0.1.0 阶段完成了最小 RPC 调用链路，v0.2.0 阶段引入了全局配置加载，v0.3.0 阶段增加了接口 Mock。本阶段的核心目标，是把原先写死的 JDK 序列化器改造成可配置、可扩展的序列化体系。

本阶段要解决三个问题：

1. 框架除了 JDK 原生序列化，是否可以支持 JSON、Kryo、Hessian 等更多序列化方式。
2. consumer 和 provider 如何通过配置选择同一种序列化器。
3. 框架如何不硬编码所有实现类，而是通过 SPI 根据 key 动态加载实现类。

这一步的重点不是“多写几个工具类”，而是让 RPC 框架从硬编码实现演进到可插拔扩展。

## 当前模块结构

本阶段仍然围绕四个主要模块展开：

- `example-common`：公共模型和服务接口，例如 `User`、`UserService`。consumer 和 provider 都依赖它，以保证双方对接口和数据模型的理解一致。
- `example-provider`：服务提供者，启动 HTTP server，注册 `UserServiceImpl`，接收 consumer 发来的序列化请求。
- `example-consumer`：服务消费者，通过动态代理调用 `UserService` 接口，框架内部负责把接口调用转换成 HTTP 请求。
- `rpc-core`：RPC 框架核心模块，本阶段新增或完善序列化器、序列化器工厂、SPI 加载器、SPI 配置文件，并让客户端和服务端统一使用配置中的序列化器。

## 本阶段新增和涉及的核心文件

### 1. 序列化接口与实现类

位置：

```text
rpc-core/src/main/java/com/achingsoul/myrpc/serializer/
```

核心文件：

- `Serializer.java`：统一序列化器接口，定义 `serialize` 和 `deserialize` 两个方法。
- `JdkSerializer.java`：JDK 原生序列化器，早期默认实现。
- `JsonSerializer.java`：JSON 序列化器，基于 Jackson `ObjectMapper`。
- `KryoSerializer.java`：Kryo 序列化器，使用 `ThreadLocal<Kryo>` 避免线程安全问题。
- `HessianSerializer.java`：Hessian 二进制序列化器。
- `SerializerKeys.java`：维护序列化器 key，例如 `jdk`、`json`、`kryo`、`hessian`。
- `SerializerFactory.java`：序列化器工厂，根据 key 获取具体序列化器实例。

### 2. SPI 加载相关文件

位置：

```text
rpc-core/src/main/java/com/achingsoul/myrpc/spi/SpiLoader.java
rpc-core/src/main/resources/META-INF/rpc/system/com.achingsoul.sosrpc.serializer.Serializer
```

`SpiLoader` 负责扫描指定目录下的 SPI 配置文件，将 key 和实现类路径建立映射。

系统 SPI 配置文件当前内容：

```properties
hessian=com.achingsoul.sosrpc.serializer.HessianSerializer
json=com.achingsoul.sosrpc.serializer.JsonSerializer
kryo=com.achingsoul.sosrpc.serializer.KryoSerializer
jdk=com.achingsoul.sosrpc.serializer.JdkSerializer
```

这表示：

```text
配置 key -> 具体序列化器实现类
```

例如：

```text
kryo -> com.achingsoul.sosrpc.serializer.KryoSerializer
```

### 3. 全局配置类

位置：

```text
rpc-core/src/main/java/com/achingsoul/myrpc/config/RpcConfig.java
```

本阶段新增配置项：

```java
private String serializer = SerializerKeys.JDK;
```

它表示当前 RPC 框架默认使用哪种序列化器。如果配置文件里写：

```properties
rpc.serializer=kryo
```

那么框架运行时会通过 `RpcApplication.getRpcConfig().getSerializer()` 读取到 `kryo`。

### 4. consumer 和 provider 配置文件

位置：

```text
example-consumer/src/main/resources/application.properties
example-provider/src/main/resources/application.properties
```

当前测试使用：

```properties
rpc.name=Sosrpc
rpc.version=2.0
rpc.mock=false
rpc.serializer=kryo
```

注意：provider 和 consumer 必须使用同一种序列化器。consumer 用 Kryo 序列化请求，provider 就必须用 Kryo 反序列化请求；provider 用 Kryo 序列化响应，consumer 也必须用 Kryo 反序列化响应。

## 项目文件逻辑关系梳理

本阶段最容易混乱的地方，是“配置、工厂、SPI、具体序列化器”之间的关系。可以按下面这条链路理解。

### 1. 配置文件决定使用哪个序列化器

在 consumer 和 provider 的 `application.properties` 中配置：

```properties
rpc.serializer=kryo
```

项目启动后，`RpcApplication` 会通过 `ConfigUtils` 读取配置文件，并映射为 `RpcConfig` 对象。

也就是：

```text
application.properties
  -> ConfigUtils
  -> RpcConfig
  -> RpcApplication 持有全局配置
```

之后框架内部只需要调用：

```java
RpcApplication.getRpcConfig().getSerializer()
```

就能拿到当前配置的序列化器 key。

### 2. SerializerFactory 根据 key 获取序列化器

`ServiceProxy` 和 `HttpServerHandler` 都不应该再写死：

```java
new JdkSerializer()
```

而是统一改成：

```java
SerializerFactory.getInstance(RpcApplication.getRpcConfig().getSerializer())
```

这句话的含义是：

```text
从全局配置里拿到 serializer key
  -> 交给 SerializerFactory
  -> 返回对应的 Serializer 实例
```

例如配置是：

```properties
rpc.serializer=kryo
```

那么最终拿到的是：

```java
KryoSerializer
```

### 3. SerializerFactory 不直接硬编码实现类，而是委托 SpiLoader

教学文档中先给了一个 Map 版工厂：

```text
jdk -> new JdkSerializer()
json -> new JsonSerializer()
kryo -> new KryoSerializer()
hessian -> new HessianSerializer()
```

这个版本能跑，但扩展性一般。后面引入 SPI 后，`SerializerFactory` 不再维护写死的 Map，而是调用：

```java
SpiLoader.load(Serializer.class);
SpiLoader.getInstance(Serializer.class, key);
```

这样序列化器映射关系就从 Java 代码转移到了资源配置文件中。

### 4. SpiLoader 读取 SPI 配置文件

`SpiLoader` 会扫描：

```text
META-INF/rpc/system/
META-INF/rpc/custom/
```

当前系统内置配置文件是：

```text
META-INF/rpc/system/com.achingsoul.sosrpc.serializer.Serializer
```

文件内容是：

```properties
kryo=com.achingsoul.sosrpc.serializer.KryoSerializer
```

`SpiLoader` 读取到这行后，会做两件事：

1. 把 `kryo` 和 `KryoSerializer.class` 建立映射。
2. 当工厂请求 `kryo` 时，通过反射创建或复用 `KryoSerializer` 实例。

也就是：

```text
SPI 配置文件
  -> SpiLoader.load
  -> loaderMap: 接口名 -> key -> 实现类
  -> SpiLoader.getInstance
  -> instanceCache: 类路径 -> 对象实例
```

### 5. 请求链路中的序列化器使用位置

consumer 侧：

```text
ConsumerExample
  -> ServiceProxyFactory.getProxy(UserService.class)
  -> ServiceProxy.invoke
  -> SerializerFactory.getInstance(...)
  -> KryoSerializer.serialize(RpcRequest)
  -> HTTP POST 请求 provider
```

provider 侧：

```text
ProviderExample / EasyProviderExample
  -> RpcApplication.init()
  -> LocalRegistry.register(...)
  -> VertxHttpServer.doStart(...)
  -> HttpServerHandler.handle
  -> SerializerFactory.getInstance(...)
  -> KryoSerializer.deserialize(RpcRequest)
  -> 反射调用 UserServiceImpl
  -> KryoSerializer.serialize(RpcResponse)
```

consumer 接收响应：

```text
ServiceProxy
  -> 接收 HTTP 响应字节
  -> KryoSerializer.deserialize(RpcResponse)
  -> rpcResponse.getData()
  -> 返回给 UserService 代理调用处
```

所以这一节最终形成的完整关系是：

```text
配置文件 rpc.serializer
  -> RpcConfig.serializer
  -> RpcApplication.getRpcConfig()
  -> SerializerFactory
  -> SpiLoader
  -> SPI 配置文件
  -> 具体 Serializer 实现
  -> ServiceProxy / HttpServerHandler 统一使用
```

## 已完成内容

### 1. 引入多种序列化器

本阶段在 `rpc-core` 中补充了三种序列化器：

- JSON：可读性强，便于调试，但体积较大，类型处理更复杂。
- Kryo：性能较好，适合 Java 内部通信，但不跨语言。
- Hessian：二进制协议，支持跨语言，但对部分 Java 类型的还原可能和预期不完全一致。

当前已经验证 Kryo 可以完成真实 RPC 调用。

### 2. 增加序列化器 key

`SerializerKeys` 用常量维护 key：

```java
String JDK = "jdk";
String JSON = "json";
String KRYO = "kryo";
String HESSIAN = "hessian";
```

这样可以避免在代码中到处写字符串，降低拼写错误风险。

### 3. RpcConfig 增加 serializer 字段

配置项从外部文件进入框架内部：

```properties
rpc.serializer=kryo
```

映射到：

```java
private String serializer = SerializerKeys.JDK;
```

默认值是 `jdk`，表示如果用户不配置序列化器，框架仍然能回退到 JDK 原生序列化。

### 4. ServiceProxy 和 HttpServerHandler 使用工厂获取序列化器

原先写死 JDK 序列化器：

```java
new JdkSerializer()
```

现在改为配置驱动：

```java
SerializerFactory.getInstance(RpcApplication.getRpcConfig().getSerializer())
```

这一步很关键，因为只有 consumer 和 provider 的实际收发逻辑都使用工厂，`rpc.serializer` 配置才真正生效。

### 5. 实现自定义 SPI 加载器

`SpiLoader` 主要承担两个职责：

- 读取 `META-INF/rpc/system/` 和 `META-INF/rpc/custom/` 下的配置文件。
- 根据接口类型和 key 返回对应实现类实例。

它内部维护两个缓存：

- `loaderMap`：保存接口名、key、实现类之间的关系。
- `instanceCache`：保存已经创建过的对象实例，避免重复创建。

### 6. 使用 SPI 重构 SerializerFactory

`SerializerFactory` 当前不再依赖硬编码 Map，而是通过 `SpiLoader` 获取实例。

这表示序列化器的扩展点已经从代码层移动到配置层。后续如果用户想新增一个自定义序列化器，理论上只需要：

1. 新建类实现 `Serializer` 接口。
2. 在 `META-INF/rpc/custom/com.achingsoul.sosrpc.serializer.Serializer` 中配置 key 和类路径。
3. 在 `application.properties` 中指定对应 key。

## 验收结果

本阶段已经通过 Kryo 完成真实 RPC 调用。

运行条件：

```properties
rpc.mock=false
rpc.serializer=kryo
```

运行顺序：

1. 先启动 provider。
2. 再启动 consumer。

验收现象：

```text
rpc init, config: RpcConfig(... mock=false, serializer=kryo)
加载类型为 com.achingsoul.sosrpc.serializer.Serializer 的 SPI
achingsoul
```

这说明：

1. 配置文件中的 `rpc.serializer=kryo` 被正确读取。
2. `SerializerFactory` 触发了 `SpiLoader` 加载。
3. SPI 文件中的 `kryo` key 能正确映射到 `KryoSerializer`。
4. consumer 能用 Kryo 序列化 `RpcRequest`。
5. provider 能用 Kryo 反序列化请求并调用 `UserServiceImpl`。
6. provider 能用 Kryo 序列化 `RpcResponse`。
7. consumer 能反序列化响应并输出 `achingsoul`。

本阶段验收指标：

- `rpc-core` 中存在 JSON、Kryo、Hessian 三种新增序列化器。
- `RpcConfig` 中存在 `serializer` 配置项。
- consumer 和 provider 的配置文件中可以指定 `rpc.serializer`。
- `SerializerFactory` 能根据 key 获取序列化器。
- `SpiLoader` 能读取 `META-INF/rpc/system/` 下的 SPI 配置文件。
- provider 和 consumer 使用相同序列化器时，真实 RPC 调用成功。
- 使用 Kryo 时，consumer 最终输出 `achingsoul`。

## 问题复盘

### 1. JsonSerializer 中 Value 相关方法爆红

曾出现 `writeValueAsBytes`、`readValue`、`convertValue` 爆红。

原因不是缺少 `Value` 相关类，而是导错了 `ObjectMapper`。

错误导入：

```java
import cn.hutool.json.ObjectMapper;
```

正确导入：

```java
import com.fasterxml.jackson.databind.ObjectMapper;
```

这些方法属于 Jackson 的 `ObjectMapper`，不是 Hutool 的 `ObjectMapper`。

同时，`rpc-core/pom.xml` 需要有 `jackson-databind` 依赖，否则 Jackson 的 `ObjectMapper` 可能无法被识别。

### 2. consumer 连接被拒绝

曾出现：

```text
ConnectException: Connection refused: connect
```

直接原因是 consumer 请求的端口和 provider 启动端口不一致。

例如：

```text
provider: 8081
consumer: 8080
```

解决思路是保证 consumer 和 provider 的 `rpc.serverPort` 一致，或者两边都不写端口，统一使用默认值 `8080`。

本阶段要特别注意：序列化器要一致，端口也要一致。否则错误现象可能会让人误以为是序列化器失败。

### 3. Hessian 下 getNumber 出现类型转换异常

曾出现：

```text
java.lang.Integer cannot be cast to java.lang.Short
```

原因是 `UserService#getNumber` 返回 `short`，但 Hessian 反序列化数字时返回了 `Integer`，JDK 动态代理按接口返回类型处理时发生类型转换失败。

这说明不同序列化器对类型的还原细节可能不同。`getUser` 能成功返回 `User`，说明主链路已经打通；`getNumber` 的问题更偏向序列化器类型兼容性问题。

当前用 Kryo 跑通，说明 Kryo 在这个示例上能更好地保持返回类型。

### 4. Map 版工厂和 SPI 版工厂不要混用

教学文档先讲 Map 版 `SerializerFactory`，后讲 SPI 版 `SerializerFactory`。

学习时容易误解为两个都要保留。实际应该理解为：

```text
Map 版工厂：过渡方案，用硬编码 Map 管理内置序列化器。
SPI 版工厂：最终方案，用配置文件和 SpiLoader 管理序列化器。
```

如果已经做到 SPI 阶段，`SerializerFactory` 应以 SPI 版为准。

### 5. Java 原生 SPI 和当前自定义 SPI 不是同一个目录

Java 原生 SPI 常见目录是：

```text
META-INF/services/
```

本教学文档的自定义 SPI 使用的是：

```text
META-INF/rpc/system/
META-INF/rpc/custom/
```

当前 `SpiLoader` 扫描的是 `META-INF/rpc/`，不是 `META-INF/services/`。所以配置文件必须放在正确目录下，否则 `SpiLoader` 找不到对应 key。

## 秋招表达版本

可以这样描述本阶段项目：

> 在完成基础 RPC 调用、全局配置加载和接口 Mock 后，我进一步为框架增加了可插拔的序列化体系。首先抽象统一的 `Serializer` 接口，并实现 JDK、JSON、Kryo、Hessian 等多种序列化器；随后在 `RpcConfig` 中新增 `serializer` 配置项，使 consumer 和 provider 可以通过 `application.properties` 指定一致的序列化方式。为了避免在工厂类中硬编码所有实现，我实现了一个简易 SPI 加载器，通过扫描 `META-INF/rpc/system/` 和 `META-INF/rpc/custom/` 下的配置文件，将序列化器 key 映射到具体实现类，并通过缓存复用实例。最终在 `ServiceProxy` 和 `HttpServerHandler` 中统一通过 `SerializerFactory` 获取序列化器，使请求和响应的序列化方式完全由配置驱动。目前已验证在 `rpc.serializer=kryo` 场景下，provider 和 consumer 可以完成真实 RPC 调用并返回 `achingsoul`。

这段适合用来表达项目的可扩展性、配置驱动能力和对 Java SPI 思想的理解。

## 当前能力边界

当前序列化与 SPI 机制仍然是教学阶段实现，存在以下边界：

- consumer 和 provider 必须手动保持 `rpc.serializer` 一致。
- 当前没有在请求头或协议层携带序列化器标识，服务端无法自动识别客户端使用的序列化器。
- `SerializerFactory#getInstance` 如果 key 不存在，会直接抛异常，缺少更友好的降级或错误提示。
- `DEFAULT_SERIALIZER` 在 SPI 版工厂中暂时没有实际参与兜底逻辑。
- SPI 配置文件按行用 `=` 分割，暂未处理空行、注释行、重复 key 冲突提示等细节。
- `SpiLoader` 当前使用 `newInstance()`，后续可以替换为更推荐的反射构造方式。
- Kryo 虽然性能较好，但不是跨语言方案，并且线程安全需要通过 `ThreadLocal` 处理。
- Hessian 对部分基础数字类型的反序列化结果可能和接口返回类型不完全一致。
- JSON 可读性较好，但对象参数和返回值中的 `Object` 类型需要额外转换处理。
- 当前还没有针对不同序列化器建立独立测试用例。

## 面试深挖问题

### 序列化基础

1. RPC 框架为什么需要序列化和反序列化？
2. 请求对象 `RpcRequest` 和响应对象 `RpcResponse` 分别在什么时候被序列化？
3. JDK、JSON、Kryo、Hessian 的主要区别是什么？
4. 为什么 JSON 可读性好，但性能和体积不一定占优？
5. 为什么 Kryo 适合 Java 内部服务调用，但不适合跨语言场景？
6. Hessian 为什么可能出现数字类型还原不完全符合接口声明的情况？

### 框架设计

1. 为什么不能在 `ServiceProxy` 和 `HttpServerHandler` 中一直写死 `new JdkSerializer()`？
2. `Serializer` 接口的意义是什么？
3. `SerializerFactory` 解决了什么问题？
4. `SerializerKeys` 为什么要单独抽出来？
5. 为什么 consumer 和 provider 必须使用同一种序列化器？
6. 如果两端序列化器不一致，会出现什么问题？

### SPI 机制

1. 什么是 SPI？它和 API 有什么区别？
2. Java 原生 SPI 的目录为什么是 `META-INF/services/`？
3. 当前项目为什么自定义了 `META-INF/rpc/system/` 和 `META-INF/rpc/custom/`？
4. `SpiLoader` 的 `loaderMap` 保存了什么？
5. `instanceCache` 的作用是什么？
6. 为什么 `SpiLoader` 使用 `ResourceUtil.getResources` 而不是普通文件路径？
7. 如果用户自定义 SPI 和系统 SPI 出现相同 key，应该谁优先？
8. 当前 SPI 实现和 Dubbo SPI 相比还缺少哪些能力？

### 调用链路

1. consumer 调用 `userService.getUser(user)` 后，框架内部发生了什么？
2. `ServiceProxy` 在什么时候选择序列化器？
3. provider 的 `HttpServerHandler` 在什么时候选择序列化器？
4. `RpcConfig.serializer` 是如何影响真实请求链路的？
5. 为什么只改配置文件就能切换序列化器？
6. 如果 consumer 端配置了 `kryo`，provider 端配置了 `jdk`，会发生什么？

### 问题排查

1. `ConnectException: Connection refused` 优先排查什么？
2. JSON 序列化器中 `ObjectMapper` 导错包会出现什么现象？
3. Hessian 下 `Integer cannot be cast to Short` 说明了什么？
4. 如果 SPI key 写错，比如 `rpc.serializer=kyro`，当前框架会如何报错？
5. 如果 SPI 配置文件路径放错，`SerializerFactory` 会出现什么问题？

### 后续扩展

1. 如何在协议头中携带序列化器类型，让服务端自动识别？
2. 如何给 `SerializerFactory` 增加默认兜底逻辑？
3. 如何支持用户自定义序列化器？
4. 如何为每种序列化器设计单元测试？
5. 如何比较不同序列化器的性能和序列化体积？
6. 如何让 SPI 支持懒加载、优先级和覆盖规则？

## 下一版本计划

建议后续新建 `study-log-v0.5.0-日期.md`，继续保持不追加写入旧文件。

v0.5.0 可以考虑聚焦以下方向之一：

1. 服务注册与发现：从本地注册表过渡到注册中心。
2. 协议升级：在请求中携带魔数、版本号、序列化器类型、请求 ID 等信息。
3. 负载均衡：当一个服务有多个 provider 时，选择合适的服务节点。
4. 容错机制：增加重试、失败转移、快速失败等策略。
5. 更完善的 SPI：支持懒加载、自定义扩展目录、覆盖规则和异常提示。
6. 序列化器测试：为 JDK、JSON、Kryo、Hessian 分别补充测试用例。

## 阶段结论

v0.4.0 阶段完成了 RPC 框架序列化器可插拔能力的初步建设。项目从“写死 JDK 序列化器”演进到“通过配置选择序列化器，再通过 SPI 加载具体实现类”。

这一阶段最重要的收获是：RPC 框架中的扩展能力通常不是直接散落在业务代码中，而是通过接口抽象、配置项、工厂类和 SPI 加载机制组合出来的。consumer 和 provider 表面上仍然只是发送和接收请求，但底层序列化方式已经可以通过配置切换。当前使用 `rpc.serializer=kryo` 已经完成真实调用验收，说明序列化器工厂和 SPI 链路已经初步跑通。
