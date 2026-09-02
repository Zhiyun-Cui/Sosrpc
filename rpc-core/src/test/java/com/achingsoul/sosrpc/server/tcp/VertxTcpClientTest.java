package com.achingsoul.sosrpc.server.tcp;

import cn.hutool.core.net.NetUtil;
import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import com.achingsoul.sosrpc.registry.LocalRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class VertxTcpClientTest {

    private VertxTcpServer server;

    private String serviceName;

    @After
    public void tearDown() {
        if (server != null) {
            server.doStop();
        }
        if (serviceName != null) {
            LocalRegistry.remove(serviceName);
        }
    }

    @Test
    public void shouldSendRequestAndReceiveResponse() throws Exception {
        serviceName = EchoService.class.getName();
        LocalRegistry.register(serviceName, EchoServiceImpl.class);
        int port = NetUtil.getUsableLocalPort();
        server = new VertxTcpServer();
        server.doStart(port);
        Thread.sleep(500);

        RpcRequest rpcRequest = RpcRequest.builder()
                .serviceName(serviceName)
                .methodName("echo")
                .parameterTypes(new Class[]{String.class})
                .args(new Object[]{"tcp"})
                .build();
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceHost("localhost");
        serviceMetaInfo.setServicePort(port);

        RpcResponse rpcResponse =
                VertxTcpClient.doRequest(rpcRequest, serviceMetaInfo);

        Assert.assertNotNull(rpcResponse);
        Assert.assertEquals("echo:tcp", rpcResponse.getData());
        Assert.assertEquals("ok", rpcResponse.getMessage());
    }

    public interface EchoService {

        String echo(String text);
    }

    public static class EchoServiceImpl implements EchoService {

        @Override
        public String echo(String text) {
            return "echo:" + text;
        }
    }
}
