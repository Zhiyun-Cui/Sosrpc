package com.achingsoul.example.consumer;

import com.achingsoul.example.common.model.User;
import com.achingsoul.example.common.service.UserService;

/**
 * Local fallback implementation used when the remote user service is unavailable.
 */
public class UserServiceFallback implements UserService {

    @Override
    public User getUser(User user) {
        User fallbackUser = new User();
        fallbackUser.setName("fallback-user");
        return fallbackUser;
    }

    @Override
    public short getNumber() {
        return -1;
    }
}
