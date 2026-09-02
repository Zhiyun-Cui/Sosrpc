# Sosrpc 学习日志 v0.6.0

## 基本信息

- 项目名称：Sosrpc
- 当前版本：v0.6.0
- 日志类型：阶段学习记录
- 更新时间：2026-08-21 00:00:00 +08:00
- 学习阶段：自定义协议与 TCP 网络传输
- 前置版本：v0.5.0 注册中心基础实现与优化扩展
- 面向目标：秋招项目梳理、RPC 协议设计理解、TCP 半包粘包问题掌握、网络传输层能力沉淀
- 参考资料：教学文档 7《自定义协议》

## 本阶段目标

v0.5.0 阶段已经把 provider 地址从静态配置升级为注册中心动态发现。consumer 可以先通过注册中心找到 provider，再发起远程调用。

但当时真正的网络传输仍然是 HTTP：

```text
ServiceProxy
  -> HTTP POST
  -> VertxHttpServer
  -> HttpServerHandler
```

HTTP 能让项目快速跑通，但它不是 RPC 框架的唯一选择。对于一个希望继续往底层深入的 RPC 框架来说，下一步要思考的是：能不能自己设计一套更轻、更贴合 RPC 请求响应模型的协议。

本阶段要解决四个问题：

1. 不再依赖 HTTP 请求头和 HTTP 语义，改用 TCP 做网络传输。
2. 自定义一套 Sosrpc 消息结构，明确消息头和消息体的字节布局。
3. 实现协议编码器和解码器，让 Java 对象能转换成紧凑的二进制 Buffer。
4. 处理 TCP 半包和粘包问题，保证服务端和客户端每次拿到的都是完整消息。

这一步的核心不是“把 HTTP 换成 TCP”这么简单，而是第一次真正进入 RPC 框架的协议层。

## 为什么要自定义协议

最早的 HTTP 方案非常直观：

```text
RpcRequest 对象
  -> 序列化成 byte[]
  -> HTTP POST body
  -> provider 反序列化
```

它的好处是容易理解、容易调试、浏览器和工具链成熟。

但 HTTP 也有几个明显问题：

- HTTP 是通用应用层协议，头部信息比较重。
- 请求和响应包含很多 RPC 本身不关心的字段。
- 如果使用短连接，每次调用都有建连和断连成本。
- RPC 框架无法完全掌控消息格式。
- 后续要加入请求 id、序列化器标识、消息类型、心跳等能力时，HTTP 的表达不够贴合。

RPC 的目标是远程方法调用。它真正需要的是：

```text
我是谁的请求？
用什么版本的协议？
body 用什么序列化器？
这是请求还是响应？
响应状态是什么？
这次请求的 requestId 是多少？
body 到底有多长？
body 内容是什么？
```

所以自定义协议的本质，是用最少的字节承载 RPC 最需要的信息。

## 当前阶段架构变化

v0.5.0 的调用链路是：

```text
ConsumerExample
  -> ServiceProxy
  -> Registry.serviceDiscovery()
  -> 选择 provider
  -> HTTP POST
  -> VertxHttpServer
  -> HttpServerHandler
  -> LocalRegistry
  -> 反射调用 UserServiceImpl
```

v0.6.0 改成：

```text
ConsumerExample
  -> ServiceProxy
  -> Registry.serviceDiscovery()
  -> 选择 provider
  -> VertxTcpClient.doRequest()
  -> ProtocolMessageEncoder
  -> TCP 发送 Buffer
  -> VertxTcpServer
  -> TcpBufferHandlerWrapper
  -> ProtocolMessageDecoder
  -> TcpServerHandler
  -> LocalRegistry
  -> 反射调用 UserServiceImpl
  -> ProtocolMessageEncoder
  -> TCP 返回 Buffer
  -> ProtocolMessageDecoder
  -> 返回 RpcResponse.data
```

注册中心仍然负责找服务，序列化器仍然负责 body 编解码，动态代理仍然负责拦截接口调用。

本阶段替换的是网络传输和消息边界：

```text
HTTP + HTTP body
  -> TCP + 自定义协议消息
```

## 本阶段新增和涉及的核心文件

### 1. 协议包

位置：

