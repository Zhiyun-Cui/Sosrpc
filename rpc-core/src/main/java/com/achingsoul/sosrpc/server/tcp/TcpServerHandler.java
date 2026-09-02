package com.achingsoul.sosrpc.server.tcp;

import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.protocol.ProtocolMessage;
import com.achingsoul.sosrpc.protocol.ProtocolMessageDecoder;
import com.achingsoul.sosrpc.protocol.ProtocolMessageEncoder;
import com.achingsoul.sosrpc.protocol.ProtocolMessageStatusEnum;
import com.achingsoul.sosrpc.protocol.ProtocolMessageTypeEnum;
import com.achingsoul.sosrpc.registry.LocalRegistry;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * TCP request handler.
 */
public class TcpServerHandler implements Handler<NetSocket> {

    @Override
    public void handle(NetSocket netSocket) {
        TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
            ProtocolMessage<RpcRequest> protocolMessage;
            try {
                protocolMessage = (ProtocolMessage<RpcRequest>) ProtocolMessageDecoder.decode(buffer);
            } catch (IOException e) {
                throw new RuntimeException("协议消息解码错误", e);
            }

            RpcRequest rpcRequest = protocolMessage.getBody();
            RpcResponse rpcResponse = new RpcResponse();
            try {
                Class<?> implClass = LocalRegistry.get(rpcRequest.getServiceName());
                Method method = implClass.getMethod(
                        rpcRequest.getMethodName(), rpcRequest.getParameterTypes());
                Object result = method.invoke(
                        implClass.getDeclaredConstructor().newInstance(), rpcRequest.getArgs());

                rpcResponse.setData(result);
                rpcResponse.setDataType(method.getReturnType());
                rpcResponse.setMessage("ok");
            } catch (Exception e) {
                e.printStackTrace();
                rpcResponse.setMessage(e.getMessage());
                rpcResponse.setException(e);
            }

            ProtocolMessage.Header header = protocolMessage.getHeader();
            header.setType((byte) ProtocolMessageTypeEnum.RESPONSE.getKey());
            header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());
            ProtocolMessage<RpcResponse> responseProtocolMessage =
                    new ProtocolMessage<>(header, rpcResponse);
            try {
                Buffer encode = ProtocolMessageEncoder.encode(responseProtocolMessage);
                netSocket.write(encode);
            } catch (IOException e) {
                throw new RuntimeException("协议消息编码错误", e);
            }
        });
        netSocket.handler(bufferHandlerWrapper);
    }
}
