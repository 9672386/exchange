package com.exchange.match.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Kafka消费者服务
 * 支持从指定offset开始消费，用于数据恢复
 */
@Slf4j
@Service
public class KafkaConsumerService implements InitializingBean, DisposableBean {
    
    @Autowired
    private KafkaOffsetManager offsetManager;
    
    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${kafka.topic.match-results:match-results}")
    private String matchResultsTopic;
    
    @Value("${kafka.topic.snapshots:snapshots}")
    private String snapshotsTopic;
    
    @Value("${kafka.consumer.group-id:match-engine-consumer}")
    private String groupId;
    
    @Value("${kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;
    
    private KafkaConsumer<String, String> consumer;
    private ScheduledExecutorService scheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void afterPropertiesSet() {
        // 初始化Kafka消费者
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        
        consumer = new KafkaConsumer<>(props);
        
        // 启动消费任务
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::consumeMessages, 0, 100, TimeUnit.MILLISECONDS);
        
        log.info("Kafka消费者服务初始化完成");
    }
    
    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (consumer != null) {
            consumer.close();
        }
        
        log.info("Kafka消费者服务已关闭");
    }
    
    /**
     * 从快照恢复后开始消费
     */
    public void startConsumingFromSnapshot(Map<String, Long> snapshotOffsets) {
        log.info("从快照开始消费: offsets={}", snapshotOffsets);
        
        try {
            // 订阅主题
            List<String> topics = Arrays.asList(matchResultsTopic, snapshotsTopic);
            consumer.subscribe(topics);
            
            // 等待分区分配
            consumer.poll(Duration.ofMillis(1000));
            
            // 设置各主题的offset
            for (Map.Entry<String, Long> entry : snapshotOffsets.entrySet()) {
                String topic = entry.getKey();
                Long offset = entry.getValue();
                
                Set<TopicPartition> partitions = consumer.assignment();
                for (TopicPartition partition : partitions) {
                    if (partition.topic().equals(topic)) {
                        consumer.seek(partition, offset);
                        log.info("设置主题{}分区{}的offset为: {}", topic, partition.partition(), offset);
                    }
                }
            }
            
            log.info("从快照offset开始消费设置完成");
            
        } catch (Exception e) {
            log.error("从快照开始消费失败", e);
            throw new RuntimeException("从快照开始消费失败", e);
        }
    }
    
    /**
     * 消费消息
     */
    private void consumeMessages() {
        try {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            
            for (ConsumerRecord<String, String> record : records) {
                try {
                    processMessage(record);
                    
                    // 手动提交offset
                    consumer.commitSync();
                    
                    // 更新offset管理器
                    updateOffsetManager(record.topic(), record.offset() + 1);
                    
                } catch (Exception e) {
                    log.error("处理消息失败: topic={}, partition={}, offset={}", 
                            record.topic(), record.partition(), record.offset(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("消费消息异常", e);
        }
    }
    
    /**
     * 处理消息
     */
    private void processMessage(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        String message = record.value();
        
        log.debug("收到消息: topic={}, partition={}, offset={}, message={}", 
                topic, record.partition(), record.offset(), message);
        
        try {
            if (topic.equals(matchResultsTopic)) {
                processMatchResult(message);
            } else if (topic.equals(snapshotsTopic)) {
                processSnapshot(message);
            } else {
                log.warn("未知主题: {}", topic);
            }
            
        } catch (JsonProcessingException e) {
            log.error("解析消息失败: topic={}, offset={}", topic, record.offset(), e);
        }
    }
    
    /**
     * 处理撮合结果消息
     */
    private void processMatchResult(String message) throws JsonProcessingException {
        // 这里可以根据实际需求处理撮合结果
        // 例如：更新内存状态、发送通知等
        log.debug("处理撮合结果消息: {}", message);
    }
    
    /**
     * 处理快照消息
     */
    private void processSnapshot(String message) throws JsonProcessingException {
        // 这里可以根据实际需求处理快照消息
        // 例如：保存快照到数据库、触发恢复流程等
        log.debug("处理快照消息: {}", message);
    }
    
    /**
     * 更新offset管理器
     */
    private void updateOffsetManager(String topic, long offset) {
        offsetManager.setOffset(topic, offset);
        offsetManager.commitOffset(topic, offset);
    }
    
    /**
     * 获取当前消费的offset
     */
    public Map<String, Long> getCurrentConsumerOffsets() {
        Map<String, Long> offsets = new HashMap<>();
        
        Set<TopicPartition> partitions = consumer.assignment();
        for (TopicPartition partition : partitions) {
            long position = consumer.position(partition);
            offsets.put(partition.topic(), position);
        }
        
        return offsets;
    }
    
    /**
     * 暂停消费
     */
    public void pauseConsuming() {
        if (consumer != null) {
            consumer.pause(consumer.assignment());
            log.info("暂停消费");
        }
    }
    
    /**
     * 恢复消费
     */
    public void resumeConsuming() {
        if (consumer != null) {
            consumer.resume(consumer.assignment());
            log.info("恢复消费");
        }
    }
    
    /**
     * 检查消费状态
     */
    public boolean isConsuming() {
        return consumer != null && !consumer.assignment().isEmpty();
    }
} 