```text
rpc-core/src/main/java/com/achingsoul/myrpc/protocol/
```

核心文件：

- `ProtocolMessage.java`：协议消息对象，包含 Header 和 body。
- `ProtocolConstant.java`：协议常量，例如消息头长度、魔数、协议版本。
- `ProtocolMessageTypeEnum.java`：消息类型，请求、响应、心跳、其他。
- `ProtocolMessageStatusEnum.java`：响应状态，成功、请求失败、响应失败。
- `ProtocolMessageSerializerEnum.java`：协议中的序列化器编号和框架序列化器 key 的映射。
- `ProtocolMessageEncoder.java`：把 `ProtocolMessage<?>` 编码为 Vert.x `Buffer`。
- `ProtocolMessageDecoder.java`：把 Vert.x `Buffer` 解码为 `ProtocolMessage<?>`。

### 2. TCP 包

位置：

```text
rpc-core/src/main/java/com/achingsoul/myrpc/server/tcp/
```

核心文件：

- `VertxTcpServer.java`：基于 Vert.x 的 TCP 服务端。
- `TcpServerHandler.java`：TCP 请求处理器，负责解码请求、反射调用、编码响应。
- `VertxTcpClient.java`：TCP 客户端，负责连接 provider、发送协议消息、等待响应。
- `TcpBufferHandlerWrapper.java`：基于 `RecordParser` 封装半包粘包处理。

### 3. 调用链路接入点

涉及文件：

```text
rpc-core/src/main/java/com/achingsoul/myrpc/proxy/ServiceProxy.java
example-provider/src/main/java/com/achingsoul/example/provider/ProviderExample.java
```

`ServiceProxy` 从 HTTP 请求切换为：

```java
RpcResponse rpcResponse = VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo);
return rpcResponse.getData();
```

`ProviderExample` 从启动 HTTP server 切换为：

```java
HttpServer httpServer = new VertxTcpServer();
httpServer.doStart(rpcConfig.getServerPort());
```

### 4. 最终测试

位置：

```text
rpc-core/src/test/java/com/achingsoul/myrpc/
```

测试文件：

- `ProtocolMessageTest`：验证请求和响应协议消息的编码、解码、魔数校验。
- `TcpBufferHandlerWrapperTest`：验证半包和粘包能被拆分、重组成完整消息。
- `VertxTcpClientTest`：启动真实 TCP server，通过 TCP client 发送请求并收到响应。

## 自定义协议消息结构

本阶段最重要的设计是 17 字节消息头。

协议消息分为两部分：

```text
Header 固定 17 字节
Body   变长，由 bodyLength 决定
```

消息头字段如下：

| 字段 | 类型 | 字节数 | 作用 |
| --- | --- | --- | --- |
| magic | byte | 1 | 魔数，用来识别是不是 Sosrpc 协议消息 |
| version | byte | 1 | 协议版本 |
| serializer | byte | 1 | 序列化器编号 |
| type | byte | 1 | 消息类型，请求或响应 |
| status | byte | 1 | 响应状态 |
| requestId | long | 8 | 请求唯一 id |
| bodyLength | int | 4 | 消息体长度 |

总长度：

```text
1 + 1 + 1 + 1 + 1 + 8 + 4 = 17 字节
```

body 部分存放序列化后的 `RpcRequest` 或 `RpcResponse`。

最终二进制结构可以理解为：

```text
+--------+---------+------------+------+--------+-----------+------------+------+
| magic  | version | serializer | type | status | requestId | bodyLength | body |
+--------+---------+------------+------+--------+-----------+------------+------+
| 1 byte | 1 byte  | 1 byte     | 1    | 1      | 8 bytes   | 4 bytes    | N    |
+--------+---------+------------+------+--------+-----------+------------+------+
```

这就是协议约定。发送方按这个顺序写，接收方按这个顺序读。

## 字段设计的内核

### 1. magic 魔数

魔数用于协议识别。

如果 TCP server 收到一段随机字节，不应该直接当成 RPC 请求处理。`ProtocolMessageDecoder` 会先读取第 0 个字节：

```java
byte magic = buffer.getByte(0);
if (magic != ProtocolConstant.PROTOCOL_MAGIC) {
    throw new RuntimeException("消息 magic 非法");
}
```

