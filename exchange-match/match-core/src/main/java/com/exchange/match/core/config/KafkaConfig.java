package com.exchange.match.core.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Kafka配置类
 */
@Configuration
public class KafkaConfig {
    
    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${kafka.producer.batch-size:16384}")
    private int batchSize;
    
    @Value("${kafka.producer.linger-ms:5}")
    private int lingerMs;
    
    @Value("${kafka.producer.buffer-memory:33554432}")
    private int bufferMemory;
    
    @Bean
    public KafkaProducer<String, String> kafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        
        return new KafkaProducer<>(props);
    }
} 