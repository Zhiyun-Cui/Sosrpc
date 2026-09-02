package com.achingsoul.sosrpc.registry;

import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class EtcdRegistryIntegrationTest {

    private Registry registry;

    @Before
    public void setUp() {
        Assume.assumeTrue("Set -Detcd.integration=true to run this test",
                Boolean.getBoolean("etcd.integration"));

        RegistryConfig config = new RegistryConfig();
        config.setAddress(System.getProperty("etcd.address", "http://localhost:2379"));
        registry = new EtcdRegistry();
        registry.init(config);
    }

    @After
    public void tearDown() {
        if (registry != null) {
            registry.destroy();
        }
    }

    @Test
    public void shouldRegisterDiscoverAndUnregisterService() throws Exception {
        ServiceMetaInfo service = new ServiceMetaInfo();
        service.setServiceName("integrationService");
        service.setServiceVersion("1.0");
        service.setServiceHost("localhost");
        service.setServicePort(1234);

        registry.register(service);
        List<ServiceMetaInfo> discovered = registry.serviceDiscovery(service.getServiceKey());
        Assert.assertTrue(discovered.contains(service));

        registry.unregister(service);
        Assert.assertTrue(registry.serviceDiscovery(service.getServiceKey()).isEmpty());
    }
}
