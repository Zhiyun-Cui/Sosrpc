package com.achingsoul.sosrpc.springboot.starter.bootstrap;

import com.achingsoul.sosrpc.RpcApplication;
import com.achingsoul.sosrpc.config.RpcConfig;
import com.achingsoul.sosrpc.server.tcp.VertxTcpServer;
import com.achingsoul.sosrpc.springboot.starter.annotation.EnableRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

/**
 * Initializes ACRPC while Spring processes {@link EnableRpc}.
 */
public class RpcInitBootstrap implements ImportBeanDefinitionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(RpcInitBootstrap.class);

    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata importingClassMetadata,
            BeanDefinitionRegistry registry) {
        Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(
                EnableRpc.class.getName());
        boolean needServer = attributes == null
                || Boolean.TRUE.equals(attributes.get("needServer"));

        RpcApplication.init();
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        if (needServer) {
            VertxTcpServer tcpServer = new VertxTcpServer();
            tcpServer.doStart(rpcConfig.getServerPort());
        } else {
            log.info("Consumer mode: RPC TCP server will not be started");
        }
    }
}
