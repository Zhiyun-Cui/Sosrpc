package com.achingsoul.sosrpc.config;

import com.achingsoul.sosrpc.fault.retry.RetryStrategyKeys;
import com.achingsoul.sosrpc.fault.tolerant.TolerantStrategyKeys;
import com.achingsoul.sosrpc.loadbalancer.LoadBalancerKeys;
import com.achingsoul.sosrpc.registry.RegistryConfig;
import com.achingsoul.sosrpc.serializer.SerializerKeys;
import lombok.Data;

/**
 * RPC framework configuration.
 */
@Data
public class RpcConfig {

    /**
     * Name.
     */
    private String name = "Sosrpc";

    /**
     * Version.
     */
    private String version = "1.0";

    /**
     * Server host.
     */
    private String serverHost = "localhost";

    /**
    * Server port.
    */
    private Integer serverPort = 8080;

    /**
     * mock
     */
    private boolean mock = false;

    /**
     * Serializer
     */
    private String serializer = SerializerKeys.JDK;

    /**
     * Load balancer.
     */
    private String loadBalancer = LoadBalancerKeys.ROUND_ROBIN;

    /**
     * Retry strategy.
     */
    private String retryStrategy = RetryStrategyKeys.NO;

    /**
     * Tolerant strategy.
     */
    private String tolerantStrategy = TolerantStrategyKeys.FAIL_FAST;

    /**
     * Local fallback implementation class used by the fail-back strategy.
     */
    private String fallbackClass;

    /**
     * Whether consumer-side token-bucket rate limiting is enabled.
     */
    private boolean rateLimiterEnabled = false;

    /**
     * Maximum number of tokens in the bucket.
     */
    private int rateLimitCapacity = 10;

    /**
     * Number of tokens refilled per second.
     */
    private double rateLimitRefillRate = 10D;

    /**
     * Whether the consumer-side circuit breaker is enabled.
     */
    private boolean circuitBreakerEnabled = false;

    /**
     * Consecutive failures required to open the circuit.
     */
    private int circuitBreakerFailureThreshold = 3;

    /**
     * Time in milliseconds before an open circuit enters half-open state.
     */
    private long circuitBreakerOpenDuration = 5_000L;

    /**
     * Successful trial calls required to close a half-open circuit.
     */
    private int circuitBreakerHalfOpenSuccessThreshold = 1;

    /**
     * Registry config
     */
    private RegistryConfig registryConfig = new RegistryConfig();



}
