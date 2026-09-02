package com.achingsoul.sosrpc.protocol;

import cn.hutool.core.util.IdUtil;
import com.achingsoul.sosrpc.constant.RpcConstant;
import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import io.vertx.core.buffer.Buffer;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class ProtocolMessageTest {

    @Test
    public void shouldEncodeAndDecodeRequest() throws IOException {
        ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
        ProtocolMessage.Header header = buildHeader(
                ProtocolMessageTypeEnum.REQUEST,
                ProtocolMessageStatusEnum.OK);
        RpcRequest rpcRequest = RpcRequest.builder()
                .serviceName("myService")
                .methodName("myMethod")
                .serviceVersion(RpcConstant.DEFAULT_SERVICE_VERSION)
                .parameterTypes(new Class[]{String.class})
                .args(new Object[]{"aaa"})
                .build();
        protocolMessage.setHeader(header);
        protocolMessage.setBody(rpcRequest);

        Buffer encodeBuffer = ProtocolMessageEncoder.encode(protocolMessage);
        ProtocolMessage<?> decoded = ProtocolMessageDecoder.decode(encodeBuffer);

        Assert.assertNotNull(decoded);
        Assert.assertEquals(ProtocolConstant.PROTOCOL_MAGIC,
                decoded.getHeader().getMagic());
        Assert.assertEquals(ProtocolMessageTypeEnum.REQUEST.getKey(),
                decoded.getHeader().getType());
        Assert.assertTrue(decoded.getBody() instanceof RpcRequest);
        RpcRequest decodedRequest = (RpcRequest) decoded.getBody();
        Assert.assertEquals("myService", decodedRequest.getServiceName());
        Assert.assertEquals("myMethod", decodedRequest.getMethodName());
    }

    @Test
    public void shouldEncodeAndDecodeResponse() throws IOException {
        ProtocolMessage<RpcResponse> protocolMessage = new ProtocolMessage<>();
        ProtocolMessage.Header header = buildHeader(
                ProtocolMessageTypeEnum.RESPONSE,
                ProtocolMessageStatusEnum.OK);
        RpcResponse rpcResponse = RpcResponse.builder()
                .data("ok-data")
                .dataType(String.class)
                .message("ok")
                .build();
        protocolMessage.setHeader(header);
        protocolMessage.setBody(rpcResponse);

        Buffer encodeBuffer = ProtocolMessageEncoder.encode(protocolMessage);
        ProtocolMessage<?> decoded = ProtocolMessageDecoder.decode(encodeBuffer);

        Assert.assertNotNull(decoded);
        Assert.assertEquals(ProtocolMessageTypeEnum.RESPONSE.getKey(),
                decoded.getHeader().getType());
        Assert.assertTrue(decoded.getBody() instanceof RpcResponse);
        RpcResponse decodedResponse = (RpcResponse) decoded.getBody();
        Assert.assertEquals("ok-data", decodedResponse.getData());
        Assert.assertEquals("ok", decodedResponse.getMessage());
    }

    @Test(expected = RuntimeException.class)
    public void shouldRejectInvalidMagic() throws IOException {
        ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
        ProtocolMessage.Header header = buildHeader(
                ProtocolMessageTypeEnum.REQUEST,
                ProtocolMessageStatusEnum.OK);
        header.setMagic((byte) 0x2);
        protocolMessage.setHeader(header);
        protocolMessage.setBody(RpcRequest.builder()
                .serviceName("myService")
                .methodName("myMethod")
                .build());

        Buffer encodeBuffer = ProtocolMessageEncoder.encode(protocolMessage);
        ProtocolMessageDecoder.decode(encodeBuffer);
    }

    private ProtocolMessage.Header buildHeader(
            ProtocolMessageTypeEnum typeEnum,
            ProtocolMessageStatusEnum statusEnum) {
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
        header.setVersion(ProtocolConstant.PROTOCOL_VERSION);
        header.setSerializer((byte) ProtocolMessageSerializerEnum.JDK.getKey());
        header.setType((byte) typeEnum.getKey());
        header.setStatus((byte) statusEnum.getValue());
        header.setRequestId(IdUtil.getSnowflakeNextId());
        header.setBodyLength(0);
        return header;
    }
}
