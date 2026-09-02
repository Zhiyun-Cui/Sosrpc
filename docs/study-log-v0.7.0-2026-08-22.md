# Sosrpc 学习日志 v0.7.0

## 基本信息

- 项目名称：Sosrpc
- 当前版本：v0.7.0
- 日志类型：阶段学习记录
- 更新时间：2026-08-22 00:00:00 +08:00
- 学习阶段：负载均衡
- 前置版本：v0.6.0 自定义协议与 TCP 网络传输
- 面向目标：秋招项目梳理、服务治理能力表达、负载均衡算法理解、注册中心与调用链路整合
- 参考资料：教学文档 8《负载均衡》

## 本阶段目标

在 v0.5.0 和 v0.6.0 阶段，项目已经完成了注册中心、服务发现、服务缓存、节点下线、心跳机制、自定义协议以及 TCP 网络传输。也就是说，consumer 已经不再依赖写死的 provider 地址，而是可以从 Etcd 或 ZooKeeper 中查询可用服务节点，然后通过自定义 TCP 协议发起远程调用。

但是这里还有一个很核心的问题：

```text
注册中心可能返回多个 provider 节点，
consumer 到底应该调用哪一个？
```

如果 consumer 每次都选择列表中的第一个节点，那么即使注册中心里存在多个服务实例，实际流量仍然会集中打到某一个 provider 上。这样多节点部署就没有真正发挥作用，系统也无法做到请求分摊。

本阶段要解决的就是这个问题：在服务发现之后、发起 TCP 请求之前，引入负载均衡模块，让 consumer 能够从多个服务提供者中按照一定策略选择一个最终调用节点。

本阶段完成的核心内容包括：

1. 抽象 `LoadBalancer` 负载均衡接口。
2. 实现轮询、随机、一致性哈希三种负载均衡策略。
3. 修复一致性哈希实现中的虚拟节点与哈希环问题。
4. 使用 SPI 机制管理负载均衡器扩展点。
5. 在 `RpcConfig` 中增加 `loadBalancer` 配置项。
6. 在 `ServiceProxy` 中把负载均衡接入真实 RPC 调用链路。
7. 编写 `LoadBalancerTest` 测试算法行为与 SPI 加载。
8. 通过 Etcd + 两个 provider + 一个 consumer 验证实际调用效果。

这一阶段的重点，不是单纯写几个算法类，而是把注册中心返回的“服务节点列表”变成真正可用的“服务治理能力”。

## 为什么注册中心之后必须有负载均衡

注册中心解决的是“有哪些服务可用”的问题，负载均衡解决的是“这次请求具体调谁”的问题。

二者看起来都和服务地址有关，但关注点不同：

```text
注册中心：
  负责保存 provider 地址
  负责让 consumer 发现 provider
  关注服务实例是否存在、是否下线、是否过期

负载均衡：
  负责从多个 provider 中选一个
  负责让流量尽量合理分布
  关注调用策略、请求稳定性、资源利用率
```

如果只有注册中心，没有负载均衡，那么 consumer 最多只能做到：

```text
查到一批 provider -> 随便拿一个用
```

这在功能上能跑通，但在系统设计上是不完整的。因为一旦线上有多个 provider 实例，consumer 就必须回答几个问题：

- 是不是每个 provider 都应该承担一部分请求？
- 节点性能不一样时，是否应该让强节点多承担一些？
- 同一个用户或同一种请求是否应该稳定打到同一个节点？
- 某个节点下线后，是否应该尽量少影响已有请求映射？
- 服务列表发生变化后，consumer 是否能自然适配？

这些问题都属于负载均衡和服务治理的范畴。

所以在 RPC 框架中，注册中心和负载均衡通常是一前一后的关系：

```text
consumer 发起接口调用
  -> 动态代理构造 RpcRequest
  -> 注册中心服务发现，拿到 provider 列表
  -> 负载均衡器选择一个 provider
  -> TCP 客户端向该 provider 发送请求
  -> provider 执行业务方法并返回结果
```

这也是本阶段项目架构的核心变化。

## 本阶段架构变化

v0.6.0 阶段的调用链路大致是：

```text
ConsumerExample
  -> ServiceProxy
  -> Registry.serviceDiscovery()
  -> 选择 provider
  -> VertxTcpClient.doRequest()
```

引入负载均衡后，链路变成：

```text
ConsumerExample
  -> ServiceProxy
  -> Registry.serviceDiscovery(serviceKey)
  -> List<ServiceMetaInfo>
  -> LoadBalancerFactory.getInstance(rpcConfig.getLoadBalancer())
  -> LoadBalancer.select(requestParams, serviceMetaInfoList)
  -> selectedServiceMetaInfo
  -> VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo)
```

也就是在“服务发现”和“网络请求”之间插入了一个选择层：

```text
注册中心返回的是候选集合。
负载均衡返回的是最终目标。
```

这个设计非常重要，因为它让负载均衡策略可以独立扩展。以后如果要增加加权轮询、最少连接数、响应时间优先、区域优先、灰度路由等能力，都不需要大改调用链路，只需要新增一个实现类，再通过 SPI 注册即可。

## 项目实际代码结构

本阶段主要涉及以下代码：

