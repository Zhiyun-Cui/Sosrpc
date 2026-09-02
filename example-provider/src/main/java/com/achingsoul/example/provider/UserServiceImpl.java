package com.achingsoul.example.provider;

import com.achingsoul.example.common.model.User;
import com.achingsoul.example.common.service.UserService;
import com.achingsoul.sosrpc.springboot.starter.annotation.RpcService;

/**
 * User service implementation. Prints and returns the input user.
 */
@RpcService
public class UserServiceImpl implements UserService {

    public User getUser(User user) {
        System.out.println("User: " + user.getName());
        return user;
    }

}