这相当于一道最基础的安全门。

### 2. version 版本号

版本号用于协议演进。

现在是：

```java
byte PROTOCOL_VERSION = 0x1;
```

如果未来协议头新增字段、字段含义变化、编码方式变化，就可以通过版本号做兼容判断。

### 3. serializer 序列化器编号

协议中不能直接写字符串 `jdk`、`json`、`kryo`，否则头部会变长。

所以使用 1 个 byte 表示序列化器：

```text
0 -> jdk
1 -> json
2 -> kryo
3 -> hessian
```

`ProtocolMessageSerializerEnum` 负责把协议编号和框架已有序列化器 key 联系起来。

这一步把 v0.4.0 的序列化器 SPI 能力接入到了自定义协议中。

### 4. type 消息类型

当前支持：

```text
REQUEST    请求
RESPONSE   响应
HEART_BEAT 心跳，预留
OTHERS     其他，预留
```

`ProtocolMessageDecoder` 会根据 type 决定 body 反序列化成什么对象：

```text
REQUEST  -> RpcRequest
RESPONSE -> RpcResponse
```

这比 HTTP 方案更清晰，因为协议本身就知道这是什么消息。

### 5. status 状态

status 主要用于响应消息。

当前枚举是：

```text
OK           20
BAD_REQUEST 40
BAD_RESPONSE 50
```

教学阶段主要用 `OK`，后续可以把业务异常、服务端异常、反序列化失败等场景映射成不同状态。

### 6. requestId 请求 id

TCP 是双向通信，理论上一个连接上可以连续发送多个请求，也可能未来支持异步、多路复用。

这时就需要 requestId 把请求和响应对应起来。

当前实现每次请求生成：

```java
IdUtil.getSnowflakeNextId()
```

现在虽然 `VertxTcpClient` 仍然用 `CompletableFuture` 等待单次响应，但 requestId 已经为后续并发请求预留了空间。

### 7. bodyLength 消息体长度

这是解决 TCP 半包粘包问题的关键字段。

TCP 是字节流协议，不保留应用层消息边界。发送方写了 2 条消息，接收方可能一次收到半条、一条、两条半。

所以接收方必须知道：

```text
我要先读 17 字节头
从头里拿到 bodyLength
再继续读 bodyLength 个字节
这才是一条完整消息
```

没有 bodyLength，自定义协议无法可靠处理变长 body。

## 编码器实现

`ProtocolMessageEncoder` 的职责是把 Java 协议对象转成 Vert.x `Buffer`。

核心流程：

```text
拿到 ProtocolMessage
  -> 获取 header
  -> 根据 header.serializer 找到序列化器
  -> 序列化 body 得到 bodyBytes
  -> 写入 magic
  -> 写入 version
  -> 写入 serializer
  -> 写入 type
  -> 写入 status
  -> 写入 requestId
  -> 写入 bodyLength
  -> 写入 bodyBytes
  -> 返回 Buffer
```

实现里的关键点是：

```java
ProtocolMessageSerializerEnum serializerEnum =
        ProtocolMessageSerializerEnum.getEnumByKey(header.getSerializer());
Serializer serializer = SerializerFactory.getInstance(serializerEnum.getValue());
byte[] bodyBytes = serializer.serialize(protocolMessage.getBody());
header.setBodyLength(bodyBytes.length);
```

编码器不是固定使用 JDK 序列化，而是根据协议头里的 serializer 字段决定 body 怎么序列化。

写入顺序必须和协议设计完全一致：

```java
buffer.appendByte(header.getMagic());
buffer.appendByte(header.getVersion());
buffer.appendByte(header.getSerializer());
buffer.appendByte(header.getType());
buffer.appendByte(header.getStatus());
buffer.appendLong(header.getRequestId());
buffer.appendInt(header.getBodyLength());
buffer.appendBytes(bodyBytes);
```

这个顺序就是协议。只要改顺序，解码器就会读错。

## 解码器实现

`ProtocolMessageDecoder` 的职责是把收到的 `Buffer` 还原成 `ProtocolMessage<?>`。

核心流程：

