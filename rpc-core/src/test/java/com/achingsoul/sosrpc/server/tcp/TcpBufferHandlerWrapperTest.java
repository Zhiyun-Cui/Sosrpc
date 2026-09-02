package com.achingsoul.sosrpc.server.tcp;

import cn.hutool.core.util.IdUtil;
import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.protocol.ProtocolConstant;
import com.achingsoul.sosrpc.protocol.ProtocolMessage;
import com.achingsoul.sosrpc.protocol.ProtocolMessageEncoder;
import com.achingsoul.sosrpc.protocol.ProtocolMessageSerializerEnum;
import com.achingsoul.sosrpc.protocol.ProtocolMessageStatusEnum;
import com.achingsoul.sosrpc.protocol.ProtocolMessageTypeEnum;
import io.vertx.core.buffer.Buffer;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TcpBufferHandlerWrapperTest {

    @Test
    public void shouldSplitStickyPacketsAndJoinHalfPackets() throws IOException {
        List<Buffer> completeBuffers = new ArrayList<>();
        TcpBufferHandlerWrapper wrapper =
                new TcpBufferHandlerWrapper(completeBuffers::add);

        Buffer first = ProtocolMessageEncoder.encode(buildRequestMessage("first"));
        Buffer second = ProtocolMessageEncoder.encode(buildRequestMessage("second"));
        Buffer stickyBuffer = Buffer.buffer()
                .appendBuffer(first)
                .appendBuffer(second);

        wrapper.handle(stickyBuffer.slice(0, 5));
        wrapper.handle(stickyBuffer.slice(5, 40));
        wrapper.handle(stickyBuffer.slice(40, stickyBuffer.length()));

        Assert.assertEquals(2, completeBuffers.size());
        Assert.assertEquals(first.length(), completeBuffers.get(0).length());
        Assert.assertEquals(second.length(), completeBuffers.get(1).length());
    }

    private ProtocolMessage<RpcRequest> buildRequestMessage(String methodName) {
        ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
        header.setVersion(ProtocolConstant.PROTOCOL_VERSION);
        header.setSerializer((byte) ProtocolMessageSerializerEnum.JDK.getKey());
        header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
        header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());
        header.setRequestId(IdUtil.getSnowflakeNextId());
        header.setBodyLength(0);
        protocolMessage.setHeader(header);
        protocolMessage.setBody(RpcRequest.builder()
                .serviceName("testService")
                .methodName(methodName)
                .build());
        return protocolMessage;
    }
}
