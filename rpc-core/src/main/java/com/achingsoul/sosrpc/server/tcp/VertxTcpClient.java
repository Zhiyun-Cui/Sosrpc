package com.achingsoul.sosrpc.server.tcp;

import cn.hutool.core.util.IdUtil;
import com.achingsoul.sosrpc.RpcApplication;
import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import com.achingsoul.sosrpc.protocol.ProtocolConstant;
import com.achingsoul.sosrpc.protocol.ProtocolMessage;
import com.achingsoul.sosrpc.protocol.ProtocolMessageDecoder;
import com.achingsoul.sosrpc.protocol.ProtocolMessageEncoder;
import com.achingsoul.sosrpc.protocol.ProtocolMessageSerializerEnum;
import com.achingsoul.sosrpc.protocol.ProtocolMessageStatusEnum;
import com.achingsoul.sosrpc.protocol.ProtocolMessageTypeEnum;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Vert.x TCP client.
 */
public class VertxTcpClient {

    /**
     * Send request and wait for response.
     */
    public static RpcResponse doRequest(RpcRequest rpcRequest, ServiceMetaInfo serviceMetaInfo) {
        Vertx vertx = Vertx.vertx();
        NetClient netClient = vertx.createNetClient();
        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();

        netClient.connect(serviceMetaInfo.getServicePort(), serviceMetaInfo.getServiceHost(), result -> {
            if (!result.succeeded()) {
                responseFuture.completeExceptionally(result.cause());
                return;
            }

            NetSocket socket = result.result();
            TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
                try {
                    ProtocolMessage<RpcResponse> rpcResponseProtocolMessage =
                            (ProtocolMessage<RpcResponse>) ProtocolMessageDecoder.decode(buffer);
                    responseFuture.complete(rpcResponseProtocolMessage.getBody());
                } catch (IOException e) {
                    responseFuture.completeExceptionally(e);
                }
            });
            socket.handler(bufferHandlerWrapper);

            ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
            ProtocolMessage.Header header = new ProtocolMessage.Header();
            header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
            header.setVersion(ProtocolConstant.PROTOCOL_VERSION);
            ProtocolMessageSerializerEnum serializerEnum =
                    ProtocolMessageSerializerEnum.getEnumByValue(
                            RpcApplication.getRpcConfig().getSerializer());
            if (serializerEnum == null) {
                responseFuture.completeExceptionally(
                        new RuntimeException("序列化协议不存在"));
                return;
            }
            header.setSerializer((byte) serializerEnum.getKey());
            header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
            header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());
            header.setRequestId(IdUtil.getSnowflakeNextId());
            protocolMessage.setHeader(header);
            protocolMessage.setBody(rpcRequest);

            try {
                Buffer encodeBuffer = ProtocolMessageEncoder.encode(protocolMessage);
                socket.write(encodeBuffer);
            } catch (IOException e) {
                responseFuture.completeExceptionally(e);
            }
        });

        try {
            return responseFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("TCP request failed", e);
        } finally {
            netClient.close();
            vertx.close();
        }
    }
}