```text
rpc-core
  src/main/java/com/achingsoul/myrpc/loadbalancer
    LoadBalancer.java
    RoundRobinLoadBalancer.java
    RandomLoadBalancer.java
    ConsistentHashLoadBalancer.java
    LoadBalancerKeys.java
    LoadBalancerFactory.java

  src/main/resources/META-INF/rpc/system
    com.achingsoul.sosrpc.loadbalancer.LoadBalancer

  src/main/java/com/achingsoul/myrpc/config
    RpcConfig.java

  src/main/java/com/achingsoul/myrpc/proxy
    ServiceProxy.java

  src/test/java/com/achingsoul/myrpc/loadbalancer
    LoadBalancerTest.java

example-consumer
  src/main/resources/application.properties

example-provider
  src/main/resources/application.properties
```

从包结构上看，负载均衡被放在 `rpc-core` 中是合理的。因为它不是 provider 或 consumer 示例工程的业务逻辑，而是 RPC 框架本身的基础能力。

## 负载均衡接口设计

项目中抽象出的核心接口是：

```java
public interface LoadBalancer {

    ServiceMetaInfo select(Map<String, Object> requestParams,
                           List<ServiceMetaInfo> serviceMetaInfoList);
}
```

这个接口的输入有两部分：

1. `requestParams`：当前请求的特征参数。
2. `serviceMetaInfoList`：注册中心发现到的服务节点列表。

输出是一个 `ServiceMetaInfo`，也就是本次最终要调用的 provider。

这里有一个很好的设计点：接口没有只传服务列表，而是额外传入了 `requestParams`。

原因是不同负载均衡算法需要的信息不一样：

- 轮询只关心服务列表。
- 随机只关心服务列表。
- 一致性哈希需要根据请求特征计算哈希值。
- 未来如果做用户维度路由，可能需要 `userId`。
- 未来如果做接口维度路由，可能需要 `methodName`。
- 未来如果做灰度发布，可能需要请求标签、版本、环境等参数。

所以 `requestParams` 给了负载均衡策略更大的扩展空间。

在当前项目中，`ServiceProxy` 传入的是：

```java
Map<String, Object> requestParams = new HashMap<>();
requestParams.put("methodName", rpcRequest.getMethodName());
```

这意味着一致性哈希会基于方法名做稳定映射。例如连续调用 `getUser`，它的请求参数不变，计算出的哈希值也不变，因此会稳定选择同一个 provider。

这也解释了调试时看到的现象：使用一致性哈希时，三次调用都打到同一个端口，并不一定是巧合，而是算法特性。如果三次请求的 `methodName` 都一样，它们就会走到同一个哈希点上。

## 轮询负载均衡

轮询是最容易理解的一种负载均衡策略。它的核心思想是：

```text
第 1 次请求选第 0 个节点
第 2 次请求选第 1 个节点
第 3 次请求选第 2 个节点
...
到末尾后重新从第 0 个节点开始
```

项目中的 `RoundRobinLoadBalancer` 使用 `AtomicInteger` 保存当前下标：

```java
private final AtomicInteger currentIndex = new AtomicInteger(0);
```

选择节点时，根据当前计数对服务列表长度取模：

```java
int index = currentIndex.getAndIncrement() % serviceMetaInfoList.size();
return serviceMetaInfoList.get(index);
```

它的优点是非常直观，每个 provider 在理想情况下会平均接收请求。

它适合下面这种场景：

- provider 性能差不多。
- 每个请求耗时差不多。
- 不需要根据用户或请求内容做固定映射。

但它也有局限：

- 如果不同 provider 性能不同，普通轮询无法照顾强弱差异。
- 如果某些请求特别慢，轮询并不知道节点当前压力。
- 当前实现中 `AtomicInteger` 长时间运行可能存在整数溢出问题，教学项目里影响不大，生产中可以用取绝对值、CAS 或更安全的下标更新方式处理。

这类问题是秋招面试中很容易被追问的点：轮询不是万能的，它只是最基础、最均匀、最容易实现的策略。

## 随机负载均衡

随机策略的核心思想是：

```text
每次从服务列表中随机选一个 provider。
```

项目中的 `RandomLoadBalancer` 使用 `Random` 生成随机下标：

```java
int randomIndex = random.nextInt(serviceMetaInfoList.size());
return serviceMetaInfoList.get(randomIndex);
```

它的优点是实现简单，并且在请求量足够大时，整体流量会趋近平均分布。

但随机也有明显特征：

- 少量请求时结果可能看起来不均匀。
- 连续几次打到同一个 provider 是正常现象。
- 它不保证顺序，也不保证短时间内绝对均衡。

所以测试随机负载均衡时，不应该期望它一定是 `8080 -> 8085 -> 8080` 这种规律。只要选中的节点来自可用服务列表，就说明算法基本正确。真正要验证均匀性，需要更多请求样本。

## 一致性哈希负载均衡

一致性哈希是本阶段最值得认真理解的算法。

普通哈希的做法通常是：

```text
index = hash(request) % providerCount
```

这种方式在 provider 数量固定时没有问题。但是当 provider 上线或下线时，`providerCount` 会变化，取模结果会大面积改变。

例如原来有 3 个节点：

```text
hash(request) % 3
```

后来变成 4 个节点：

```text
hash(request) % 4
```

绝大多数请求都会重新映射到别的节点。这对缓存、长连接、会话粘滞等场景非常不友好。

