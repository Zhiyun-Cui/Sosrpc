# Sosrpc 学习日志 v0.3.0

## 基本信息

- 项目名称：Sosrpc
- 当前版本：v0.3.0
- 日志类型：阶段学习记录
- 更新时间：2026-08-17 20:29:27 +08:00
- 学习阶段：接口 Mock
- 前置版本：v0.2.0 全局配置加载
- 面向目标：秋招项目梳理、RPC 框架可测试性理解、动态代理扩展能力沉淀
- 参考资料：教学文档 3《接口 Mock》

## 本阶段目标

v0.1.0 阶段已经完成简易 RPC 调用链路，v0.2.0 阶段增加了全局配置加载能力。v0.3.0 阶段关注接口 Mock，用配置控制 consumer 当前走真实远程调用，还是走本地模拟调用。

本阶段要理解的核心不是“mock 返回真实业务对象”，而是：

1. 当 provider 没有启动或远程服务不可用时，consumer 仍然可以拿到接口代理对象。
2. consumer 不需要关心代理对象背后是真实 RPC 调用还是 mock 调用。
3. mock 代理不发网络请求，而是根据接口方法返回值类型生成默认值。
4. `rpc.mock=true` 用于验证 mock 分支是否生效。
5. `rpc.mock=false` 用于回到真实 provider 调用链路。

## 当前模块结构

本阶段仍沿用已有模块结构：

- `example-common`：公共接口和模型，新增或保留用于测试 mock 的接口方法。
- `example-consumer`：服务消费者，通过配置开启 mock 并运行 `ConsumerExample`。
- `example-provider`：服务提供者，真实调用模式下需要启动；mock 模式下可以不启动。
- `rpc-core`：RPC 核心模块，新增 mock 配置字段、mock 代理和代理工厂分流逻辑。

## 已完成内容

### 1. 在全局配置中增加 mock 开关

在 `RpcConfig` 中增加：

```java
private boolean mock = false;
```

默认值是 `false`，表示默认走真实 RPC 调用。

在 consumer 的 `application.properties` 中通过配置控制：

```properties
rpc.mock=true
```

当配置为 `true` 时，consumer 获取到的接口代理对象会走 mock 分支。

### 2. 新增 MockServiceProxy

在 `rpc-core` 的 `proxy` 包下新增 `MockServiceProxy`，实现 `InvocationHandler`。

核心逻辑：

```java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Class<?> methodReturnType = method.getReturnType();
    log.info("mock invoke {}", method.getName());
    return getDefaultObject(methodReturnType);
}
```

它和真实 `ServiceProxy` 的区别是：

- `ServiceProxy` 会构造 `RpcRequest`，序列化后通过 HTTP 请求 provider。
- `MockServiceProxy` 不会发网络请求，只根据方法返回值类型返回默认值。

### 3. 根据返回值类型生成默认值

`MockServiceProxy#getDefaultObject` 当前支持部分基本类型：

- `boolean` 返回 `false`。
- `short` 返回 `(short) 0`。
- `int` 返回 `0`。
- `long` 返回 `0L`。
- 非基本类型默认返回 `null`。

因此：

```java
User getUser(User user)
```

返回类型是对象类型 `User`，mock 默认返回 `null`。

而：

```java
default short getNumber()
```

返回类型是基本类型 `short`，mock 默认返回 `0`。

这解释了本阶段调试中出现的结果：

```text
mock invoke getUser
user == null
mock invoke getNumber
0
```

这不是失败，而是 mock 默认值逻辑生效。

### 4. 改造 ServiceProxyFactory

`ServiceProxyFactory#getProxy` 现在会根据全局配置决定返回哪种代理对象：

```java
if (RpcApplication.getRpcConfig().isMock()) {
    return getMockProxy(serviceClass);
}
```

分流结果：

```text
rpc.mock=true  -> UserService 代理对象 + MockServiceProxy
rpc.mock=false -> UserService 代理对象 + ServiceProxy
```

注意：consumer 拿到的编译期类型始终是 `UserService`。

区别只在于 JDK 动态代理对象背后的 `InvocationHandler` 不同：

- mock 模式：`MockServiceProxy#invoke`
- 真实模式：`ServiceProxy#invoke`

### 5. ConsumerExample 验证 mock 调用

