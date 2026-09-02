package com.achingsoul.sosrpc.protocol;

import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.serializer.Serializer;
import com.achingsoul.sosrpc.serializer.SerializerFactory;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;

/**
 * Protocol message decoder.
 */
public class ProtocolMessageDecoder {

    /**
     * Decode Vert.x Buffer to protocol message.
     */
    public static ProtocolMessage<?> decode(Buffer buffer) throws IOException {
        if (buffer == null || buffer.length() < ProtocolConstant.MESSAGE_HEADER_LENGTH) {
            throw new RuntimeException("消息 buffer 为空或消息头不完整");
        }

        ProtocolMessage.Header header = new ProtocolMessage.Header();
        byte magic = buffer.getByte(0);
        if (magic != ProtocolConstant.PROTOCOL_MAGIC) {
            throw new RuntimeException("消息 magic 非法");
        }
        header.setMagic(magic);
        header.setVersion(buffer.getByte(1));
        header.setSerializer(buffer.getByte(2));
        header.setType(buffer.getByte(3));
        header.setStatus(buffer.getByte(4));
        header.setRequestId(buffer.getLong(5));
        header.setBodyLength(buffer.getInt(13));

        int bodyStart = ProtocolConstant.MESSAGE_HEADER_LENGTH;
        int bodyEnd = bodyStart + header.getBodyLength();
        if (buffer.length() < bodyEnd) {
            throw new RuntimeException("消息 body 不完整");
        }
        byte[] bodyBytes = buffer.getBytes(bodyStart, bodyEnd);

        ProtocolMessageSerializerEnum serializerEnum =
                ProtocolMessageSerializerEnum.getEnumByKey(header.getSerializer());
        if (serializerEnum == null) {
            throw new RuntimeException("序列化消息的协议不存在");
        }
        Serializer serializer = SerializerFactory.getInstance(serializerEnum.getValue());

        ProtocolMessageTypeEnum messageTypeEnum =
                ProtocolMessageTypeEnum.getEnumByKey(header.getType());
        if (messageTypeEnum == null) {
            throw new RuntimeException("序列化消息的类型不存在");
        }
        switch (messageTypeEnum) {
            case REQUEST:
                RpcRequest request = serializer.deserialize(bodyBytes, RpcRequest.class);
                return new ProtocolMessage<>(header, request);
            case RESPONSE:
                RpcResponse response = serializer.deserialize(bodyBytes, RpcResponse.class);
                return new ProtocolMessage<>(header, response);
            case HEART_BEAT:
            case OTHERS:
            default:
                throw new RuntimeException("暂不支持该消息类型");
        }
    }
}
