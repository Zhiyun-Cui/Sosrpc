package com.achingsoul.sosrpc.bootstrap;

import com.achingsoul.sosrpc.RpcApplication;

/**
 * Starts the common RPC components required by a service consumer.
 */
public final class ConsumerBootstrap {

    private ConsumerBootstrap() {
    }

    public static void init() {
        RpcApplication.init();
    }
}
