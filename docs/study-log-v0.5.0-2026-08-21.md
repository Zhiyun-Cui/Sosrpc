# Sosrpc 学习日志 v0.5.0

## 基本信息

- 项目名称：Sosrpc
- 当前版本：v0.5.0
- 日志类型：阶段学习记录
- 更新时间：2026-08-21 00:00:00 +08:00
- 学习阶段：注册中心基础实现与优化扩展
- 前置版本：v0.4.0 序列化器与 SPI 机制
- 面向目标：秋招项目梳理、RPC 注册发现机制理解、Etcd 与 ZooKeeper 注册中心对比、服务治理基础能力沉淀
- 参考资料：教学文档 5《注册中心基本实现》、教学文档 6《注册中心优化》

## 本阶段目标

前几个版本已经完成了最小 RPC 调用链路、全局配置、Mock、序列化器和 SPI。那时 consumer 能通过代理对象调用 provider，但 provider 地址仍然带有明显的静态味道：服务地址要么写死，要么依赖配置提前约定。

注册中心阶段要解决的问题是：当服务提供者的数量、地址、上下线状态都开始变化时，consumer 如何知道该调用谁。

本阶段的核心目标有六个：

1. 抽象统一的注册中心接口，让框架不直接绑定某一个中间件。
2. 使用 Etcd 完成服务注册、服务发现和服务注销。
3. 通过 SPI 支持不同注册中心实现的可插拔切换。
4. 增加租约和心跳，让 provider 节点具备存活语义。
5. 增加主动下线、被动下线、消费者缓存和 watch 机制，解决节点变化后的感知问题。
6. 扩展 ZooKeeper 注册中心实现，对比 Etcd 和 ZooKeeper 的模型差异。

这一步不只是“把地址存进 Etcd”。它真正引入的是 RPC 框架里的控制面：provider 把自己的可用状态写到注册中心，consumer 从注册中心读取可用节点，框架再根据这些节点完成真实请求。

## 为什么 RPC 框架需要注册中心

最开始写 RPC 时，consumer 只要知道 provider 的地址就能发请求。例如：

```text
http://localhost:8080
```

这种方式在单机 demo 里很自然，但一旦进入真实服务环境，就会出现几个问题：

- provider 可能有多个实例，例如 8080、8081、8082。
- provider 可能重启，IP 和端口可能变化。
- provider 可能挂掉，consumer 不能继续请求一个失效地址。
- 服务可能有多个版本，consumer 不能随便调错版本。
- 框架后续要做负载均衡、容错、灰度、路由，都需要先知道“有哪些可用服务节点”。

所以注册中心的内核不是“让远程调用像本地一样简单”这句表面话，而是把动态服务拓扑从业务代码里抽出来，交给一个统一的协调组件维护。

可以把 RPC 框架拆成两条面：

```text
控制面：服务注册、服务发现、节点存活、下线感知、缓存刷新
数据面：请求序列化、网络传输、provider 反射调用、响应反序列化
```

注册中心负责控制面。真正的 RPC 请求仍然是 consumer 直连 provider，数据不会经过注册中心。

## 本阶段整体架构

注册中心引入后，调用关系从静态地址变成了下面这样：

```text
ProviderExample
  -> RpcApplication.init()
  -> 初始化 Registry
  -> LocalRegistry.register(接口名, 实现类)
  -> Registry.register(ServiceMetaInfo)
  -> 启动 HTTP Server

ConsumerExample
  -> RpcApplication.init()
  -> ServiceProxyFactory.getProxy(UserService.class)
  -> userService.getUser(user)
  -> ServiceProxy.invoke()
  -> Registry.serviceDiscovery(serviceKey)
  -> 选择一个 ServiceMetaInfo
  -> HTTP POST 调用 provider
```

这里有两个注册表，很容易混：

- `LocalRegistry` 是 provider 本地注册表，保存“接口名 -> 实现类”。它用于 provider 收到请求后找到具体实现类并反射调用。
- `Registry` 是分布式注册中心，保存“服务名/版本 -> provider 地址”。它用于 consumer 找到远程服务节点。

一句话区分：

```text
LocalRegistry 解决 provider 本地怎么执行。
Registry 解决 consumer 远程去哪里调用。
```

## 核心数据模型

本阶段最核心的数据对象是 `ServiceMetaInfo`。

它描述一个 provider 节点的服务元信息：

```text
serviceName     服务接口名，例如 com.achingsoul.example.common.service.UserService
serviceVersion  服务版本，默认 1.0
serviceHost     provider 主机地址，例如 localhost
servicePort     provider 端口，例如 8080
serviceGroup    服务分组，默认 default
```

框架根据这些字段拼出两个 key。

第一个是服务 key：

```java
public String getServiceKey() {
    return String.format("%s:%s", serviceName, serviceVersion);
}
```

例如：

```text
com.achingsoul.example.common.service.UserService:1.0
```

它表示“某个接口的某个版本”。

