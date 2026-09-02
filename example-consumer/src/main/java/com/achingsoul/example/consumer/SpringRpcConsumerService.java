package com.achingsoul.example.consumer;

import com.achingsoul.example.common.model.User;
import com.achingsoul.example.common.service.UserService;
import com.achingsoul.sosrpc.springboot.starter.annotation.RpcReference;
import org.springframework.stereotype.Service;

/**
 * Spring bean whose RPC dependency is injected by the starter.
 */
@Service
public class SpringRpcConsumerService {

    @RpcReference
    private UserService userService;

    public void test() {
        User user = new User();
        user.setName("spring-achingsoul");
        User result = userService.getUser(user);
        System.out.println("Spring RPC result: "
                + (result == null ? "null" : result.getName()));
    }
}
