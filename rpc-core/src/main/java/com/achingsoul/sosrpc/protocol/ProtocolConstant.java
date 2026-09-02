package com.achingsoul.sosrpc.protocol;

/**
 * Protocol constants.
 */
public interface ProtocolConstant {

    /**
     * Fixed protocol message header length.
     */
    int MESSAGE_HEADER_LENGTH = 17;

    /**
     * Magic number, used to identify Sosrpc protocol messages.
     */
    byte PROTOCOL_MAGIC = 0x1;

    /**
     * Protocol version.
     */
    byte PROTOCOL_VERSION = 0x1;
}