第二个是服务节点 key：

```java
public String getServiceNodeKey() {
    return String.format("%s/%s:%s", getServiceKey(), serviceHost, servicePort);
}
```

例如：

```text
com.achingsoul.example.common.service.UserService:1.0/localhost:8080
```

它表示这个服务版本下面的某一个 provider 实例。

Etcd 中最终存储路径会带上根路径：

```text
/rpc/com.achingsoul.example.common.service.UserService:1.0/localhost:8080
```

value 是 `ServiceMetaInfo` 的 JSON 字符串。

这个 key 设计很重要，因为它让 consumer 可以用前缀搜索找出同一个服务版本下的所有 provider：

```text
/rpc/com.achingsoul.example.common.service.UserService:1.0/
```

只要以这个前缀查询，就能拿到：

```text
/rpc/.../localhost:8080
/rpc/.../localhost:8081
/rpc/.../localhost:8082
```

后续负载均衡就是从这个列表里选择一个节点。

## 注册中心抽象

`Registry` 接口定义了注册中心应该具备的能力：

```java
void init(RegistryConfig registryConfig);

void register(ServiceMetaInfo serviceMetaInfo) throws Exception;

void unregister(ServiceMetaInfo serviceMetaInfo);

List<ServiceMetaInfo> serviceDiscovery(String serviceKey);

void heartBeat();

void watch(String serviceNodeKey);

void destroy();
```

这些方法对应完整生命周期：

- `init`：根据配置初始化客户端连接。
- `register`：provider 启动时注册服务节点。
- `unregister`：provider 主动下线时删除节点。
- `serviceDiscovery`：consumer 根据服务 key 查询 provider 列表。
- `heartBeat`：provider 定期续期，证明自己还活着。
- `watch`：consumer 监听节点变化，及时清理本地缓存。
- `destroy`：框架关闭时释放连接，并尽量删除本机注册节点。

有了这个接口，`ServiceProxy` 和 `ProviderExample` 不需要知道底层是 Etcd 还是 ZooKeeper。它们只依赖 `Registry` 抽象。

这也是本阶段和 v0.4.0 SPI 的关系：注册中心实现也通过 SPI 发现。

```text
rpc.registryConfig.registry=etcd
  -> RegistryFactory.getInstance("etcd")
  -> SpiLoader
  -> EtcdRegistry

rpc.registryConfig.registry=zookeeper
  -> RegistryFactory.getInstance("zookeeper")
  -> SpiLoader
  -> ZooKeeperRegistry
```

## 配置模型

注册中心配置放在 `RegistryConfig` 中，并嵌套到 `RpcConfig`。

主要字段包括：

```text
registry  注册中心类型，默认 etcd
address   注册中心地址，例如 http://localhost:2379 或 localhost:2181
username  用户名
password  密码
timeout   连接超时时间
```

Etcd 示例：

```properties
rpc.registryConfig.registry=etcd
rpc.registryConfig.address=http://localhost:2379
```

ZooKeeper 示例：

```properties
rpc.registryConfig.registry=zookeeper
rpc.registryConfig.address=localhost:2181
```

配置读取链路是：

```text
application.properties
  -> ConfigUtils.loadConfig
  -> RpcConfig
  -> RpcApplication.init
  -> RegistryFactory
  -> Registry.init
```

## 文档 5：Etcd 注册中心基础实现

文档 5 主要完成最基础的注册中心能力：注册、发现、注销和接入 RPC 调用链路。

### 1. 初始化 Etcd 客户端

`EtcdRegistry#init` 使用 `jetcd` 创建客户端：

```java
client = Client.builder()
        .endpoints(registryConfig.getAddress())
        .connectTimeout(Duration.ofMillis(registryConfig.getTimeout()))
        .build();
kvClient = client.getKVClient();
heartBeat();
```

`Client` 是 Etcd 客户端，`KV` 用来做 key-value 操作。

当前地址通常是：

```text
http://localhost:2379
```

### 2. provider 注册服务

provider 启动时会构造 `ServiceMetaInfo`：

```java
ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
serviceMetaInfo.setServiceName(UserService.class.getName());
serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
registry.register(serviceMetaInfo);
```

Etcd 注册逻辑是：

```text
ServiceMetaInfo
  -> 计算 registerKey
  -> JSON 序列化为 value
  -> 申请 30 秒 lease
  -> put key/value，并绑定 lease
  -> 本地记录 registerKey
```

关键点是 lease。这个节点不是永久节点，而是有 30 秒租约。租约过期后，Etcd 会自动删除对应 key。

### 3. consumer 发现服务

consumer 调用代理方法时，`ServiceProxy` 会根据接口名和版本构造 serviceKey：

```text
com.achingsoul.example.common.service.UserService:1.0
```

然后调用：

```java
registry.serviceDiscovery(serviceMetaInfo.getServiceKey());
```

Etcd 侧会做前缀查询：

```text
/rpc/com.achingsoul.example.common.service.UserService:1.0/
```

