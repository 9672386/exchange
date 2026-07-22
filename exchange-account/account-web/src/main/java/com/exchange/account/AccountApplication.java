package com.exchange.account;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 资产账户服务启动入口。
 *
 * <p>职责：
 * <ul>
 *   <li>用户资产余额管理（可用余额、冻结余额）</li>
 *   <li>资金冻结/解冻（下单/撤单时由订单服务调用）</li>
 *   <li>消费撮合引擎 {@code match-results} Kafka 主题，完成成交结算并写资金流水</li>
 *   <li>提供资产查询和资金流水查询 REST API</li>
 * </ul>
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.exchange")
@MapperScan("com.exchange.account.core.repository")
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
