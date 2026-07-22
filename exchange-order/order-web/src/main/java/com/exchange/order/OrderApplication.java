package com.exchange.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动入口。
 *
 * <p>职责：
 * <ul>
 *   <li>订单生命周期管理（创建、撤单、查询）</li>
 *   <li>消费撮合引擎 {@code state-changes} Kafka 主题，持久化订单状态变更</li>
 *   <li>提供 REST API 供网关及其他服务调用</li>
 * </ul>
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.exchange")
@MapperScan("com.exchange.order.core.repository")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
