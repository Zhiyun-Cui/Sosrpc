package com.achingsoul.sosrpc.model;

import com.achingsoul.sosrpc.constant.RpcConstant;
import org.junit.Assert;
import org.junit.Test;

public class RpcRequestTest {

    @Test
    public void shouldUseDefaultServiceVersionWhenBuilt() {
        RpcRequest request = RpcRequest.builder()
                .serviceName("example.UserService")
                .methodName("getUser")
                .build();

        Assert.assertEquals(RpcConstant.DEFAULT_SERVICE_VERSION,
                request.getServiceVersion());
    }
}