查询结果是多个 key-value。每个 value 解析成一个 `ServiceMetaInfo`，返回给 consumer。

当前最基础的节点选择策略是：

```java
ServiceMetaInfo selectedServiceMetaInfo = serviceMetaInfoList.get(0);
```

也就是选第一个 provider。它还不是负载均衡，只是把注册中心打通。

### 4. consumer 调用 provider

拿到 `ServiceMetaInfo` 后，consumer 调用：

```java
selectedServiceMetaInfo.getServiceAddress()
```

例如：

```text
http://localhost:8080
```

然后通过 HTTP POST 发送序列化后的 `RpcRequest`。

所以注册中心只参与“找地址”，不参与后面的业务数据传输。

### 5. provider 注销服务

`unregister` 做的事情是删除 Etcd 中对应 key：

```text
/rpc/serviceName:version/host:port
```

并从本地注册节点集合中移除该 key。

这用于 provider 主动下线。

### 6. 销毁注册中心连接

`destroy` 会遍历本机注册过的 key，逐个删除，然后关闭 `kvClient` 和 `client`。

`RpcApplication.init` 中注册了 JVM shutdown hook：

```java
Runtime.getRuntime().addShutdownHook(new Thread(registry::destroy));
```

这样用户正常停止 provider 时，框架有机会执行下线逻辑。

## 文档 6：注册中心优化

文档 6 在基础注册发现上继续补齐四类能力：

1. 心跳机制。
2. 服务节点下线机制。
3. 消费端服务缓存。
4. ZooKeeper 注册中心扩展。

这些能力的共同目标是让注册中心从“能存地址”变成“能表达节点是否可用”。

## 心跳机制

### 为什么需要心跳

如果 provider 启动时写入一个永久 key，那么 provider 进程崩溃后，这个 key 仍然留在 Etcd。consumer 继续发现到这个地址，就会调用失败。

所以 provider 注册的节点必须带有存活期限。

Etcd 中的做法是 lease：

```text
provider 注册 key，绑定 30 秒 lease
provider 每 10 秒续期
只要续期正常，key 一直存在
如果 provider 崩溃或网络长时间断开，lease 到期，Etcd 自动删除 key
```

这就是被动下线的基础。

### 当前项目的心跳实现

当前 `EtcdRegistry#heartBeat` 使用 Hutool 的 `CronUtil`：

```text
每 10 秒执行一次
遍历 localRegisterNodeKeySet
查询 Etcd 中该 key 是否存在
如果存在，读取 value，反序列化成 ServiceMetaInfo
再次 register(serviceMetaInfo)
```

当前实现的“续期”不是直接调用 Etcd lease keepAlive，而是重新申请一个 30 秒 lease，再次 put 同一个 key。

可以理解为：

```text
重新注册 = 用新的租约覆盖旧 key
```

这在教学阶段能达到近似续期效果，但生产实现更常见的是保留 leaseId 并调用 keepAlive。

### 关键时间线

以 TTL 30 秒、心跳 10 秒为例：

```text
t=0    provider 注册 key，lease=30s
t=10   provider 重新注册，key 绑定新 lease
t=20   provider 重新注册，key 绑定新 lease
t=30   旧 lease 到期，但 key 已经绑定过新 lease，不会消失
```

如果 provider 在 t=12 崩溃：

```text
t=12   provider 崩溃，心跳停止
t=20   不再续期
t=30~42 左右，最后一次 lease 到期
Etcd 删除 key
consumer watch 到 DELETE 事件
consumer 清空本地缓存
```

具体消失时间取决于最后一次成功注册发生在什么时候，不一定刚好是停止后的 30 秒。

### 心跳实现的边界

当前教学实现有几个需要面试时说清楚的边界：

- 使用重新注册模拟续期，而不是 Etcd 原生 keepAlive。
- `localRegisterNodeKeySet` 使用 `HashSet`，如果多线程注册和心跳并发，存在并发安全风险。
- 如果心跳发现 key 已经不存在，当前代码是 `continue`，没有自动重新注册。代码注释说“需要重启节点才能重新注册”，所以不能把它描述成真正的自恢复。
- 每次重新注册都会 grant 新 lease，旧 lease 可能自然过期，资源使用不如 keepAlive 精确。

教学阶段这样写的好处是容易理解：provider 定时确认自己的注册信息还存在，并刷新过期时间。

## 服务节点下线机制

服务下线分两类：主动下线和被动下线。

### 主动下线

主动下线是 provider 正常退出时触发。

链路是：

```text
用户停止 ProviderExample
  -> JVM 执行 shutdown hook
  -> registry.destroy()
  -> 删除 localRegisterNodeKeySet 中记录的 key
  -> Etcd 中节点立即消失
  -> consumer watch 到 DELETE
  -> consumer 清空缓存
```

主动下线的优点是快。provider 一退出，节点就被删掉，consumer 不需要等 lease 到期。

