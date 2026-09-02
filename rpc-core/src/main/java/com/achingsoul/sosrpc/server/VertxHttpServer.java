package com.achingsoul.sosrpc.server;

import io.vertx.core.Vertx;

/**
 * Vert.x based HTTP server.
 */
public class VertxHttpServer implements HttpServer {

    /**
     * Start HTTP server and listen on the given port.
     * @param port server listen port
     */
    public void doStart(int port) {
        // Create Vert.x instance.
        Vertx vertx = Vertx.vertx();

        // Create HTTP server.
        io.vertx.core.http.HttpServer server = vertx.createHttpServer();

        // Register request handler.
        server.requestHandler(new HttpServerHandler());

        // Start HTTP server.
        server.listen(port, result -> {
            if (result.succeeded()) {
                System.out.println("HTTP server started on port " + port);
            } else {
                System.err.println("Failed to start HTTP server: " + result.cause());
            }
        });
    }

}
