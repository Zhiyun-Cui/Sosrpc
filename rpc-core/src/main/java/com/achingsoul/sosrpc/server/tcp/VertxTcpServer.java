package com.achingsoul.sosrpc.server.tcp;

import com.achingsoul.sosrpc.server.HttpServer;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Vert.x TCP server.
 */
@Slf4j
public class VertxTcpServer implements HttpServer {

    private Vertx vertx;

    private NetServer server;

    @Override
    public void doStart(int port) {
        vertx = Vertx.vertx();
        server = vertx.createNetServer();
        server.connectHandler(new TcpServerHandler());
        server.listen(port, result -> {
            if (result.succeeded()) {
                log.info("TCP server started on port {}", port);
            } else {
                log.error("Failed to start TCP server", result.cause());
            }
        });
    }

    /**
     * Stop server, mainly used by tests.
     */
    public void doStop() {
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        if (server != null) {
            server.close(result -> {
                if (result.failed()) {
                    closeFuture.completeExceptionally(result.cause());
                    return;
                }
                if (vertx != null) {
                    vertx.close(closeResult -> {
                        if (closeResult.failed()) {
                            closeFuture.completeExceptionally(closeResult.cause());
                        } else {
                            closeFuture.complete(null);
                        }
                    });
                } else {
                    closeFuture.complete(null);
                }
            });
        } else if (vertx != null) {
            vertx.close(result -> {
                if (result.failed()) {
                    closeFuture.completeExceptionally(result.cause());
                } else {
                    closeFuture.complete(null);
                }
            });
        } else {
            closeFuture.complete(null);
        }
        closeFuture.join();
    }
}
