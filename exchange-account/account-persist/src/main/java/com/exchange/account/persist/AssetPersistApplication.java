package com.exchange.account.persist;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 资产持久化服务入口。
 *
 * <h3>职责</h3>
 * <p>订阅 Aeron Archive（asset-state-changes 录制），将 Asset Cluster 内存账本的每次状态变更
 * 落库到 {@code t_fund_flow}（资金流水）和 {@code t_user_asset}（余额快照）。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>无业务逻辑：只做数据的 transform + write，不做余额计算。</li>
 *   <li>幂等写入：以 {@code eventId} 为幂等键，防止重放导致重复写。</li>
 *   <li>消费位点持久化：{@code t_archive_position} 记录已消费的 byte position，
 *       重启后续读，等价于 Kafka offset。</li>
 * </ul>
 *
 * <h3>启动端口</h3>
 * <p>默认 {@code 8086}（与 account-web 8085 区分）。
 */
@SpringBootApplication(scanBasePackages = {
        "com.exchange.account.persist",
        "com.exchange.account.core.service",      // FundFlowServiceImpl, AssetServiceImpl
        "com.exchange.account.core.repository"    // MyBatis-Plus mappers
})
@MapperScan({"com.exchange.account.core.repository", "com.exchange.account.persist.repository"})
public class AssetPersistApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssetPersistApplication.class, args);
    }
}