但 shutdown hook 不是万能的。如果机器断电、进程被强杀、JVM 崩溃，hook 可能来不及执行。

### 被动下线

被动下线依赖 lease。

链路是：

```text
provider 异常退出
  -> 没有机会执行 unregister 或 destroy
  -> 心跳停止
  -> lease 到期
  -> Etcd 自动删除 key
  -> consumer watch 到 DELETE
  -> consumer 清空缓存
```

被动下线的优点是最终一定能清理僵尸节点，缺点是有延迟。

这就是注册中心设计中常见的权衡：

```text
主动下线：快，但依赖进程正常退出。
被动下线：稳，但依赖 TTL，有感知延迟。
```

生产环境通常两者都要。

## 消费端服务缓存

### 为什么 consumer 需要缓存

如果每一次 RPC 调用都去注册中心查询，会有几个问题：

- 每次调用多一次网络请求，延迟增加。
- 注册中心压力变大。
- 注册中心短暂抖动时，consumer 可能无法发起调用。
- 高频业务调用会把控制面压力放大。

所以 consumer 通常会把服务发现结果缓存在本地。

当前项目的缓存类是 `RegistryServiceCache`：

```java
List<ServiceMetaInfo> serviceCache;

void writeCache(List<ServiceMetaInfo> newServiceCache) {
    this.serviceCache = newServiceCache;
}

List<ServiceMetaInfo> readCache() {
    return this.serviceCache;
}

void clearCache() {
    this.serviceCache = null;
}
```

`EtcdRegistry#serviceDiscovery` 的逻辑是：

```text
先读本地缓存
如果缓存不为 null，直接返回
如果缓存为 null，查询 Etcd
解析 Etcd 返回结果
为每个服务节点开启 watch
写入本地缓存
返回服务列表
```

这解释了你调试时看到的现象：

```text
第一次 getUser()
  -> cachedServiceMetaInfoList = null
  -> keyValues size = 1
  -> 查询 Etcd，写入缓存

第二次 getUser()
  -> cachedServiceMetaInfoList != null
  -> 直接返回缓存
  -> 不再查 Etcd

停止 provider，等 EtcdKeeper 节点消失
  -> watch 收到 DELETE
  -> clearCache()

第三次 getUser()
  -> cachedServiceMetaInfoList = null
  -> 重新查 Etcd
  -> keyValues size = 0
  -> serviceMetaInfoList size = 0
  -> 抛出 No available service provider
```

所以“provider 结束后 size=0”的含义是：consumer 本地缓存已经被 watch 清掉，下一次发现服务时重新去 Etcd 查询，而 Etcd 中 provider 节点已经不存在。

### 缓存一致性

本地缓存带来了性能，也带来了不一致风险。

如果没有 watch，会出现：

```text
provider 已经下线
Etcd 中 key 已经消失
consumer 本地缓存还保存旧地址
consumer 继续请求旧地址
调用失败
```

所以缓存必须配合变更通知。

当前 Etcd 实现中，consumer 对发现到的每个具体节点 key 调用 `watch(key)`。当某个 key 被删除时，清空整个服务缓存。

教学实现选择“清空整个缓存”，而不是精确删除某个节点，是为了简单可靠。下一次调用会重新查 Etcd，拿到最新列表。

### 当前缓存实现的边界

当前缓存类只有一个 `List<ServiceMetaInfo>` 字段，所以严格说它只适合教学中的单服务测试。

生产级通常要做成：

```text
Map<String, List<ServiceMetaInfo>>
```

key 是 serviceKey，value 是对应服务的节点列表。

否则如果 consumer 同时调用多个接口，一个服务的发现结果可能覆盖另一个服务的缓存。

另外还要考虑：

- 缓存字段可见性，可以使用 `volatile`、锁或并发容器。
- 空列表是否缓存。缓存空列表能保护注册中心，但可能导致新 provider 上线后 consumer 短时间感知不到。
- watch 是否覆盖 PUT 事件。当前 Etcd 实现只在 DELETE 时清缓存，PUT 时不处理。
- watcher 生命周期。当前没有保存 Watcher 对象，也没有在 destroy 时关闭 watcher。

这些都是秋招深挖时可以主动补充的点。

## watch 机制

watch 的本质是让 consumer 从“轮询注册中心”变成“被注册中心推送变化”。

没有 watch 时：

```text
consumer 定时查 Etcd
发现变了再更新缓存
```

有 watch 时：

```text
consumer 查到节点后监听这些 key
Etcd 发现 key 删除或变化
主动通知 consumer
consumer 清空缓存
下一次调用重新查询
```

当前 Etcd watch 逻辑：

```text
serviceDiscovery 查出节点 key
  -> watch(key)
  -> watchingKeySet 去重
  -> Etcd WatchClient 监听
  -> DELETE 事件触发 clearCache()
```

`watchingKeySet` 的作用是防止重复监听同一个 key。否则每次 serviceDiscovery 都可能创建一个新 watcher，最终同一个 key 上堆很多监听器。

