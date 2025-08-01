package com.exchange.message.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 消息服务启动类
 */
@SpringBootApplication
@EnableFeignClients
public class MessageApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
} 