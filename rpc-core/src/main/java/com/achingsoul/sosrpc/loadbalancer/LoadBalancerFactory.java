package com.achingsoul.sosrpc.loadbalancer;

import com.achingsoul.sosrpc.spi.SpiLoader;

/**
 * Load balancer factory.
 */
public class LoadBalancerFactory {

    static {
        SpiLoader.load(LoadBalancer.class);
    }

    /**
     * Default load balancer.
     */
    private static final LoadBalancer DEFAULT_LOAD_BALANCER = new RoundRobinLoadBalancer();

    /**
     * Get load balancer instance by key.
     */
    public static LoadBalancer getInstance(String key) {
        return SpiLoader.getInstance(LoadBalancer.class, key);
    }
}
