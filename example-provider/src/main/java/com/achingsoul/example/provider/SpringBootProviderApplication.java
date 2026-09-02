package com.achingsoul.example.provider;

import com.achingsoul.sosrpc.springboot.starter.annotation.EnableRpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Annotation-driven RPC provider used for the chapter 11 test.
 */
@EnableRpc
@SpringBootApplication
public class SpringBootProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootProviderApplication.class, args);
    }
}
