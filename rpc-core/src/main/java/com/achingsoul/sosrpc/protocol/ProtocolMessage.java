package com.achingsoul.sosrpc.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Protocol message structure.
 *
 * @param <T> message body type
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProtocolMessage<T> {

    /**
     * Message header.
     */
    private Header header;

    /**
     * Message body.
     */
    private T body;

    /**
     * Protocol message header.
     */
    @Data
    public static class Header {

        /**
         * Magic number.
         */
        private byte magic;

        /**
         * Protocol version.
         */
        private byte version;

        /**
         * Serializer key.
         */
        private byte serializer;

        /**
         * Message type.
         */
        private byte type;

        /**
         * Response status.
         */
        private byte status;

        /**
         * Request id.
         */
        private long requestId;

        /**
         * Body byte length.
         */
        private int bodyLength;
    }
}
