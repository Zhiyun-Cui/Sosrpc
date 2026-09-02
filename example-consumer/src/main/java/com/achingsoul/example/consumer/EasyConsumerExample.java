package com.achingsoul.example.consumer;

import com.achingsoul.example.common.model.User;
import com.achingsoul.example.common.service.UserService;
import com.achingsoul.sosrpc.proxy.ServiceProxyFactory;

/**
 * Simple service consumer example.
 */
public class EasyConsumerExample {

    public static void main(String[] args) {
        // Get a UserService proxy object.
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
    }
}
