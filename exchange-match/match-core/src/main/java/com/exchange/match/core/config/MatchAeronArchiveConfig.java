package com.exchange.match.core.config;

import io.aeron.Aeron;
import io.aeron.archive.client.AeronArchive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Match 引擎 Aeron Archive Bean 配置。
 *
 * <p>连接到 {@link com.exchange.match.core.cluster.MatchClusterNode} 启动的嵌入式
 * Archive（默认控制端口 8010），供 {@link com.exchange.match.core.transport.AeronMatchResultPublisher}
 * 用于录制成交结果。
 *
 * <h3>激活条件</h3>
 * <p>需要 {@link Aeron} Bean 存在（{@code aeron.enabled=true}）且 Archive 控制端口可达。
 *
 * <h3>端口对应关系</h3>
 * <pre>
 *   MatchClusterNode Archive 控制端口 = 8010（CLUSTER_MEMBERS 第 6 字段）
 *   AssetClusterNode Archive 控制端口 = 8020
 * </pre>
 */
@Slf4j
@Configuration
@ConditionalOnBean(Aeron.class)
public class MatchAeronArchiveConfig implements DisposableBean {

    @Value("${match.archive.control-channel:aeron:udp?endpoint=localhost:8010}")
    private String archiveControlChannel;

    private AeronArchive aeronArchive;

    @Bean
    public AeronArchive matchAeronArchive(Aeron aeron) {
        try {
            aeronArchive = AeronArchive.connect(new AeronArchive.Context()
                    .controlRequestChannel(archiveControlChannel)
                    .controlResponseChannel("aeron:udp?endpoint=localhost:0")
                    .aeron(aeron));
            log.info("[MatchAeronArchiveConfig] AeronArchive connected — controlChannel={}",
                    archiveControlChannel);
            return aeronArchive;
        } catch (Exception e) {
            log.error("[MatchAeronArchiveConfig] Failed to connect AeronArchive — " +
                    "match results will not be durably recorded", e);
            return null;
        }
    }

    @Override
    public void destroy() {
        if (aeronArchive != null) {
            try { aeronArchive.close(); } catch (Exception ignored) {}
        }
        log.info("[MatchAeronArchiveConfig] AeronArchive closed");
    }
}
