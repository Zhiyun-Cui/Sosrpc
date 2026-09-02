package com.achingsoul.sosrpc.loadbalancer;

/**
 * Load balancer keys.
 */
public interface LoadBalancerKeys {

    /**
     * Round robin.
     */
    String ROUND_ROBIN = "roundRobin";

    /**
     * Random.
     */
    String RANDOM = "random";

    /**
     * Consistent hash.
     */
    String CONSISTENT_HASH = "consistentHash";
}
