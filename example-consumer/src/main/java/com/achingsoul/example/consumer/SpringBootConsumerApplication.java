package com.achingsoul.example.consumer;

import com.achingsoul.sosrpc.springboot.starter.annotation.EnableRpc;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Annotation-driven RPC consumer used for the chapter 11 test.
 */
@EnableRpc(needServer = false)
@SpringBootApplication
public class SpringBootConsumerApplication implements CommandLineRunner {

    private final SpringRpcConsumerService consumerService;

    public SpringBootConsumerApplication(SpringRpcConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringBootConsumerApplication.class, args);
    }

    @Override
    public void run(String... args) {
        consumerService.test();
    }
}
