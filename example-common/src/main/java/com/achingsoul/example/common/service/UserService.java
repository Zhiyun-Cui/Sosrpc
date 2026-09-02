package com.achingsoul.example.common.service;

import com.achingsoul.example.common.model.User;

/**
 * User service.
 */

public interface UserService {

    /**
     * Get user.
     * @param user
     * @return
     */
    User getUser(User user);

    /**
     * Get number
     */
    default short getNumber() {
        return 1;
    }
}
