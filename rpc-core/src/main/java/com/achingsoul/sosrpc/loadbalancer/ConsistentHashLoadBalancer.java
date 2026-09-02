package com.achingsoul.sosrpc.loadbalancer;

import com.achingsoul.sosrpc.model.ServiceMetaInfo;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Consistent Hash Load Balancer implementation
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {

    /**
     * Consistent Hash loop, to restore virtual node
     */
    private final TreeMap<Integer, ServiceMetaInfo> virtualNodes = new TreeMap<>();

    /**
     * Virtual node count for each real service node.
     */
    private static final int VIRTUAL_NODE_NUM = 100;

    @Override
    public ServiceMetaInfo select(Map<String, Object> requestParams, List<ServiceMetaInfo> serviceMetaInfoList) {
        if (serviceMetaInfoList.isEmpty()) {
            return null;
        }

        virtualNodes.clear();
        for (ServiceMetaInfo serviceMetaInfo : serviceMetaInfoList) {
            for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
                int hash = getHash(serviceMetaInfo.getServiceAddress() + "#" + i);
                virtualNodes.put(hash, serviceMetaInfo);
            }
        }

        int hash = getHash(requestParams);

        Map.Entry<Integer, ServiceMetaInfo> entry = virtualNodes.ceilingEntry(hash);
        if (entry == null) {
            // return the first entry if no ceiling entry found
            entry = virtualNodes.firstEntry();
        }
        return entry.getValue();
    }

    /**
     * Hash algorithm implementation
     */
    private int getHash(Object key) {
        return key.hashCode();
    }

}
