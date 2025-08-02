package com.exchange.match.core.event.config;

import com.exchange.match.core.event.MatchEvent;
import com.exchange.match.core.event.disruptor.MatchEventHandler;
import com.exchange.match.core.event.factory.MatchEventFactory;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

/**
 * Disruptor配置类
 */
@Slf4j
@Configuration
public class DisruptorConfig {
    
    private static final int BUFFER_SIZE = 1024; // RingBuffer大小，必须是2的幂
    
    @Autowired
    private MatchEventHandler matchEventHandler;
    
    @Bean
    public Disruptor<MatchEvent> disruptor() {
        // 创建Disruptor实例
        Disruptor<MatchEvent> disruptor = new Disruptor<>(
                new MatchEventFactory(),
                BUFFER_SIZE,
                Executors.defaultThreadFactory()
        );
        
        // 设置事件处理器
        disruptor.handleEventsWith(matchEventHandler);
        
        // 启动Disruptor
        disruptor.start();
        
        log.info("Disruptor启动成功，RingBuffer大小: {}", BUFFER_SIZE);
        
        return disruptor;
    }
    
    @Bean
    public RingBuffer<MatchEvent> ringBuffer(Disruptor<MatchEvent> disruptor) {
        return disruptor.getRingBuffer();
    }
} 