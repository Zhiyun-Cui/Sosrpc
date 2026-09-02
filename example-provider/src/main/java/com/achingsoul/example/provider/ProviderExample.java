package com.achingsoul.example.provider;

import com.achingsoul.example.common.service.UserService;
import com.achingsoul.sosrpc.RpcApplication;
import com.achingsoul.sosrpc.config.RpcConfig;
import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import com.achingsoul.sosrpc.registry.LocalRegistry;
import com.achingsoul.sosrpc.registry.Registry;
import com.achingsoul.sosrpc.registry.RegistryConfig;
import com.achingsoul.sosrpc.registry.RegistryFactory;
import com.achingsoul.sosrpc.server.HttpServer;
import com.achingsoul.sosrpc.server.tcp.VertxTcpServer;

public class ProviderExample {

    public static void main(String[] args) {
        // Initialize RPC framework.
        RpcApplication.init();

        // Register service.
        String serviceName = UserService.class.getName();
        LocalRegistry.register(serviceName, UserServiceImpl.class);

        // Register the provider address with the registry center.
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
        Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName(serviceName);
        serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
        serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
        try {
            registry.register(serviceMetaInfo);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register service: " + serviceName, e);
        }

        // Start provider.
        HttpServer httpServer = new VertxTcpServer();
        httpServer.doStart(rpcConfig.getServerPort());
    }
}