一致性哈希要解决的就是这个问题。

它把整个哈希空间看成一个环：

```text
0 -------------------- MAX
|                      |
|                      |
+---------- ring ------+
```

然后把 provider 节点通过哈希函数放到环上：

```text
providerA -> hashA
providerB -> hashB
providerC -> hashC
```

请求也通过哈希函数放到环上：

```text
request -> requestHash
```

选择节点时，从请求所在位置开始，沿顺时针方向找到第一个 provider 节点：

```text
requestHash
   -> 顺时针找
   -> 第一个 provider
```

如果走到环尾都没有找到，就回到环头选择第一个节点。

项目中用 `TreeMap<Integer, ServiceMetaInfo>` 来实现这个哈希环：

```java
private final TreeMap<Integer, ServiceMetaInfo> virtualNodes = new TreeMap<>();
```

`TreeMap` 的 key 是哈希值，value 是服务节点。它天然支持有序查找，所以可以使用：

```java
Map.Entry<Integer, ServiceMetaInfo> entry = virtualNodes.ceilingEntry(hash);
```

`ceilingEntry(hash)` 的含义是：找到第一个 key 大于等于当前请求哈希值的节点。这正好对应一致性哈希中“顺时针找到第一个节点”的动作。

如果没有找到，说明请求哈希值落在环尾，需要回到环头：

```java
if (entry == null) {
    entry = virtualNodes.firstEntry();
}
```

这就是哈希环的闭环逻辑。

## 为什么需要虚拟节点

如果每个真实 provider 只在哈希环上放一个点，节点数量少的时候很容易分布不均。

例如只有两个 provider：

```text
providerA 落在环的 10% 位置
providerB 落在环的 80% 位置
```

那么 A 和 B 负责的区间可能差距很大，请求分布就会倾斜。

虚拟节点的做法是：每个真实 provider 在哈希环上放多个点。

项目中配置的是：

```java
private static final int VIRTUAL_NODE_NUM = 100;
```

然后每个真实节点都会生成 100 个虚拟节点：

```java
for (ServiceMetaInfo serviceMetaInfo : serviceMetaInfoList) {
    for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
        int hash = getHash(serviceMetaInfo.getServiceAddress() + "#" + i);
        virtualNodes.put(hash, serviceMetaInfo);
    }
}
```

这里的 `serviceMetaInfo.getServiceAddress() + "#" + i` 很关键。它保证同一个真实节点的不同虚拟节点有不同的 hash 输入。

例如：

```text
http://localhost:8080#0
http://localhost:8080#1
http://localhost:8080#2
...
http://localhost:8080#99
```

这些虚拟节点都会指向同一个真实 provider，但会分散在哈希环的不同位置，从而让请求分布更均匀。

这也是之前一致性哈希代码里需要修复的关键点之一：虚拟节点必须真正生成不同 hash，否则名义上有 100 个虚拟节点，实际可能没有起到分散作用。

## 一致性哈希的稳定性

一致性哈希最重要的优势不是“绝对平均”，而是“节点变化时迁移范围小”。

当一个 provider 下线时，只有它负责的那一段哈希区间会转移给顺时针方向的下一个节点。其他区间的请求映射关系基本不变。

这对 RPC 框架很有价值：

- 可以减少节点上下线带来的请求抖动。
- 可以让同类请求稳定落到同一 provider。
- 如果 provider 内部有本地缓存，可以提高缓存命中率。
- 做会话粘滞、用户维度路由时更自然。

但是在当前项目实现中，一致性哈希是基于：

```java
requestParams.put("methodName", rpcRequest.getMethodName());
```

也就是说，目前它更像是“按方法名做稳定路由”。如果连续调用的都是 `getUser`，那么三次选择同一个端口是符合预期的。

如果希望更接近真实业务，可以把请求参数改得更细，例如：

```text
userId
tenantId
接口名 + 用户 id
接口名 + 某个业务 key
```

这样才能体现“同一个用户稳定路由到同一个 provider，不同用户可能分散到不同 provider”的效果。

## 当前一致性哈希实现的工程取舍

项目当前 `ConsistentHashLoadBalancer` 的核心实现是：

```java
public class ConsistentHashLoadBalancer implements LoadBalancer {

    private final TreeMap<Integer, ServiceMetaInfo> virtualNodes = new TreeMap<>();

    private static final int VIRTUAL_NODE_NUM = 100;

    @Override
    public ServiceMetaInfo select(Map<String, Object> requestParams,
                                  List<ServiceMetaInfo> serviceMetaInfoList) {
        if (serviceMetaInfoList.isEmpty()) {
            return null;
        }

        virtualNodes.clear();
        for (ServiceMetaInfo serviceMetaInfo : serviceMetaInfoList) {
            for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
                int hash = getHash(serviceMetaInfo.getServiceAddress() + "#" + i);
                virtualNodes.put(hash, serviceMetaInfo);
            }
        }

        int hash = getHash(requestParams);

        Map.Entry<Integer, ServiceMetaInfo> entry = virtualNodes.ceilingEntry(hash);
        if (entry == null) {
            entry = virtualNodes.firstEntry();
        }
        return entry.getValue();
    }

    private int getHash(Object key) {
        return key.hashCode();
    }
}
```

这份代码符合教学阶段目标：把一致性哈希的核心流程跑通。

