package com.achingsoul.example.consumer;

import com.achingsoul.example.common.service.UserService;
import com.achingsoul.sosrpc.proxy.ServiceProxyFactory;

/**
 * Repeated calls for observing rate limiting and circuit breaker behavior.
 */
public class FaultProtectionConsumerExample {

    public static void main(String[] args) throws InterruptedException {
        UserService userService = ServiceProxyFactory.getProxy(UserService.class);

        for (int i = 1; i <= 10; i++) {
            try {
                short result = userService.getNumber();
                System.out.printf("第 %d 次调用结果：%d%n", i, result);
            } catch (Exception e) {
                System.out.printf("第 %d 次调用失败：%s%n", i, e.getMessage());
            }
            Thread.sleep(1_000L);
        }
    }
}
