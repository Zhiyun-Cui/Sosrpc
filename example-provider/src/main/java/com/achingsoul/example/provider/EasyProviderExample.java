package com.achingsoul.example.provider;

import com.achingsoul.example.common.service.UserService;
import com.achingsoul.sosrpc.RpcApplication;
import com.achingsoul.sosrpc.registry.LocalRegistry;
import com.achingsoul.sosrpc.server.HttpServer;
import com.achingsoul.sosrpc.server.VertxHttpServer;

/**
 * Simple service provider example.
 */
public class EasyProviderExample {

    public static void main(String[] args) {

        // Initialize RPC framework and register service.
        RpcApplication.init();
        LocalRegistry.register(UserService.class.getName(), UserServiceImpl.class);

        // Start HTTP server.
        HttpServer httpServer = new VertxHttpServer();
        httpServer.doStart(RpcApplication.getRpcConfig().getServerPort());

    }

}