不过从秋招和生产化角度，需要能说出它的边界：

1. `virtualNodes` 是成员变量，`select` 中会 `clear()` 后重建，在并发调用下不是线程安全的。
2. 每次调用都重建哈希环，逻辑简单，但成本是 `O(provider 数量 * 虚拟节点数)`。
3. `hashCode()` 足够教学演示，但生产环境一般会选择分布更稳定的 hash 算法，例如 MurmurHash。
4. 当前请求特征只使用 `methodName`，粒度比较粗。
5. 虚拟节点数量固定为 100，生产中需要结合节点数量、流量分布和性能压测调整。

如果面试官追问“怎么优化”，可以回答：

```text
教学项目为了贴合文档和易复现，每次 select 都根据最新服务列表重建哈希环。
生产环境我会考虑把哈希环按 serviceKey 缓存起来，服务列表变化时再重建；
同时使用局部不可变 TreeMap 或读写锁保证并发安全；
哈希函数也会从 Object.hashCode 升级为分布更好的 MurmurHash。
```

这个回答能体现你不是只会背算法，而是知道算法进入工程之后还有并发、性能、缓存一致性和节点变更这些现实问题。

## SPI 扩展机制

负载均衡器没有在 `ServiceProxy` 中直接 `new RoundRobinLoadBalancer()`，而是通过工厂和 SPI 加载。

项目中定义了负载均衡器 key：

```java
public interface LoadBalancerKeys {

    String ROUND_ROBIN = "roundRobin";

    String RANDOM = "random";

    String CONSISTENT_HASH = "consistentHash";
}
```

SPI 配置文件位于：

```text
rpc-core/src/main/resources/META-INF/rpc/system/com.achingsoul.sosrpc.loadbalancer.LoadBalancer
```

内容为：

```properties
roundRobin=com.achingsoul.sosrpc.loadbalancer.RoundRobinLoadBalancer
random=com.achingsoul.sosrpc.loadbalancer.RandomLoadBalancer
consistentHash=com.achingsoul.sosrpc.loadbalancer.ConsistentHashLoadBalancer
```

`LoadBalancerFactory` 负责根据配置 key 获取实例：

```java
public class LoadBalancerFactory {

    static {
        SpiLoader.load(LoadBalancer.class);
    }

    public static LoadBalancer getInstance(String key) {
        return SpiLoader.getInstance(LoadBalancer.class, key);
    }
}
```

这个设计的好处是负载均衡策略变成了可插拔扩展点。

如果以后新增 `leastConnection`，不需要改 `ServiceProxy` 主流程，只需要：

1. 新增 `LeastConnectionLoadBalancer`。
2. 在 SPI 文件中添加映射。
3. 在配置文件中设置 `rpc.loadBalancer=leastConnection`。

这和序列化器、注册中心的扩展方式保持一致，说明项目的可扩展架构在逐步成型。

## RpcConfig 配置接入

项目在 `RpcConfig` 中新增了负载均衡配置：

```java
private String loadBalancer = LoadBalancerKeys.ROUND_ROBIN;
```

这意味着默认策略是轮询。如果用户没有配置负载均衡器，框架会使用 `roundRobin`。

在 `example-consumer/src/main/resources/application.properties` 中可以切换策略：

```properties
rpc.loadBalancer=roundRobin
```

可选值包括：

```properties
rpc.loadBalancer=roundRobin
rpc.loadBalancer=random
rpc.loadBalancer=consistentHash
```

这里要注意，真正决定 consumer 使用哪种策略的是 consumer 侧配置。provider 侧的 `rpc.loadBalancer` 对 provider 注册服务没有直接影响。

provider 的核心配置更关注：

```properties
rpc.serverHost=localhost
rpc.serverPort=8080
rpc.registryConfig.registry=etcd
rpc.registryConfig.address=http://localhost:2379
```

consumer 的核心配置更关注：

```properties
rpc.registryConfig.registry=etcd
rpc.registryConfig.address=http://localhost:2379
rpc.loadBalancer=roundRobin
```

一句话总结：

```text
provider 负责注册自己在哪里；
consumer 负责发现有哪些 provider，并选择一个调用。
```

## ServiceProxy 如何接入负载均衡

本阶段最关键的主链路改造在 `ServiceProxy`。

调用开始时，动态代理先根据接口和方法构造 `RpcRequest`：

```java
String serviceName = method.getDeclaringClass().getName();
RpcRequest rpcRequest = RpcRequest.builder()
        .serviceName(serviceName)
        .serviceVersion(RpcConstant.DEFAULT_SERVICE_VERSION)
        .methodName(method.getName())
        .parameterTypes(method.getParameterTypes())
        .args(args)
        .build();
```

然后从全局配置中获取注册中心配置，并执行服务发现：

```java
RpcConfig rpcConfig = RpcApplication.getRpcConfig();
Registry registry = RegistryFactory.getInstance(
        rpcConfig.getRegistryConfig().getRegistry());

ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
serviceMetaInfo.setServiceName(serviceName);
serviceMetaInfo.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);

List<ServiceMetaInfo> serviceMetaInfoList = registry.serviceDiscovery(
        serviceMetaInfo.getServiceKey());
```

如果注册中心没有找到服务节点，直接抛异常：