当前只处理 `DELETE`：

```java
case DELETE:
    registryServiceCache.clearCache();
    break;
case PUT:
default:
    break;
```

如果 provider 节点内容变了，例如地址、权重、元数据变化，生产实现中也应该处理 PUT 或重新加载。

## EtcdKeeper 中看到节点消失的原理

EtcdKeeper 只是可视化界面，展示的是 Etcd 里的 key。

当 provider 运行时，你会看到类似：

```text
/rpc/com.achingsoul.example.common.service.UserService:1.0/localhost:8080
```

当 provider 正常停止时：

```text
shutdown hook
  -> registry.destroy()
  -> kvClient.delete(key)
  -> EtcdKeeper 中节点消失
```

当 provider 异常停止时：

```text
心跳停止
  -> lease 到期
  -> Etcd 自动删除 key
  -> EtcdKeeper 中节点消失
```

所以节点过一会儿没了，大概率就是 lease 到期或主动下线删除导致的。区别是主动下线更快，被动下线要等 TTL。

## ZooKeeper 注册中心扩展

文档 6 后半部分扩展了 ZooKeeper 注册中心。

ZooKeeper 和 Etcd 都可以做注册中心，但它们的模型不同。

Etcd 是 key-value 模型：

```text
key   = /rpc/serviceKey/host:port
value = ServiceMetaInfo JSON
```

ZooKeeper 是目录树模型：

```text
/rpc/zk
  /com.achingsoul.example.common.service.UserService:1.0
    /localhost:8080
```

当前项目使用 Curator 的 `ServiceDiscovery` 来封装 ZooKeeper 服务发现。

### ZooKeeper 初始化

`ZooKeeperRegistry#init` 做了两件事：

```text
创建 CuratorFramework client
创建 ServiceDiscovery<ServiceMetaInfo>
启动 client 和 serviceDiscovery
```

根路径是：

```text
/rpc/zk
```

### ZooKeeper 注册

注册时构造 `ServiceInstance<ServiceMetaInfo>`：

```text
id      = host:port
name    = serviceKey
address = host:port
payload = ServiceMetaInfo
```

然后调用：

```java
serviceDiscovery.registerService(...)
```

Curator ServiceDiscovery 默认使用动态实例，底层可以利用 ZooKeeper 临时节点表达 provider 存活。

这就是 ZooKeeper 版本不需要自己写心跳的原因：临时节点绑定在 session 上，provider 和 ZooKeeper 的 session 断开并超时后，临时节点会被删除。

### ZooKeeper 发现

consumer 通过：

```java
serviceDiscovery.queryForInstances(serviceKey)
```

查询某个服务 key 下的所有实例，再取 payload 得到 `ServiceMetaInfo`。

当前同样会写入 `RegistryServiceCache`。

### ZooKeeper watch

当前 `ZooKeeperRegistry#watch` 使用 `CuratorCache` 监听某个路径：

```text
forDeletes  -> clearCache()
forChanges  -> clearCache()
```

不过要注意：当前项目中的 `ZooKeeperRegistry#serviceDiscovery` 没有像 Etcd 那样对查询出来的节点调用 `watch()`。也就是说，代码结构上有 watch 方法，但查询链路里还没有真正挂上监听。

这符合当前教学实现的阶段性代码，但面试时不能说它已经完整实现了 ZooKeeper 缓存自动失效。更严谨的表达是：

```text
ZooKeeper 版本已经实现注册、发现、注销和 watch 方法骨架；
但当前 serviceDiscovery 尚未接入 watch，后续应在查询实例后对节点路径注册监听，或直接使用 Curator ServiceCache / PathChildrenCache 管理服务列表缓存。
```

## Etcd 和 ZooKeeper 对比

| 维度 | Etcd | ZooKeeper |
| --- | --- | --- |
| 数据模型 | 扁平 KV，天然适合前缀查询 | 树形目录，天然适合按路径组织节点 |
| 存活语义 | lease + TTL + keepAlive 或重新注册 | session + 临时节点 |
| 客户端库 | jetcd | Curator |
| 服务注册 | put key/value，绑定 lease | registerService，创建服务实例节点 |
| 服务发现 | prefix get | queryForInstances 或读取子节点 |
| 变化监听 | watch key 或 prefix | watcher / CuratorCache / ServiceCache |
| 下线机制 | 主动 delete，异常时 lease 到期删除 | 主动 unregister，异常时 session 超时删除临时节点 |
| 一致性基础 | Raft | ZAB |
| 教学实现重点 | 更清楚地理解 TTL、续期和 key-value 设计 | 更清楚地理解临时节点和服务发现封装 |

一句话总结：

```text
Etcd 版本更适合学习“租约、TTL、前缀查询、watch”。
ZooKeeper 版本更适合学习“临时节点、session、目录树服务发现”。
```

## 完整运行链路复盘

### provider 启动

