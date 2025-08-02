package com.exchange.match.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局命令ID生成器
 * 为每个有状态变动的事件分配唯一递增ID
 */
@Slf4j
@Component
public class CommandIdGenerator {
    
    private static final AtomicLong id = new AtomicLong(0);
    
    /**
     * 获取下一个命令ID
     */
    public static long nextId() {
        return id.incrementAndGet();
    }
    
    /**
     * 获取当前命令ID
     */
    public static long getCurrentId() {
        return id.get();
    }
    
    /**
     * 设置命令ID（用于恢复时）
     */
    public static void set(long value) {
        id.set(value);
        log.info("设置命令ID: {}", value);
    }
    
    /**
     * 重置命令ID
     */
    public static void reset() {
        id.set(0);
        log.info("重置命令ID");
    }
} 