```java
if (CollUtil.isEmpty(serviceMetaInfoList)) {
    throw new RuntimeException("No available service provider: " + serviceName);
}
```

然后进入本阶段新增的负载均衡逻辑：

```java
LoadBalancer loadBalancer = LoadBalancerFactory.getInstance(rpcConfig.getLoadBalancer());
Map<String, Object> requestParams = new HashMap<>();
requestParams.put("methodName", rpcRequest.getMethodName());
ServiceMetaInfo selectedServiceMetaInfo = loadBalancer.select(
        requestParams, serviceMetaInfoList);
```

为了方便测试，还打印了最终选择的 provider：

```java
System.out.println("Selected service provider: "
        + selectedServiceMetaInfo.getServiceAddress());
```

最后再通过 TCP 客户端发起请求：

```java
RpcResponse rpcResponse = VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo);
return rpcResponse.getData();
```

这段链路非常适合秋招讲项目，因为它把多个阶段串起来了：

```text
动态代理
  -> 请求对象构造
  -> 注册中心服务发现
  -> 负载均衡选择节点
  -> 自定义协议编码
  -> TCP 网络调用
  -> 服务端反射调用
  -> 响应解码返回
```

这说明项目不是零散功能堆叠，而是一条完整 RPC 调用链。

## 测试代码设计

本阶段新增的测试类是：

```text
rpc-core/src/test/java/com/achingsoul/myrpc/loadbalancer/LoadBalancerTest.java
```

它覆盖了四类行为。

第一类是轮询顺序：

```java
Assert.assertEquals(8080, loadBalancer.select(
        requestParams, serviceMetaInfoList).getServicePort().intValue());
Assert.assertEquals(8081, loadBalancer.select(
        requestParams, serviceMetaInfoList).getServicePort().intValue());
Assert.assertEquals(8082, loadBalancer.select(
        requestParams, serviceMetaInfoList).getServicePort().intValue());
Assert.assertEquals(8080, loadBalancer.select(
        requestParams, serviceMetaInfoList).getServicePort().intValue());
```

这验证了轮询策略确实按顺序循环。

第二类是随机选择：

```java
ServiceMetaInfo selected = loadBalancer.select(
        new HashMap<>(), serviceMetaInfoList);

Assert.assertNotNull(selected);
Assert.assertTrue(serviceMetaInfoList.contains(selected));
```

随机测试不应该断言固定端口，因为随机本来就没有固定顺序。它只需要验证结果来自可用节点列表。

第三类是一致性哈希稳定性：

```java
requestParams.put("methodName", "getUser");

ServiceMetaInfo first = loadBalancer.select(requestParams, serviceMetaInfoList);
ServiceMetaInfo second = loadBalancer.select(requestParams, serviceMetaInfoList);
ServiceMetaInfo third = loadBalancer.select(requestParams, serviceMetaInfoList);

Assert.assertEquals(first, second);
Assert.assertEquals(second, third);
```

这验证了同一个请求参数会稳定选择同一个节点。

第四类是 SPI 加载：

```java
Assert.assertTrue(LoadBalancerFactory.getInstance(
        LoadBalancerKeys.ROUND_ROBIN) instanceof RoundRobinLoadBalancer);
Assert.assertTrue(LoadBalancerFactory.getInstance(
        LoadBalancerKeys.RANDOM) instanceof RandomLoadBalancer);
Assert.assertTrue(LoadBalancerFactory.getInstance(
        LoadBalancerKeys.CONSISTENT_HASH) instanceof ConsistentHashLoadBalancer);
```

这验证了配置 key 能够正确映射到对应实现类。

## 单元测试方法

在项目根目录执行：

```bash
mvn -pl rpc-core "-Dtest=LoadBalancerTest" test
```

预期结果：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

这个测试不依赖 Etcd、ZooKeeper、provider 或 consumer，只验证负载均衡模块自身是否正常。

所以它适合做第一层测试：

```text
先测算法和 SPI，
再测真实 RPC 调用链路。
```

## 真实联调测试方法

真实联调需要 Etcd、两个 provider、一个 consumer。

### 第一步：启动 Etcd

确保 Etcd 监听在：

```text
http://127.0.0.1:2379
```

consumer 和 provider 配置中使用：

```properties
rpc.registryConfig.registry=etcd
rpc.registryConfig.address=http://localhost:2379
```

### 第二步：启动 EtcdKeeper

EtcdKeeper 的页面一般访问：

```text
http://127.0.0.1:8081/etcdkeeper/
```

注意：`8081` 已经被 EtcdKeeper 占用时，不要再让 provider 使用 `8081`，否则会端口冲突。

### 第三步：启动第一个 provider

第一个 provider 使用默认端口：

```properties
rpc.serverPort=8080
```

运行：

```text
ProviderExample_loadbalancer
```

启动成功后，EtcdKeeper 中应该能看到类似节点：

```text
/rpc/com.achingsoul.example.common.service.UserService:1.0/http://localhost:8080
```

### 第四步：启动第二个 provider

第二个 provider 使用同一个启动类，但通过 VM options 覆盖端口。

在 IDEA 的运行配置中设置：

```text
-Drpc.serverPort=8085
```

然后再启动一个 `ProviderExample_loadbalancer`。

启动成功后，EtcdKeeper 中应该能看到两个节点：