```text
ProviderExample.main
  -> RpcApplication.init()
  -> 读取 application.properties
  -> 初始化 Registry
  -> 注册 shutdown hook
  -> LocalRegistry.register(UserService, UserServiceImpl)
  -> 构造 ServiceMetaInfo
  -> Registry.register(serviceMetaInfo)
  -> 启动 VertxHttpServer
```

provider 启动后，注册中心里应该出现一个服务节点。

### consumer 第一次调用

```text
ConsumerExample.main
  -> RpcApplication.init()
  -> ServiceProxyFactory.getProxy(UserService.class)
  -> userService.getUser(user)
  -> ServiceProxy.invoke()
  -> 构造 RpcRequest
  -> 从 RegistryFactory 获取注册中心实例
  -> serviceDiscovery(serviceKey)
  -> 缓存为空，查询注册中心
  -> 发现 provider 节点
  -> 写入本地缓存
  -> 选择第一个 provider
  -> HTTP 调用 provider
```

### consumer 第二次调用

```text
userService.getUser(user)
  -> serviceDiscovery(serviceKey)
  -> 读取本地缓存
  -> 不查 Etcd
  -> 直接使用缓存里的 provider 地址
```

这就是为什么调试时第二次不会进入 Etcd 查询分支。

### provider 停止后再次调用

```text
provider 停止
  -> 主动 delete 或 lease 到期
  -> Etcd key 消失
  -> consumer watch 收到 DELETE
  -> 清空缓存
  -> consumer 下一次调用重新查 Etcd
  -> 查询结果 size = 0
  -> 抛出 No available service provider
```

这个过程体现了注册中心、watch 和本地缓存之间的配合。

## 测试与验收思路

### 1. 基础单元测试

可以运行：

```text
RegistryFactoryTest
ServiceMetaInfoTest
```

它们不依赖真实 Etcd 或 ZooKeeper，主要验证：

- SPI 能拿到注册中心实现。
- serviceKey 和 serviceNodeKey 拼接正确。
- serviceAddress 拼接正确。
- ServiceMetaInfo JSON 序列化结构可用。

### 2. Etcd 基础注册发现测试

启动 Etcd 后运行：

```text
RegistryTest
```

重点观察：

- register 后 EtcdKeeper 中出现 `/rpc/.../localhost:8080`。
- serviceDiscovery 能查到 1 个节点。
- unregister 后节点消失。
- heartbeat 测试中节点不会在 30 秒后自动消失。

### 3. Etcd 集成测试

如果使用带开关的集成测试，需要加 VM options：

```text
-Detcd.integration=true
```

然后运行：

```text
EtcdRegistryIntegrationTest
```

这个测试适合验证真实 Etcd 环境下注册、发现、注销链路能跑通。

### 4. provider + consumer 真实调用

运行顺序：

```text
1. 启动 Etcd
2. 启动 EtcdKeeper
3. 启动 ProviderExample
4. 打开 http://127.0.0.1:8081/etcdkeeper/
5. 确认 /rpc 下出现 UserService 节点
6. 启动 ConsumerExample
7. 控制台输出 achingsoul
```

如果 consumer 连续调用三次，可以观察：

```text
第一次：查 Etcd，写缓存
第二次：走缓存
停止 provider 并等待节点消失
第三次：缓存被清空，重新查 Etcd，查不到 provider
```

### 5. ZooKeeper 测试

切换配置：

```properties
rpc.registryConfig.registry=zookeeper
rpc.registryConfig.address=localhost:2181
```

运行顺序：

```text
1. 启动 ZooKeeper
2. 启动 ProviderExample
3. 确认 ZooKeeper 中出现 /rpc/zk 下的服务节点
4. 启动 ConsumerExample
5. 控制台输出 achingsoul
6. 停止 provider，观察临时节点消失
```

ZooKeeper 版本重点观察的是临时节点和 session 机制，而不是 Etcd 的 lease。

## 常见问题复盘

### 1. EtcdKeeper 打开只有 CMD，没有图形界面

EtcdKeeper 本身是一个 Web 服务，不是桌面 GUI。双击或运行它会出现命令行窗口，真正的界面在浏览器里：

```text
http://127.0.0.1:8081/etcdkeeper/
```

命令行窗口要保持打开，否则 Web 服务会停止。

### 2. 浏览器提示 127.0.0.1 拒绝连接

说明对应端口没有服务在监听。

排查顺序：

```text
先确认 Etcd 是否启动在 2379
再确认 EtcdKeeper 是否启动在 8081
再确认浏览器 URL 是否是 /etcdkeeper/
```

### 3. EtcdKeeper 页面卡住

可能原因：

- EtcdKeeper 正在连 Etcd，但 Etcd 没启动或 2379 不通。
- 浏览器页面加载了，但 EtcdKeeper 后端没有成功响应。
- provider 运行时占用了某些资源，但通常 provider 本身不会让 EtcdKeeper 卡住。

