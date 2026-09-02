package com.achingsoul.sosrpc.registry;

import org.junit.Assert;
import org.junit.Test;

public class RegistryFactoryTest {

    @Test
    public void shouldLoadAndCacheEtcdRegistryThroughSpi() {
        Registry first = RegistryFactory.getInstance(RegistryKeys.ETCD);
        Registry second = RegistryFactory.getInstance(RegistryKeys.ETCD);

        Assert.assertTrue(first instanceof EtcdRegistry);
        Assert.assertSame(first, second);
    }
}