```text
校验 Buffer 至少有 17 字节
  -> 读取 magic
  -> 校验 magic
  -> 读取 version
  -> 读取 serializer
  -> 读取 type
  -> 读取 status
  -> 读取 requestId
  -> 读取 bodyLength
  -> 只读取指定长度的 bodyBytes
  -> 根据 serializer 找到反序列化器
  -> 根据 type 判断 body 类型
  -> REQUEST 反序列化成 RpcRequest
  -> RESPONSE 反序列化成 RpcResponse
```

字段偏移如下：

```text
0      magic
1      version
2      serializer
3      type
4      status
5-12   requestId
13-16  bodyLength
17...  body
```

所以 bodyLength 的读取位置是：

```java
header.setBodyLength(buffer.getInt(13));
```

body 的读取范围是：

```java
int bodyStart = ProtocolConstant.MESSAGE_HEADER_LENGTH;
int bodyEnd = bodyStart + header.getBodyLength();
byte[] bodyBytes = buffer.getBytes(bodyStart, bodyEnd);
```

这也是为什么消息头长度必须稳定为 17。

## TCP 半包和粘包问题

这是本阶段最值得讲清楚的地方。

HTTP 里不太需要自己处理消息边界，因为 HTTP 协议已经帮我们定义了 header、body、Content-Length、请求响应格式。

TCP 不一样。TCP 是面向字节流的协议，它只保证字节有序可靠到达，不保证一次 `write` 对应一次 `read`。

所以会出现：

```text
半包：发送方发了一条完整消息，接收方第一次只收到前半段。
粘包：发送方连续发多条消息，接收方一次收到多条连在一起的数据。
```

例如发送方写入：

```text
message1 message2 message3
```

接收方可能收到：

```text
message1
message2message3
```

也可能收到：

```text
mess
age1message2mes
sage3
```

所以 TCP server 不能假设 `socket.handler(buffer -> ...)` 里拿到的 buffer 就是一条完整 RPC 消息。

## TcpBufferHandlerWrapper

`TcpBufferHandlerWrapper` 用来解决半包粘包。

它使用 Vert.x 的 `RecordParser`，按两段式读取：

```text
第一阶段：固定读取 17 字节消息头。
第二阶段：根据消息头中的 bodyLength，固定读取 bodyLength 字节消息体。
```

核心状态：

```java
int size = -1;
Buffer resultBuffer = Buffer.buffer();
```

当 `size == -1` 时，说明当前正在读消息头：

```java
size = buffer.getInt(13);
resultBuffer.appendBuffer(buffer);
parser.fixedSizeMode(size);
```

当 `size != -1` 时，说明当前读到的是 body：

```java
resultBuffer.appendBuffer(buffer);
bufferHandler.handle(resultBuffer);
parser.fixedSizeMode(ProtocolConstant.MESSAGE_HEADER_LENGTH);
size = -1;
resultBuffer = Buffer.buffer();
```

也就是说，业务处理器永远只会收到完整消息：

```text
17 字节 header + bodyLength 字节 body
```

这一步是从“TCP 字节流”到“RPC 消息”的边界恢复。

## TCP 服务端处理链路

`VertxTcpServer` 负责启动 TCP server：

```java
server.connectHandler(new TcpServerHandler());
```

`TcpServerHandler` 负责处理请求。

完整链路：

```text
NetSocket 收到 TCP 数据
  -> TcpBufferHandlerWrapper 组装完整消息
  -> ProtocolMessageDecoder.decode(buffer)
  -> 得到 ProtocolMessage<RpcRequest>
  -> 取出 RpcRequest
  -> LocalRegistry.get(serviceName)
  -> 反射调用目标方法
  -> 构造 RpcResponse
  -> 把请求 header 的 type 改成 RESPONSE
  -> ProtocolMessageEncoder.encode(responseProtocolMessage)
  -> netSocket.write(encode)
```

其中反射调用部分延续之前 HTTP handler 的逻辑：

```java
Class<?> implClass = LocalRegistry.get(rpcRequest.getServiceName());
Method method = implClass.getMethod(
        rpcRequest.getMethodName(), rpcRequest.getParameterTypes());
Object result = method.invoke(
        implClass.getDeclaredConstructor().newInstance(), rpcRequest.getArgs());
```