核心判断方式是分别检查：

```text
Etcd:       http://127.0.0.1:2379
EtcdKeeper: http://127.0.0.1:8081/etcdkeeper/
Provider:   http://localhost:8080
```

### 4. consumer 查到 keyValues size = 0

如果是第一次调用就 `size = 0`，通常说明 provider 没注册成功。

如果是 provider 停止、EtcdKeeper 里的节点已经消失后，第三次调用看到 `size = 0`，这是正确现象，说明：

```text
provider 节点已下线
consumer 缓存已清空
consumer 重新查 Etcd
Etcd 中没有可用 provider
```

### 5. 调试器提示跳过断点

如果 IDEA 提示“已跳过断点，因为它发生在调试器评估内部”，通常是调试器为了展示变量调用了对象的 `toString()`，而这个 `toString()` 又触发了代理逻辑或异常。

它不是注册中心本身的问题。遇到这种情况，可以少展开代理对象，或者直接在关键代码行打断点观察普通变量。

## 秋招表达版本

### 30 秒版本

可以这样讲：

> 我在 Sosrpc 中实现了注册中心模块，把 provider 地址从静态配置改成动态注册发现。provider 启动时会把服务名、版本、地址和端口注册到 Etcd，consumer 调用代理方法时根据 serviceKey 从注册中心发现可用节点，再发起真实 RPC 请求。后续我又补了租约心跳、主动下线、异常下线、本地服务缓存和 watch 缓存失效，并通过 SPI 扩展了 ZooKeeper 注册中心。

### 1 分钟版本

可以这样讲：

> 注册中心在我的 RPC 框架里承担控制面职责。provider 启动后会先注册本地实现类，再把 `ServiceMetaInfo` 注册到分布式注册中心；consumer 通过动态代理调用接口时，会根据接口名和版本拼出 serviceKey，从注册中心查询 provider 列表，并选择一个节点发起 HTTP 调用。基础实现用 Etcd 的 KV 和前缀查询完成注册发现，节点 key 设计为 `/rpc/serviceName:version/host:port`，value 是服务元信息 JSON。优化阶段引入 30 秒 lease 和 10 秒心跳，保证 provider 异常退出后节点能自动过期；同时通过 shutdown hook 做主动下线。consumer 侧增加本地缓存减少注册中心访问，并通过 watch 监听节点 DELETE 事件来清空缓存。最后我用同一套 `Registry` 接口和 SPI 机制扩展了 ZooKeeper 版本，对比了 Etcd lease 和 ZooKeeper 临时节点两种存活模型。

### 深挖版本

可以这样讲：

> 我理解注册中心不是 RPC 的数据转发组件，而是服务拓扑的协调组件。consumer 和 provider 的真实请求仍然是点对点完成，注册中心只负责维护服务节点的元信息和存活状态。在实现上，我抽象了 `Registry` 接口，把初始化、注册、注销、发现、心跳、监听和销毁纳入同一生命周期；再通过 `RegistryFactory` 和自定义 SPI 让 `etcd`、`zookeeper` 这种实现可以按配置切换。Etcd 版本里，我使用 `/rpc/serviceKey/host:port` 作为节点路径，用前缀查询获得同一个服务版本的多个 provider，用 lease 表达节点存活，用定时任务模拟续期，用 watch 配合 consumer 本地缓存解决缓存失效问题。ZooKeeper 版本则使用 Curator ServiceDiscovery 和临时节点语义，减少手写心跳。这个阶段让我真正理解了服务发现、健康状态、缓存一致性和注册中心可用性之间的关系。

## 面试深挖问题

### 注册中心基础

1. RPC 为什么不能一直写死 provider 地址？
2. 注册中心在 RPC 框架里是控制面还是数据面？
3. provider 注册的是接口实现类，还是服务地址？
4. `LocalRegistry` 和 `Registry` 的区别是什么？
5. `ServiceMetaInfo` 为什么要包含 serviceName、version、host、port？
6. serviceKey 和 serviceNodeKey 分别解决什么问题？
7. 为什么 Etcd 查询前缀结尾要加 `/`？
8. 当前为什么只选第一个 provider？后续如何扩展负载均衡？

### Etcd 机制

1. Etcd 的 key-value 模型如何映射 RPC 服务节点？
2. lease 是什么？为什么服务注册节点要绑定 lease？
3. TTL 30 秒、心跳 10 秒的含义是什么？
4. 当前项目里的续期为什么说是“重新注册模拟续期”？
5. Etcd 原生 keepAlive 和重新 register 有什么区别？
6. provider 正常退出和异常退出时，Etcd 节点分别如何消失？
7. 如果 Etcd 短暂不可用，provider 和 consumer 会受到什么影响？
8. 注册中心不可用时，已经缓存的 consumer 是否还能继续调用？

### 缓存与 watch

