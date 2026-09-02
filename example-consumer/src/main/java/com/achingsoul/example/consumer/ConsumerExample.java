package com.achingsoul.example.consumer;

import com.achingsoul.example.common.model.User;
import com.achingsoul.example.common.service.UserService;
import com.achingsoul.sosrpc.config.RpcConfig;
import com.achingsoul.sosrpc.proxy.ServiceProxyFactory;
import com.achingsoul.sosrpc.utils.ConfigUtils;

/**
 * Service consumer example.
 */
public class ConsumerExample {

    public static void main(String[] args) {

        // get proxy
        UserService userService = ServiceProxyFactory.getProxy(UserService.class);
        User user = new User();
        user.setName("achingsoul");

        // Invoke remote method.
        User newUser = userService.getUser(user);
        if (newUser != null) {
            System.out.println(newUser.getName());
        } else {
            System.out.println("user == null");
        }

        long number = userService.getNumber();
        System.out.println(number);

        long number2 = userService.getNumber();   // 第三次
        System.out.println(number2);
    }
}
