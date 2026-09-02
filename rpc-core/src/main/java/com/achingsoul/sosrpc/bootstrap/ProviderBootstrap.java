package com.achingsoul.sosrpc.bootstrap;

import com.achingsoul.sosrpc.RpcApplication;
import com.achingsoul.sosrpc.config.RpcConfig;
import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import com.achingsoul.sosrpc.model.ServiceRegisterInfo;
import com.achingsoul.sosrpc.registry.LocalRegistry;
import com.achingsoul.sosrpc.registry.Registry;
import com.achingsoul.sosrpc.registry.RegistryConfig;
import com.achingsoul.sosrpc.registry.RegistryFactory;
import com.achingsoul.sosrpc.server.tcp.VertxTcpServer;

import java.util.List;

/**
 * Starts the RPC framework for a service provider.
 */
public final class ProviderBootstrap {

    private ProviderBootstrap() {
    }

    /**
     * Initializes the framework, registers every service and starts the TCP server.
     *
     * @param serviceRegisterInfoList services to publish
     */
    public static void init(List<ServiceRegisterInfo<?>> serviceRegisterInfoList) {
        RpcApplication.init();
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
        Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());

        for (ServiceRegisterInfo<?> serviceRegisterInfo : serviceRegisterInfoList) {
            String serviceName = serviceRegisterInfo.getServiceName();
            LocalRegistry.register(serviceName, serviceRegisterInfo.getImplClass());

            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceName);
            serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
            serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
            try {
                registry.register(serviceMetaInfo);
            } catch (Exception e) {
                throw new RuntimeException(serviceName + " service registration failed", e);
            }
        }

        VertxTcpServer tcpServer = new VertxTcpServer();
        tcpServer.doStart(rpcConfig.getServerPort());
    }
}