1. consumer 为什么需要本地服务缓存？
2. 本地缓存会带来什么一致性问题？
3. watch 监听的是 serviceKey 还是 serviceNodeKey？
4. 为什么 DELETE 事件要清空缓存？
5. 为什么不是每次 provider 变化都立刻更新缓存，而是清空后下次重查？
6. 当前 `RegistryServiceCache` 为什么只能算教学实现？
7. 如果要支持多个服务接口，缓存结构应该怎么改？
8. 如果大量 consumer 同时 watch 到删除并重新查询，可能出现什么问题？

### 下线与故障

1. 主动下线和被动下线有什么区别？
2. shutdown hook 一定会执行吗？
3. 被动下线为什么存在延迟？
4. lease TTL 设置太长或太短分别有什么问题？
5. provider 停止后，为什么 consumer 第三次调用才看到 `size = 0`？
6. 如果 provider 假死但进程还在，当前机制能否发现？
7. 如果 provider HTTP 服务挂了但心跳线程还在，会发生什么？
8. 生产环境如何做更准确的健康检查？

### ZooKeeper 对比

1. ZooKeeper 的临时节点和 Etcd lease 有什么相同点？
2. ZooKeeper session 断开后节点会立即删除吗？
3. Curator ServiceDiscovery 帮我们封装了什么？
4. ZooKeeper 为什么不需要像 Etcd 那样手写心跳？
5. Etcd 的前缀查询和 ZooKeeper 的子节点查询有什么差异？
6. 当前项目的 ZooKeeper watch 实现有什么边界？
7. 如果要补全 ZooKeeper 缓存失效，应该怎么做？
8. Etcd 和 ZooKeeper 分别适合怎样的服务发现场景？

### 工程设计

1. 为什么注册中心也要通过 SPI 扩展？
2. `RegistryFactory` 和 `SerializerFactory` 的设计思想有什么相同点？
3. 为什么 `RpcApplication` 要统一初始化 registry？
4. 为什么 shutdown hook 注册在 `RpcApplication.init` 中？
5. 如果 consumer 和 provider 配置了不同注册中心，会发生什么？
6. 如果 provider 注册到 ZooKeeper，consumer 从 Etcd 查询，会发生什么？
7. 如何让注册中心支持负载均衡、重试和容错？
8. 如何把注册中心扩展成更接近 Dubbo/Nacos 的服务治理能力？

## 生产级改进方向

当前实现是教学阶段，主链路已经能跑通，但距离生产级注册中心还有差距。

可以从这些方向继续演进：

1. 服务缓存改成 `Map<String, List<ServiceMetaInfo>>`，支持多个 serviceKey。
2. 缓存字段使用并发容器或 `volatile`，保证多线程可见性。
3. Etcd 使用原生 keepAlive 续租，而不是反复重新注册。
4. 保存 leaseId 和 watcher 对象，便于精确续期和关闭监听。
5. watch 支持前缀监听，处理 PUT、DELETE、更新等事件。
6. provider 健康检查不仅检查进程，还要检查端口和业务接口是否可用。
7. serviceKey 加入 group，避免同名同版本不同分组冲突。
8. consumer 支持负载均衡，例如轮询、随机、一致性哈希。
9. consumer 支持失败重试、失败转移、快速失败和熔断。
10. 注册中心支持多地址和高可用配置。
11. ZooKeeper 查询链路接入 watch 或使用 Curator ServiceCache。
12. 配置错误时给出更明确异常，例如 registry key 不存在、地址不可达、认证失败。

## 本阶段收获

v0.5.0 阶段完成了 RPC 框架从静态地址调用到动态注册发现的关键升级。

文档 5 解决的是基础能力：

```text
provider 能注册
consumer 能发现
provider 能注销
RPC 调用链路能通过注册中心找到地址
注册中心实现能通过 SPI 切换
```

文档 6 解决的是稳定性和扩展性：

```text
provider 活着时节点持续存在
provider 正常退出时节点主动删除
provider 异常退出时节点最终过期
consumer 不必每次都查注册中心
节点变化后 consumer 缓存能失效
框架能扩展 ZooKeeper 注册中心
```

这阶段最重要的理解是：注册中心不是简单的 key-value 存储，而是 RPC 框架服务治理能力的入口。它维护的是服务拓扑和节点存活状态，决定了 consumer 能否找到正确、可用、及时更新的 provider。

面向秋招，可以把这一阶段讲成一个从“能跑”到“更可靠”的演进过程：

```text
静态地址
  -> Etcd 注册发现
  -> SPI 可插拔注册中心
  -> lease + 心跳
  -> 主动/被动下线
  -> consumer 本地缓存
  -> watch 缓存失效
  -> ZooKeeper 扩展
```

如果面试官继续追问，就围绕三个核心展开：

- 存活性：provider 是否真的可用，节点什么时候应该消失。
- 一致性：consumer 本地缓存什么时候更新，如何避免旧地址。
- 扩展性：注册中心实现、负载均衡、容错、健康检查如何继续插拔。

这才是注册中心这一块真正的内核。