```text
/rpc/com.achingsoul.example.common.service.UserService:1.0/http://localhost:8080
/rpc/com.achingsoul.example.common.service.UserService:1.0/http://localhost:8085
```

这里能看到两个 provider，才说明负载均衡有测试条件。

如果只看到一个 provider，那么 consumer 再怎么切换策略，也只能选一个节点。

### 第五步：切换 consumer 负载均衡策略

修改：

```text
example-consumer/src/main/resources/application.properties
```

轮询：

```properties
rpc.loadBalancer=roundRobin
```

随机：

```properties
rpc.loadBalancer=random
```

一致性哈希：

```properties
rpc.loadBalancer=consistentHash
```

修改后重新运行 `ConsumerExample`。

### 第六步：观察 consumer 输出

`ServiceProxy` 中已经打印：

```text
Selected service provider: http://localhost:8080
```

所以真正看效果的位置不是 EtcdKeeper，而是 consumer 控制台。

EtcdKeeper 负责看注册中心里有没有节点：

```text
有没有 8080？
有没有 8085？
节点会不会过期？
```

consumer 控制台负责看负载均衡选中了谁：

```text
Selected service provider: http://localhost:8080
Selected service provider: http://localhost:8085
Selected service provider: http://localhost:8080
```

两者观察重点不同。

## 不同策略的预期现象

### 轮询策略

配置：

```properties
rpc.loadBalancer=roundRobin
```

如果注册中心中有两个 provider：

```text
http://localhost:8080
http://localhost:8085
```

连续调用三次，比较典型的输出是：

```text
Selected service provider: http://localhost:8080
Selected service provider: http://localhost:8085
Selected service provider: http://localhost:8080
```

这说明请求正在按顺序分摊。

### 随机策略

配置：

```properties
rpc.loadBalancer=random
```

可能输出：

```text
Selected service provider: http://localhost:8085
Selected service provider: http://localhost:8085
Selected service provider: http://localhost:8080
```

也可能连续几次都是同一个端口。这不代表错，因为随机算法本身不保证短样本均匀。

判断随机策略是否正常，应该看两个点：

1. 选中的节点必须来自注册中心返回的服务列表。
2. 请求次数足够多时，分布大致趋于均匀。

### 一致性哈希策略

配置：

```properties
rpc.loadBalancer=consistentHash
```

如果连续三次调用的是同一个方法，例如都是 `getUser`，可能输出：

```text
Selected service provider: http://localhost:8085
Selected service provider: http://localhost:8085
Selected service provider: http://localhost:8085
```

这不是异常，而是正确体现了“一致性”。

因为当前请求参数中使用的是：

```text
methodName = getUser
```

同一个 `methodName` 的 hash 值不变，所以它在哈希环上落点不变，最终选中的 provider 也会稳定。

如果你希望看到一致性哈希选择不同 provider，可以尝试让请求参数不同，例如调用不同方法，或者后续把 `requestParams` 改为业务参数。需要注意的是，只有两个 provider 时，不同请求也可能因为哈希环分布原因落到同一个 provider，这属于正常现象。

## 常见问题与排查

### 问题一：第二个 provider 启动失败

最常见原因是端口冲突。

如果 EtcdKeeper 占用了 `8081`，第二个 provider 就不能再配置 `8081`。可以使用：

```text
-Drpc.serverPort=8085
```

同时观察 provider 启动日志里的 `serverPort` 是否真的变成了 `8085`。

### 问题二：EtcdKeeper 里只有一个节点

这说明注册中心中只有一个 provider 成功注册。

可能原因：

- 第二个 provider 没启动成功。
- 第二个 provider 端口被占用。
- VM options 没有配置到正确的运行配置里。
- provider 连接的不是同一个 Etcd 地址。
- provider 启动后很快异常退出，租约到期后节点消失。

负载均衡必须建立在“服务发现结果有多个节点”的前提下。

### 问题三：consumer 一直选同一个节点

需要分情况看：

如果使用的是 `consistentHash`，连续选同一个节点是正常现象，因为同一个请求参数会稳定映射。

如果使用的是 `roundRobin`，仍然一直选同一个节点，优先检查：

1. Etcd 中是否真的有两个 provider。
2. consumer 控制台中的 `rpc init` 是否显示 `loadBalancer=roundRobin`。
3. 服务发现返回的 `serviceMetaInfoList` size 是否为 2。
4. 是否每次重新创建了负载均衡器导致轮询计数被重置。

当前项目通过 SPI 获取负载均衡器实例，正常情况下轮询计数可以在同一 consumer 生命周期内递增。

### 问题四：Etcd 中出现旧端口节点

如果 provider 启动过程中已经注册到 Etcd，但后面因为端口冲突或其他异常退出，可能短时间内还能看到旧节点。

这是因为 Etcd 节点依赖租约过期或主动下线清理。可以等待租约过期，也可以手动删除旧节点，再重新启动 provider。

这和之前注册中心优化阶段实现的：

```text
心跳续约
节点下线
服务缓存失效
watch 监听
```

是连在一起的。

## 本阶段和注册中心优化的关系

负载均衡并不是孤立模块，它依赖注册中心提供准确的服务列表。

完整关系是：

