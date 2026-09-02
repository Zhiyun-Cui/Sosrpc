package com.achingsoul.sosrpc;

import com.achingsoul.sosrpc.config.RpcConfig;
import com.achingsoul.sosrpc.constant.RpcConstant;
import com.achingsoul.sosrpc.registry.Registry;
import com.achingsoul.sosrpc.registry.RegistryConfig;
import com.achingsoul.sosrpc.registry.RegistryFactory;
import com.achingsoul.sosrpc.utils.ConfigUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC framework application holder.
 * Stores global RPC config and initializes it lazily.
 */
@Slf4j
public class RpcApplication {

    private static volatile RpcConfig rpcConfig;

    /**
     * Initialize framework with custom config.
     *
     * @param newRpcConfig
     */
    public static void init(RpcConfig newRpcConfig) {
        rpcConfig = newRpcConfig;
        log.info("rpc init, config: {}", newRpcConfig.toString());
        // Registry initialization
        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
        Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());
        registry.init(registryConfig);
        log.info("registry init, config: {}", registryConfig);
        // 创建并注册 Shutdown Hook，JVM 退出时执行操作
        Runtime.getRuntime().addShutdownHook(new Thread(registry::destroy));
    }

    /**
    * Initialize framework.
    */
    public static void init() {
        RpcConfig newRpcConfig;
        try {
            newRpcConfig = ConfigUtils.loadConfig(RpcConfig.class, RpcConstant.DEFAULT_CONFIG_PREFIX);
        } catch (Exception e) {
            // Use default config if loading fails.
            newRpcConfig = new RpcConfig();
        }
        init(newRpcConfig);
    }

    /**
    * Get global config.
    *
    * @return
    */
    public static RpcConfig getRpcConfig() {
        if (rpcConfig == null) {
            synchronized (RpcApplication.class) {
                if (rpcConfig == null) {
                    init();
                }
            }
        }
        return rpcConfig;
    }
}