区别只是请求来源从 HTTP body 变成了 TCP Buffer。

## TCP 客户端处理链路

`VertxTcpClient#doRequest` 负责 consumer 侧发送请求。

完整链路：

```text
ServiceProxy 构造 RpcRequest
  -> Registry.serviceDiscovery 找 provider
  -> 选择第一个 ServiceMetaInfo
  -> VertxTcpClient.doRequest(rpcRequest, serviceMetaInfo)
  -> NetClient 连接 serviceHost:servicePort
  -> 构造 ProtocolMessage<RpcRequest>
  -> header 写入 magic/version/serializer/type/status/requestId
  -> ProtocolMessageEncoder.encode
  -> socket.write
  -> TcpBufferHandlerWrapper 接收响应
  -> ProtocolMessageDecoder.decode
  -> 得到 RpcResponse
  -> CompletableFuture.complete
  -> 返回 rpcResponse.getData()
```

由于 Vert.x 的网络 API 是异步回调风格，而动态代理调用需要同步返回结果，所以这里使用了：

```java
CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
RpcResponse rpcResponse = responseFuture.get(10, TimeUnit.SECONDS);
```

这一步可以理解为：

```text
Vert.x 异步回调
  -> CompletableFuture
  -> 同步等待 RPC 返回值
```

当前实现每次请求创建一个 `Vertx` 和 `NetClient`，请求完成后关闭。教学阶段这样比较容易理解；生产阶段可以考虑连接池、长连接和复用。

## provider 和 consumer 的变化

### provider 变化

`ProviderExample` 仍然做三件事：

```text
初始化 RpcApplication
注册本地服务实现
向注册中心注册 ServiceMetaInfo
启动 server
```

只是最后一步从：

```java
new VertxHttpServer()
```

改成：

```java
new VertxTcpServer()
```

所以 provider 仍然注册同一个 host 和 port，但这个端口上跑的已经不是 HTTP server，而是 TCP server。

这就是为什么现在浏览器访问：

```text
http://localhost:8080
```

不再是有效测试方式。浏览器说不通，不代表 RPC 不通。正确方式是启动 consumer 发 TCP 协议请求。

### consumer 变化

`ServiceProxy` 仍然负责：

```text
拦截方法调用
构造 RpcRequest
从注册中心发现服务
选择 provider 节点
返回远程调用结果
```

只是发送请求这一步从 HTTP 改为：

```java
RpcResponse rpcResponse = VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo);
return rpcResponse.getData();
```

所以对业务代码来说仍然是：

```java
userService.getUser(user)
```

但底层已经从 HTTP POST 变成自定义 TCP 协议。

## 与前面阶段的关系

这一章不是孤立存在的，它把前面几章都串起来了。

### 与 v0.4.0 序列化器和 SPI 的关系

协议头中的 serializer 字段会映射到：

```text
0 -> jdk
1 -> json
2 -> kryo
3 -> hessian
```

然后通过：

```java
SerializerFactory.getInstance(serializerEnum.getValue())
```

拿到真正的序列化器。

也就是说，自定义协议没有取代序列化器，而是把“使用哪种序列化器”写进了协议头。

### 与 v0.5.0 注册中心的关系

注册中心仍然负责发现 provider：

```java
List<ServiceMetaInfo> serviceMetaInfoList =
        registry.serviceDiscovery(serviceMetaInfo.getServiceKey());
```

自定义协议负责发现之后的真实调用。

可以理解为：

```text
注册中心解决：我要调谁？
自定义协议解决：我怎么跟它说话？
序列化器解决：body 怎么变成字节？
TCP 解决：字节怎么传过去？
```

这个分层非常适合秋招讲项目，因为它体现了你不是在堆代码，而是在逐层拆解 RPC 框架。

## 本阶段最终测试

本阶段没有保留教学文档里的 Hello TCP 示例，也没有保留手写半包粘包打印 demo。最终只保留和业务链路有关的测试。

### 1. 协议编解码测试

测试类：

```text
ProtocolMessageTest
```

测试目标：

- `RpcRequest` 能编码成协议 Buffer。
- 协议 Buffer 能解码回 `ProtocolMessage<RpcRequest>`。
- `RpcResponse` 能编码和解码。
- 非法 magic 会被拒绝。