```text
provider 启动
  -> 注册服务节点到 Etcd
  -> 心跳续约保持节点有效
  -> shutdownHook 下线时注销节点

consumer 调用
  -> 从 Etcd 查询服务列表
  -> 写入本地服务缓存
  -> watch 监听节点变化
  -> 节点变化时清理缓存
  -> 下一次重新查询最新服务列表
  -> 负载均衡选择一个 provider
  -> TCP 发起调用
```

这里可以看到，负载均衡的前提是服务列表要尽可能准确。

如果注册中心里有过期节点，负载均衡可能会选到不可用 provider。

如果服务缓存没有及时清理，consumer 可能继续使用旧列表。

如果服务列表中只有一个节点，负载均衡就没有实际效果。

所以这一阶段把前面几章串起来之后，RPC 框架已经开始具备比较完整的服务治理雏形：

```text
注册中心：解决服务地址动态管理
心跳机制：解决服务存活状态维护
节点下线：解决服务退出后的清理
服务缓存：减少注册中心查询压力
watch 监听：保证缓存和注册中心变化联动
负载均衡：解决多个 provider 的调用选择
```

这套链路是秋招讲 RPC 项目时非常有含金量的一部分。

## 和教学文档中的算法扩展对照

教学文档中除了本项目已经实现的轮询、随机、一致性哈希，还提到了其他常见策略。

### 加权轮询

加权轮询适合 provider 性能不同的场景。

例如：

```text
providerA 权重 5
providerB 权重 1
```

那么 providerA 应该承担更多请求。

这适合机器配置不同、实例规格不同、或者某些节点承载能力更强的场景。

### 加权随机

加权随机和随机类似，但每个节点被选中的概率不同。

权重越高，随机命中的概率越大。

它比普通随机更适合异构机器，但短时间内仍然可能有波动。

### 最少连接数

最少连接数策略会优先选择当前连接数最少或压力最低的 provider。

它适合请求耗时差异较大的场景。

例如有些请求 10ms 返回，有些请求 3s 返回，普通轮询无法感知节点压力，而最少连接数可以根据运行时状态做选择。

不过它也更复杂，因为框架需要维护每个 provider 的连接数、请求数或活跃状态。

### IP Hash 或参数 Hash

IP Hash 会根据客户端 IP 做哈希，让同一个客户端稳定访问同一个 provider。

参数 Hash 则可以根据业务参数做稳定路由，例如：

```text
userId
tenantId
orderId
```

当前项目中的一致性哈希已经有这个雏形，只是请求参数目前使用的是 `methodName`。

如果后续把 `requestParams` 换成业务 key，就能更接近真实服务路由。

## 秋招项目表达：30 秒版本

如果面试官问：“你的 RPC 框架负载均衡怎么做的？”

可以这样回答：

```text
我的 RPC 框架在服务发现之后接入了负载均衡模块。
consumer 先通过注册中心拿到某个服务的所有 provider 节点，
然后根据配置选择对应的 LoadBalancer 实现，
目前支持轮询、随机和一致性哈希。
负载均衡器通过 SPI 加载，配置项是 rpc.loadBalancer，
所以新增策略不需要改主调用链路。
最终 ServiceProxy 会把选中的 ServiceMetaInfo 交给 TCP 客户端发起调用。
```

这个回答重点是：位置、输入、输出、策略、扩展方式。

## 秋招项目表达：1 分钟版本

更完整一点可以说：

```text
在注册中心阶段，consumer 已经可以从 Etcd 或 ZooKeeper 获取服务列表。
但如果每次都取第一个节点，请求会集中到单个 provider 上，
所以我在 ServiceProxy 的服务发现之后增加了负载均衡层。

我定义了 LoadBalancer 接口，入参是请求参数和服务节点列表，
返回一个最终调用的 ServiceMetaInfo。
目前实现了 RoundRobin、Random 和 ConsistentHash 三种策略。
RoundRobin 用 AtomicInteger 做循环下标；
Random 从列表中随机取节点；
ConsistentHash 用 TreeMap 构建哈希环，每个真实节点生成 100 个虚拟节点，
请求根据 methodName 计算 hash，然后通过 ceilingEntry 找顺时针第一个节点。

这些策略通过 SPI 注册，由 LoadBalancerFactory 根据 rpc.loadBalancer 配置加载。
我也写了单元测试验证轮询顺序、随机结果合法性、一致性哈希稳定性和 SPI 加载。
联调时启动两个 provider，例如 8080 和 8085，consumer 控制台可以看到每次 selected provider，从而验证不同策略的效果。
```

这个版本适合大多数项目面试。

## 秋招项目表达：深入追问版本

如果面试官继续问：“一致性哈希为什么比普通 hash 取模好？”

可以回答：

```text
普通 hash 取模依赖节点数量，比如 hash(request) % n。
当 provider 上下线导致 n 变化时，大量请求的映射都会改变。
一致性哈希把节点和请求都映射到同一个哈希环上，
请求顺时针找到第一个 provider。
当一个节点下线时，只影响它负责的那段区间，
其他请求到 provider 的映射基本不变。
所以它更适合节点动态变化、缓存命中和会话粘滞场景。
```

如果面试官问：“你这个实现有没有问题？”

可以诚实回答：

```text
当前实现是教学项目版本，能完整体现一致性哈希原理，
但生产环境还需要优化。
比如 virtualNodes 是成员 TreeMap，select 中会 clear 并重建，
并发场景下不是线程安全的。
另外每次调用都重建哈希环有额外开销，
生产中可以按 serviceKey 缓存不可变哈希环，
只有服务列表变化时再重建。
hash 函数也可以从 hashCode 升级为 MurmurHash。
```

