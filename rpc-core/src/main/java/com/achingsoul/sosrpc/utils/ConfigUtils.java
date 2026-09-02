package com.achingsoul.sosrpc.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.dialect.Props;

/**
 * Config utility.
 */
public class ConfigUtils {

    /**
     * Load config object.
     *
     * @param tClass
     * @param prefix
     * @param <T>
     * @return
     */
    public static <T> T loadConfig(Class<T> tClass, String prefix) {
        return loadConfig(tClass, prefix, "");
    }

    /**
     * Load config object for the given environment.
     *
     * @param tClass
     * @param prefix
     * @param environment
     * @param <T>
     * @return
     */
    public static <T> T loadConfig(Class<T> tClass, String prefix, String environment) {
        StringBuilder configFileBuilder = new StringBuilder("application");
        if (StrUtil.isNotBlank(environment)) {
            configFileBuilder.append("-").append(environment);
        }
        configFileBuilder.append(".properties");
        Props props = new Props(configFileBuilder.toString());
        String prefixKey = prefix + ".";
        System.getProperties().forEach((key, value) -> {
            String keyStr = String.valueOf(key);
            if (keyStr.startsWith(prefixKey)) {
                props.setProperty(keyStr, String.valueOf(value));
            }
        });
        return props.toBean(tClass, prefix);
    }
}
