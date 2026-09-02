package com.achingsoul.sosrpc.loadbalancer;

import com.achingsoul.sosrpc.model.ServiceMetaInfo;

import java.util.List;
import java.util.Map;

/**
 * LoadBalancer for Consumer
 */
public interface LoadBalancer {

    /**
     * Select a server from the server list
     * @param requestParams
     * @param serviceMetaInfoList
     * @return
     */
    ServiceMetaInfo select(Map<String, Object> requestParams, List<ServiceMetaInfo> serviceMetaInfoList);
}
