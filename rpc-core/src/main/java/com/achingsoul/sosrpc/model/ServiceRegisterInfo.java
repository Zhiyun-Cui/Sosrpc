package com.achingsoul.sosrpc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Information required to publish a service.
 *
 * @param <T> service interface type
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceRegisterInfo<T> {

    /**
     * Fully qualified service interface name.
     */
    private String serviceName;

    /**
     * Service implementation class.
     */
    private Class<? extends T> implClass;
}
