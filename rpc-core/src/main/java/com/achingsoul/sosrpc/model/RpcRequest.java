package com.achingsoul.sosrpc.model;

import com.achingsoul.sosrpc.constant.RpcConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RpcRequest implements Serializable {

    /**
     * Service name.
     */
    private String serviceName;

    /**
     * Method name.
     */
    private String methodName;

    /**
     * Service version.
     */
    @Builder.Default
    private String serviceVersion = RpcConstant.DEFAULT_SERVICE_VERSION;

    /**
     * Parameter types.
     */
    private Class<?>[] parameterTypes;

    /**
     * Parameter values.
     */
    private Object[] args;

}