这类回答会比“我实现了一致性哈希”更有说服力。

## 本阶段踩坑记录

### 1. 第二个 provider 端口不要使用 8081

实际测试中，EtcdKeeper 使用的是：

```text
http://127.0.0.1:8081/etcdkeeper/
```

所以第二个 provider 如果也配置成 `8081`，会启动失败或出现端口占用问题。

正确做法是使用其他端口，例如：

```text
-Drpc.serverPort=8085
```

### 2. VM options 是虚拟机选项

IDEA 中的 VM options 指的是传给 JVM 的参数。

例如：

```text
-Drpc.serverPort=8085
```

这里的 `-D` 是 Java 系统属性。项目中的配置工具已经支持用系统属性覆盖 `application.properties`，所以同一个 provider 启动类可以通过不同 VM options 启动多个实例。

### 3. 看负载均衡效果要看 consumer 控制台

EtcdKeeper 只负责看注册中心状态。

负载均衡真正选中了哪个 provider，要看 consumer 输出：

```text
Selected service provider: http://localhost:8080
```

如果只盯着 EtcdKeeper，会不知道 consumer 实际选了谁。

### 4. 一致性哈希连续三次同端口不是错

连续调用同一个方法时，`requestParams` 一样，hash 值一样，所以选中同一个 provider 是一致性哈希的预期。

如果期望看到“依次切换”，那应该使用 `roundRobin`，不是 `consistentHash`。

这也是理解不同负载均衡策略差异的关键。

## 本阶段完成度

本阶段已经完成了教学文档中负载均衡部分的主要落地：

- 已实现负载均衡接口。
- 已实现轮询负载均衡。
- 已实现随机负载均衡。
- 已修复并实现一致性哈希负载均衡。
- 已实现负载均衡 key 管理。
- 已通过 SPI 接入负载均衡器。
- 已在 `RpcConfig` 中增加 `loadBalancer` 配置。
- 已在 `ServiceProxy` 真实调用链路中使用负载均衡。
- 已编写最终测试类 `LoadBalancerTest`。
- 已支持 Etcd 环境下启动多个 provider 联调。
- 已通过 consumer 控制台观察 selected provider 验证策略效果。

从项目演进角度看，这一阶段让 Sosrpc 从“能发现服务”进一步升级成了“能在多个服务实例之间做选择”。

## 后续可以扩展的方向

如果继续往工程化方向推进，负载均衡还可以继续增强。

### 1. 加权负载均衡

在 `ServiceMetaInfo` 中增加权重字段：

```text
weight
```

provider 注册时携带权重，consumer 根据权重做加权轮询或加权随机。

### 2. 最少连接数

在 consumer 侧统计每个 provider 当前活跃请求数，优先选择活跃请求少的节点。

这个策略适合请求耗时差异明显的服务。

### 3. 健康状态参与选择

负载均衡前可以过滤不可用节点：

```text
服务发现列表
  -> 过滤失效节点
  -> 过滤熔断节点
  -> 负载均衡选择
```

这样可以避免把请求打到已经异常的 provider。

### 4. 失败重试和故障转移

如果选中的 provider 调用失败，可以结合负载均衡选择下一个节点。

这会把负载均衡和容错机制连接起来：

```text
select providerA
  -> 调用失败
  -> retry
  -> select providerB
```

### 5. 一致性哈希生产化

当前一致性哈希可以进一步优化：

- 使用局部不可变 `TreeMap` 避免并发问题。
- 按 `serviceKey` 缓存哈希环。
- 服务列表变化时再重建哈希环。
- 使用 MurmurHash 等更稳定的哈希算法。
- 支持根据 `userId` 等业务参数路由。

这些方向都可以作为秋招面试中的“项目可扩展性”回答。

## 本阶段总结

负载均衡是 RPC 框架从“能调用”走向“能治理”的关键一步。

没有负载均衡时，注册中心只是提供了一批地址，consumer 如何使用这些地址并没有被认真解决。引入负载均衡后，consumer 可以根据策略从多个 provider 中选择一个调用目标，从而实现流量分摊、稳定路由和扩展策略接入。

在 Sosrpc 当前实现中，负载均衡模块通过 `LoadBalancer` 接口抽象选择行为，通过 `RoundRobinLoadBalancer`、`RandomLoadBalancer`、`ConsistentHashLoadBalancer` 提供具体策略，通过 SPI 和 `LoadBalancerFactory` 实现可插拔扩展，并最终接入 `ServiceProxy` 的真实调用链路。

这一阶段最核心的收获是：RPC 框架不是简单地把远程调用包装成本地方法调用，而是在一次调用背后完成了服务发现、节点选择、协议编码、网络传输、服务执行和响应返回等一整套流程。负载均衡正是其中负责“节点选择”的关键组件。

从秋招角度看，这一部分可以重点突出三点：

1. 我理解负载均衡在 RPC 调用链中的位置。
2. 我实现了多种策略，并用 SPI 做到了可扩展。
3. 我知道一致性哈希的原理、适用场景和当前实现的工程边界。

这比只说“我写了轮询和随机算法”更有项目深度。
