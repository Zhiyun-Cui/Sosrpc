package com.achingsoul.sosrpc.registry;

import com.achingsoul.sosrpc.model.ServiceMetaInfo;

import java.util.List;

/**
 * Registry interface
 */
public interface Registry {

    /**
     * initialize registry
     */
    void init(RegistryConfig registryConfig);

    /**
     * register service
     * @param serviceMetaInfo
     */
    void register(ServiceMetaInfo serviceMetaInfo) throws Exception;

    /**
    * unregister service
    * @param serviceMetaInfo
    */
    void unregister(ServiceMetaInfo serviceMetaInfo);

    /**
    * get service meta info by service key
    * @param serviceKey
    * @return
    */
    List<ServiceMetaInfo> serviceDiscovery(String serviceKey);

    /**
     * 心跳检测（服务端）
     */
    void heartBeat();

    /**
     * 监听（消费端）
     *
     * @param serviceNodeKey
     */
    void watch(String serviceNodeKey);


    /**
    * remove service meta info by service node key
    *
    */
    void destroy();

}
