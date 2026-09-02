package com.achingsoul.sosrpc.model;

import cn.hutool.json.JSONUtil;
import org.junit.Assert;
import org.junit.Test;

public class ServiceMetaInfoTest {

    @Test
    public void shouldBuildRegistryKeysAndRoundTripJson() {
        ServiceMetaInfo service = new ServiceMetaInfo();
        service.setServiceName("myService");
        service.setServiceVersion("1.0");
        service.setServiceHost("localhost");
        service.setServicePort(1234);

        Assert.assertEquals("myService:1.0", service.getServiceKey());
        Assert.assertEquals("myService:1.0/localhost:1234", service.getServiceNodeKey());
        Assert.assertEquals("http://localhost:1234", service.getServiceAddress());

        ServiceMetaInfo restored = JSONUtil.toBean(
                JSONUtil.toJsonStr(service), ServiceMetaInfo.class);
        Assert.assertEquals(service, restored);
    }

    @Test
    public void shouldKeepConfiguredProtocolInServiceAddress() {
        ServiceMetaInfo service = new ServiceMetaInfo();
        service.setServiceHost("http://127.0.0.1");
        service.setServicePort(8080);

        Assert.assertEquals("http://127.0.0.1:8080", service.getServiceAddress());
    }
}