命令：

```powershell
mvn -pl rpc-core "-Dtest=ProtocolMessageTest" test
```

这个测试不依赖 Etcd、ZooKeeper，也不启动 TCP server。

### 2. 半包粘包测试

测试类：

```text
TcpBufferHandlerWrapperTest
```

测试目标：

- 构造两条完整协议消息。
- 把两条消息拼在一起模拟粘包。
- 再把粘在一起的 Buffer 分段喂给 wrapper 模拟半包。
- 验证最终输出两条完整消息。

命令：

```powershell
mvn -pl rpc-core "-Dtest=TcpBufferHandlerWrapperTest" test
```

这个测试验证的是 TCP 消息边界恢复能力。

### 3. TCP 请求响应测试

测试类：

```text
VertxTcpClientTest
```

测试目标：

- 启动真实 `VertxTcpServer`。
- 在 `LocalRegistry` 中注册测试服务。
- 通过 `VertxTcpClient` 发送真实 RPC 请求。
- 服务端反射调用方法。
- 客户端收到 `RpcResponse`。

命令：

```powershell
mvn -pl rpc-core "-Dtest=VertxTcpClientTest" test
```

这个测试不依赖注册中心，因为它手动构造了 `ServiceMetaInfo`。

### 4. 完整 RPC 测试

完整测试需要注册中心。

当前配置使用 ZooKeeper：

```properties
rpc.registryConfig.registry=zookeeper
rpc.registryConfig.address=localhost:2181
```

运行顺序：

```text
1. 启动 ZooKeeper
2. 启动 ProviderExample
3. 确认 provider 控制台出现 TCP server started on port 8080
4. 启动 ConsumerExample
5. consumer 控制台输出 achingsoul、1、1
```

注意：provider 当前是 TCP server，不是 HTTP server。浏览器访问 8080 不是测试方式。

### 已验证结果

本阶段已经验证：

```powershell
mvn -pl rpc-core "-Dtest=ProtocolMessageTest,TcpBufferHandlerWrapperTest,VertxTcpClientTest" test
```

结果：

```text
Tests run: 5, Failures: 0, Errors: 0
BUILD SUCCESS
```

也验证了 provider 和 consumer 能通过编译：

```powershell
mvn -pl example-provider,example-consumer -am -DskipTests package
```

结果：

```text
BUILD SUCCESS
```

## 常见问题复盘

### 1. 为什么浏览器访问 8080 没效果

因为 8080 上现在跑的是 TCP server，不是 HTTP server。

浏览器发的是 HTTP 请求，provider 期待的是 Sosrpc 自定义协议 Buffer，两者不是同一种协议。

所以浏览器打不开不代表服务没启动。正确验证方式是：

```text
启动 ProviderExample
启动 ConsumerExample
看 consumer 控制台输出
```

### 2. 为什么 TCP 要自己处理半包粘包

因为 TCP 是字节流协议。

它不关心应用层的一条 RPC 消息从哪里开始、到哪里结束。应用层必须自己设计消息边界。

当前项目用：

```text
固定 17 字节 header + bodyLength
```

来恢复边界。

### 3. 为什么 header 里要有 bodyLength

body 是变长的。

如果没有 bodyLength，接收方无法知道这次消息应该读多少字节才算完整。

bodyLength 是 TCP 自定义协议里非常关键的字段。

### 4. 为什么 requestId 现在看起来没用

当前 TCP client 是一次请求等一次响应，所以 requestId 的作用还不明显。

但后续如果支持长连接、多路复用、异步调用，一个连接上可能同时有多个请求在飞。那就必须用 requestId 把响应匹配回请求。

所以 requestId 是为后续扩展预留的。

### 5. 为什么 TCP client 用 CompletableFuture

Vert.x 的连接、读写都是异步回调。

但是 JDK 动态代理的 `invoke` 方法需要同步返回调用结果。

所以用 `CompletableFuture` 把异步回调转成同步等待：

```text
socket.handler 收到响应
  -> complete(rpcResponse)
  -> responseFuture.get() 返回
  -> ServiceProxy 返回 data
```

### 6. 为什么每次请求都创建 NetClient

