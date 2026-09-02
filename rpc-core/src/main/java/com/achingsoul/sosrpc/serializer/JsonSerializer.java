package com.achingsoul.sosrpc.serializer;


import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.IOException;

/**
 * JSON 序列化器
 */
public class JsonSerializer implements Serializer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public <T> byte[] serialize(T object) throws IOException {
        return OBJECT_MAPPER.writeValueAsBytes(object);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) throws IOException {
        T object = OBJECT_MAPPER.readValue(bytes, type);

        if (object instanceof RpcRequest) {
            return handleRequest((RpcRequest) object, type);
        }

        if (object instanceof RpcResponse) {
            return handleResponse((RpcResponse) object, type);
        }

        return object;
    }

    /**
     * 处理请求参数类型，避免 JSON 反序列化后参数变成 LinkedHashMap
     */
    private <T> T handleRequest(RpcRequest rpcRequest, Class<T> type) throws IOException {
        Class<?>[] parameterTypes = rpcRequest.getParameterTypes();
        Object[] args = rpcRequest.getArgs();

        if (parameterTypes == null || args == null) {
            return type.cast(rpcRequest);
        }

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            Object arg = args[i];

            if (arg != null && !parameterType.isAssignableFrom(arg.getClass())) {
                args[i] = OBJECT_MAPPER.convertValue(arg, parameterType);
            }
        }

        return type.cast(rpcRequest);
    }

    /**
     * 处理响应数据类型，避免 JSON 反序列化后 data 类型丢失
     */
    private <T> T handleResponse(RpcResponse rpcResponse, Class<T> type) {
        Object data = rpcResponse.getData();
        Class<?> dataType = rpcResponse.getDataType();

        if (data != null && dataType != null && !dataType.isAssignableFrom(data.getClass())) {
            rpcResponse.setData(OBJECT_MAPPER.convertValue(data, dataType));
        }

        return type.cast(rpcResponse);
    }
}