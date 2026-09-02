package com.achingsoul.sosrpc.loadbalancer;

import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoadBalancerTest {

    private final List<ServiceMetaInfo> serviceMetaInfoList = Arrays.asList(
            buildServiceMetaInfo("localhost", 8080),
            buildServiceMetaInfo("localhost", 8081),
            buildServiceMetaInfo("localhost", 8082)
    );

    @Test
    public void roundRobinShouldSelectInOrder() {
        LoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        Map<String, Object> requestParams = new HashMap<>();

        Assert.assertEquals(8080, loadBalancer.select(
                requestParams, serviceMetaInfoList).getServicePort().intValue());
        Assert.assertEquals(8081, loadBalancer.select(
                requestParams, serviceMetaInfoList).getServicePort().intValue());
        Assert.assertEquals(8082, loadBalancer.select(
                requestParams, serviceMetaInfoList).getServicePort().intValue());
        Assert.assertEquals(8080, loadBalancer.select(
                requestParams, serviceMetaInfoList).getServicePort().intValue());
    }

    @Test
    public void randomShouldSelectAvailableService() {
        LoadBalancer loadBalancer = new RandomLoadBalancer();
        ServiceMetaInfo selected = loadBalancer.select(
                new HashMap<>(), serviceMetaInfoList);

        Assert.assertNotNull(selected);
        Assert.assertTrue(serviceMetaInfoList.contains(selected));
    }

    @Test
    public void consistentHashShouldSelectSameNodeForSameRequest() {
        LoadBalancer loadBalancer = new ConsistentHashLoadBalancer();
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("methodName", "getUser");

        ServiceMetaInfo first = loadBalancer.select(requestParams, serviceMetaInfoList);
        ServiceMetaInfo second = loadBalancer.select(requestParams, serviceMetaInfoList);
        ServiceMetaInfo third = loadBalancer.select(requestParams, serviceMetaInfoList);

        Assert.assertNotNull(first);
        Assert.assertEquals(first, second);
        Assert.assertEquals(second, third);
    }

    @Test
    public void factoryShouldLoadLoadBalancerBySpi() {
        Assert.assertTrue(LoadBalancerFactory.getInstance(
                LoadBalancerKeys.ROUND_ROBIN) instanceof RoundRobinLoadBalancer);
        Assert.assertTrue(LoadBalancerFactory.getInstance(
                LoadBalancerKeys.RANDOM) instanceof RandomLoadBalancer);
        Assert.assertTrue(LoadBalancerFactory.getInstance(
                LoadBalancerKeys.CONSISTENT_HASH) instanceof ConsistentHashLoadBalancer);
    }

    private static ServiceMetaInfo buildServiceMetaInfo(String host, int port) {
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName("com.achingsoul.example.common.service.UserService");
        serviceMetaInfo.setServiceVersion("1.0");
        serviceMetaInfo.setServiceHost(host);
        serviceMetaInfo.setServicePort(port);
        return serviceMetaInfo;
    }
}