这是教学阶段实现，简单、清晰、容易验证。

生产级 RPC 框架通常会使用长连接、连接池、连接复用和心跳保活，否则高频调用下建连成本会比较高。

### 7. 为什么 ProtocolMessageDecoder 只支持 REQUEST 和 RESPONSE

当前业务链路只需要请求和响应。

`HEART_BEAT` 和 `OTHERS` 是为后续协议扩展预留的类型。后续可以用它们做 TCP 连接保活、探测、元数据同步等。

## 秋招表达版本

### 30 秒版本

可以这样讲：

> 在注册中心阶段之后，我进一步把 RPC 框架的网络传输从 HTTP 升级为 TCP，并自定义了一套二进制协议。协议头固定 17 字节，包含 magic、version、serializer、type、status、requestId 和 bodyLength，body 部分存放序列化后的 `RpcRequest` 或 `RpcResponse`。我实现了协议编码器、解码器、TCP 服务端、TCP 客户端，并使用 Vert.x 的 `RecordParser` 封装了半包粘包处理。最终 consumer 仍然通过动态代理调用接口，但底层已经变成注册中心发现 provider 后，使用自定义 TCP 协议完成调用。

### 1 分钟版本

可以这样讲：

> 这一阶段我主要解决 RPC 框架传输协议的问题。之前项目使用 HTTP POST 传输序列化后的请求，虽然简单，但 HTTP 头部和通用语义比较重，而且消息结构不完全由框架掌控。所以我参考教学文档设计了一个固定 17 字节头的自定义协议，字段包括魔数、版本号、序列化器编号、消息类型、状态、请求 id 和消息体长度。编码器会根据协议头中的序列化器字段，把 body 序列化后写入 Buffer；解码器按固定偏移读取头部，再根据 bodyLength 截取完整 body，并按消息类型反序列化成 `RpcRequest` 或 `RpcResponse`。网络层改用 Vert.x TCP server/client，并用 `RecordParser` 按“先读固定头，再读变长 body”的方式解决 TCP 半包粘包。最后 provider 启动 TCP server，consumer 通过注册中心发现地址后用 TCP client 发起请求，完成了真实 RPC 调用链路。

### 深挖版本

可以这样讲：

> 我把这一阶段理解为从“借用 HTTP 完成 RPC”走向“框架自己定义 RPC 协议”。HTTP 方案里，框架把 `RpcRequest` 放在 HTTP body 中，消息边界和元信息都依赖 HTTP；自定义协议后，框架自己定义消息头，能更紧凑地表达 RPC 所需信息。协议头里 magic 用于协议识别，version 用于后续兼容，serializer 复用前面做的序列化器 SPI，type 区分请求和响应，status 表达响应状态，requestId 为后续异步和多路复用做准备，bodyLength 则是解决 TCP 字节流半包粘包的关键。实现上我用 `ProtocolMessageEncoder/Decoder` 完成对象和 Buffer 的转换，用 `TcpBufferHandlerWrapper` 封装 `RecordParser`，保证业务 handler 收到的一定是完整消息。这样注册中心负责服务发现，自定义协议负责消息格式，TCP 负责传输，序列化器负责 body 编解码，整个 RPC 框架的分层更加清晰。

## 面试深挖问题

### 协议设计

1. 为什么 RPC 框架要自定义协议，而不是一直用 HTTP？
2. 自定义协议的消息头里应该包含哪些字段？
3. magic 魔数的作用是什么？
4. version 字段对协议演进有什么意义？
5. serializer 为什么用 byte 表示，而不是直接写字符串？
6. type 字段为什么要区分 REQUEST 和 RESPONSE？
7. status 字段和异常处理有什么关系？
8. requestId 现在和未来分别有什么作用？
9. bodyLength 为什么是 TCP 自定义协议的关键字段？
10. 为什么消息头固定 17 字节？

### 编码和解码

1. 编码器按什么顺序写入 Buffer？
2. 解码器如何根据偏移读取 header？
3. 如果编码和解码字段顺序不一致会发生什么？
4. 为什么解码器要先校验 magic？
5. 为什么 body 要根据 type 反序列化成不同对象？
6. 如果 serializer 编号不存在，应该如何处理？
7. 如果 bodyLength 和真实 body 长度不一致，会有什么问题？
8. 当前协议是否支持心跳消息？为什么暂时没有真正处理？