`ConsumerExample` 仍然只通过接口调用服务：

```java
UserService userService = ServiceProxyFactory.getProxy(UserService.class);
User newUser = userService.getUser(user);
long number = userService.getNumber();
```

consumer 不需要写：

```java
new MockServiceProxy()
```

也不需要判断当前是否启动 provider。是否走 mock 完全由配置和代理工厂决定。

## 验收结果

本阶段 mock 功能已经生效。

运行输出：

```text
rpc init, config: RpcConfig(name=Sosrpc, version=2.0, serverHost=localhost, serverPort=8081, mock=true)
mock invoke getUser
user == null
mock invoke getNumber
0
进程已结束，退出代码为 0
```

该结果说明：

1. `rpc.mock=true` 被正确读取到 `RpcConfig`。
2. `ServiceProxyFactory` 根据配置返回了 mock 代理。
3. 调用 `getUser` 时进入了 `MockServiceProxy`，没有请求 provider。
4. `getUser` 返回类型是 `User`，mock 默认返回 `null`。
5. 调用 `getNumber` 时同样进入 mock 代理。
6. `getNumber` 返回类型是 `short`，mock 默认返回 `0`。
7. 程序正常结束，说明 mock 模式可以脱离 provider 运行 consumer。

本阶段验收指标：

- 配置文件中存在 `rpc.mock=true`。
- 运行 consumer 时日志出现 `mock invoke getUser`。
- 不启动 provider 时 consumer 也能正常结束。
- `getUser` 输出 `user == null`。
- `getNumber` 输出 `0`。
- 如果将 `rpc.mock=false`，则应回到真实 RPC 调用，需要先启动 provider。

## 问题复盘

### 1. setName 后为什么仍然 user == null

在 `ConsumerExample` 中执行：

```java
user.setName("achingsoul");
```

只是设置了请求参数对象的值。

但在 mock 模式下，调用不会发送到 provider，也不会执行 `UserServiceImpl#getUser`。mock 代理只看方法返回类型，不关心入参内容。

因此：

```java
User newUser = userService.getUser(user);
```

最终返回的是 `MockServiceProxy` 生成的默认值 `null`，所以输出：

```text
user == null
```

### 2. mock 模式和真实模式的区别

真实模式：

```text
Consumer -> ServiceProxy -> HTTP 请求 -> Provider -> UserServiceImpl -> 返回 User
```

mock 模式：

```text
Consumer -> MockServiceProxy -> 根据返回类型生成默认值
```

所以 mock 模式验证的是“接口调用流程能否在无 provider 情况下继续执行”，不是验证真实业务结果。

### 3. 接口代理对象是什么意思

`ServiceProxyFactory.getProxy(UserService.class)` 返回的始终是一个实现了 `UserService` 接口的 JDK 动态代理对象。

consumer 只关心：

```java
UserService userService
```

但代理对象背后的处理器不同：

- `mock=true`：背后是 `MockServiceProxy`。
- `mock=false`：背后是 `ServiceProxy`。

因此更准确的表达是：

> consumer 拿到的始终是 `UserService` 接口代理对象；mock=true 时，这个代理对象内部使用 `MockServiceProxy` 处理方法调用，mock=false 时内部使用 `ServiceProxy` 处理方法调用。

### 4. getNumber 为什么输出 0 而不是 1

`UserService#getNumber` 虽然有默认实现：

```java
default short getNumber() {
    return 1;
}
```

但在 JDK 动态代理中，调用接口方法时会进入 `InvocationHandler#invoke`。mock 模式下进入的是 `MockServiceProxy#invoke`，它不会执行接口默认方法体，而是根据返回类型生成默认值。

所以 `short` 的 mock 默认值是 `0`，不是接口默认实现里的 `1`。

## 秋招表达版本

可以这样描述本阶段项目：

> 在全局配置加载的基础上，我为 RPC 框架增加了接口 Mock 能力。通过在 `RpcConfig` 中增加 `mock` 开关，并在 `ServiceProxyFactory` 中根据配置选择真实代理或 mock 代理，使 consumer 在不修改业务调用代码的情况下，可以在真实远程调用和本地模拟调用之间切换。mock 代理基于 JDK 动态代理实现，不发起网络请求，而是根据接口方法的返回值类型生成默认返回值。这样在 provider 未启动或远程服务不可用时，consumer 仍然可以完成接口调用流程，降低前期开发和测试成本。

