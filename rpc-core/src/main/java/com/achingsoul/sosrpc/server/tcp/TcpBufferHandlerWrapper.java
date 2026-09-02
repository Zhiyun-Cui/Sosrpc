package com.achingsoul.sosrpc.server.tcp;

import com.achingsoul.sosrpc.protocol.ProtocolConstant;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;

/**
 * Wraps a Buffer handler with fixed-header/body parsing to solve TCP sticky and half packet issues.
 */
public class TcpBufferHandlerWrapper implements Handler<Buffer> {

    private final RecordParser recordParser;

    public TcpBufferHandlerWrapper(Handler<Buffer> bufferHandler) {
        recordParser = initRecordParser(bufferHandler);
    }

    @Override
    public void handle(Buffer buffer) {
        recordParser.handle(buffer);
    }

    private RecordParser initRecordParser(Handler<Buffer> bufferHandler) {
        RecordParser parser = RecordParser.newFixed(ProtocolConstant.MESSAGE_HEADER_LENGTH);
        parser.setOutput(new Handler<Buffer>() {

            int size = -1;

            Buffer resultBuffer = Buffer.buffer();

            @Override
            public void handle(Buffer buffer) {
                if (size == -1) {
                    size = buffer.getInt(13);
                    resultBuffer.appendBuffer(buffer);
                    if (size == 0) {
                        bufferHandler.handle(resultBuffer);
                        parser.fixedSizeMode(ProtocolConstant.MESSAGE_HEADER_LENGTH);
                        size = -1;
                        resultBuffer = Buffer.buffer();
                        return;
                    }
                    parser.fixedSizeMode(size);
                    return;
                }

                resultBuffer.appendBuffer(buffer);
                bufferHandler.handle(resultBuffer);
                parser.fixedSizeMode(ProtocolConstant.MESSAGE_HEADER_LENGTH);
                size = -1;
                resultBuffer = Buffer.buffer();
            }
        });
        return parser;
    }
}
