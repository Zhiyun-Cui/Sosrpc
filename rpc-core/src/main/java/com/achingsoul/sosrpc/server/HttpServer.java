package com.achingsoul.sosrpc.server;

/**
* HTTP server interface. Defines a common server startup method.
*/
public interface HttpServer {

    /**
    * Start server.
     * @param port server listen port
    */

    void doStart(int port);

}