这段适合用来说明项目的“可测试性”和“动态代理扩展点”。

## 当前能力边界

当前 mock 能力仍是教学阶段的简易实现：

- 对象类型统一返回 `null`，不会自动构造对象。
- 只处理了部分基本类型，例如 `boolean`、`short`、`int`、`long`。
- 没有处理 `double`、`float`、`byte`、`char` 等类型。
- 没有根据方法名生成更贴近业务的 mock 数据。
- 没有支持集合、数组、泛型对象等复杂返回类型。
- 没有支持用户自定义 mock 规则。
- 接口默认方法在 mock 模式下不会执行其默认实现。
- mock 只用于 consumer 本地开发辅助，不代表真实远程调用成功。

## 面试深挖问题

### Mock 基础

1. 什么是 mock？它解决了什么问题？
2. 为什么 RPC 框架可以支持 mock？
3. mock 模式和真实 RPC 调用模式有什么区别？
4. mock 返回默认值的意义是什么？
5. mock 功能为什么能降低开发和测试成本？

### 动态代理

1. `MockServiceProxy` 为什么要实现 `InvocationHandler`？
2. JDK 动态代理对象和 `InvocationHandler` 是什么关系？
3. `mock=true` 和 `mock=false` 时，代理对象有什么相同点和不同点？
4. consumer 为什么不需要关心当前是真实调用还是 mock 调用？
5. 接口默认方法在动态代理中会发生什么？

### 代理工厂

1. `ServiceProxyFactory` 在 mock 功能中承担什么职责？
2. 为什么不把 mock 判断写在 `ConsumerExample` 里？
3. 为什么不把 mock 逻辑直接写进 `ServiceProxy`？
4. 将真实代理和 mock 代理拆成两个类有什么好处？
5. 这种工厂分流设计体现了什么设计思想？

### 默认值生成

1. `getDefaultObject` 为什么要根据返回类型生成默认值？
2. 为什么对象类型默认返回 `null`？
3. 为什么 `getNumber` 返回 `0` 而不是接口默认方法中的 `1`？
4. 如果返回类型是 `List<User>`，当前 mock 会返回什么？
5. 如何扩展 mock，让对象类型返回一个带默认字段的对象？

### 配置驱动

1. 为什么用 `rpc.mock=true` 控制 mock 开关？
2. 如果配置文件里不写 `rpc.mock`，默认会走哪种模式？
3. 配置项是如何从 `application.properties` 映射到 `RpcConfig` 的？
4. mock 配置应该由 consumer 控制还是 provider 控制？
5. 在真实项目中，mock 配置是否应该允许线上开启？

### 框架演进

1. 如何支持更复杂的 mock 数据生成？
2. 是否可以为不同接口或不同方法配置不同 mock 返回值？
3. 如何结合 JSON 文件配置 mock 响应？
4. 如何结合 Faker 生成更真实的测试数据？
5. mock 能否和单元测试、集成测试结合？
6. mock 模式和服务降级有什么区别？

## 下一版本计划

建议后续新建 `study-log-v0.4.0-日期.md`，不要追加写入本文件。

v0.4.0 可以考虑聚焦以下方向之一：

1. 序列化器可插拔：通过配置选择不同序列化实现。
2. SPI 机制：让框架扩展点可以按名称动态加载。
3. 更完善的 mock：支持对象、集合、字符串、浮点数等返回值。
4. 服务注册与发现：从硬编码或配置地址过渡到注册中心。
5. 错误处理：区分 mock 返回、远程调用失败、服务不存在等不同情况。
6. 测试用例：为 `mock=true` 和 `mock=false` 分别设计验收测试。

## 阶段结论

v0.3.0 阶段完成了接口 Mock 能力。项目从 v0.2.0 的“配置化真实调用”进一步演进为“配置驱动的调用模式切换”。

这一阶段最重要的收获是：RPC consumer 面向接口编程，通过代理工厂获取接口代理对象；至于代理对象背后是真实远程调用还是本地 mock，由框架配置和代理工厂统一决定。这样既保持了业务代码的稳定，也为后续测试、联调、降级和扩展能力打下基础。