### TCP 网络

1. TCP 和 HTTP 在 RPC 传输中的区别是什么？
2. TCP 为什么会出现半包和粘包？
3. 半包和粘包是不是 TCP 的错误？
4. 为什么一次 `write` 不一定对应一次 `read`？
5. 解决半包粘包有哪些常见方式？
6. 当前项目为什么选择固定头 + bodyLength？
7. Vert.x 的 `RecordParser` 在这里做了什么？
8. 为什么要把半包粘包处理封装成 wrapper？

### 项目链路

1. consumer 调用 `getUser` 后完整链路是什么？
2. 注册中心和自定义协议分别负责什么？
3. `ServiceProxy` 为什么还保留服务发现逻辑？
4. provider 为什么还需要 `LocalRegistry`？
5. `TcpServerHandler` 和 `HttpServerHandler` 的相同点和不同点是什么？
6. 为什么现在浏览器访问 provider 端口没意义？
7. `CompletableFuture` 在 TCP client 中解决什么问题？
8. 当前实现为什么还不能算生产级长连接 RPC？

### 生产扩展

1. 如何实现 TCP 长连接和连接池？
2. 如何基于 requestId 支持异步调用和多路复用？
3. 如何做协议版本兼容？
4. 如何加入压缩字段？
5. 如何加入心跳和空闲连接检测？
6. 如何处理客户端超时、服务端异常和断线重连？
7. 如何防止恶意 bodyLength 导致内存问题？
8. 如何实现零拷贝或更高性能的 Buffer 管理？

## 生产级改进方向

当前实现已经打通教学阶段主链路，但距离生产级 RPC 仍有明显差距。

可以继续优化：

1. TCP client 连接复用，不要每次请求都创建 `Vertx` 和 `NetClient`。
2. 建立连接池，根据 provider 地址缓存长连接。
3. 利用 requestId 支持同一连接上的并发请求和响应匹配。
4. 增加心跳消息，及时发现断开的连接。
5. 增加请求超时、连接超时、重试和失败转移。
6. 对 bodyLength 做上限校验，防止异常大包。
7. 协议头增加压缩字段、扩展字段或 headerLength。
8. 支持协议版本兼容，不同版本走不同解析逻辑。
9. 统一错误码，让服务端异常能以协议状态返回。
10. 服务端避免每次反射都创建实现类，可以复用单例或容器管理。
11. 客户端关闭连接和 Vert.x 实例时可以更精细地管理生命周期。
12. 增加完整的端到端集成测试，覆盖注册中心 + TCP + 自定义协议。

## 本阶段收获

v0.6.0 阶段完成了 Sosrpc 从 HTTP 传输到自定义 TCP 协议传输的升级。

本阶段形成了新的分层：

```text
动态代理：拦截本地接口调用
注册中心：发现 provider 地址
自定义协议：定义消息格式和元信息
序列化器：处理 body 对象和字节数组转换
TCP：负责底层字节传输
LocalRegistry：provider 本地定位实现类
反射调用：执行目标方法
```

这一阶段最重要的认知是：RPC 框架不是简单把对象通过网络传过去，而是要自己定义“网络中的一条 RPC 消息到底长什么样”。

HTTP 方案让项目能快速跑通，自定义协议则让框架开始拥有自己的网络协议边界。

面向秋招，可以把这一阶段讲成：

```text
HTTP 传输
  -> TCP 传输
  -> 17 字节固定协议头
  -> 序列化器编号进入协议
  -> requestId 支持请求追踪
  -> bodyLength 解决消息边界
  -> RecordParser 处理半包粘包
  -> provider/consumer 接入真实 TCP 调用
```

如果面试官继续深挖，就围绕三个关键词展开：

- 协议：magic、version、serializer、type、status、requestId、bodyLength。
- 边界：TCP 是字节流，必须自己恢复应用层消息边界。
- 扩展：长连接、连接池、多路复用、心跳、超时和容错。

这一章完成之后，Sosrpc 已经不只是“HTTP 上包一层代理”的 demo，而是具备了自定义 RPC 协议的基本雏形。
