package com.exchange.match.engine.service;

import com.exchange.match.engine.config.MatchEngineConfig;
import com.exchange.match.engine.dto.OrderCommand;
import com.exchange.match.engine.model.Order;
import com.exchange.match.engine.util.ProtostuffUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka消费者服务，负责消费订单消息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    
    private final MatchEngineConfig config;
    private final Map<String, KafkaConsumer<String, byte[]>> consumers = new ConcurrentHashMap<>();
    
    /**
     * 启动消费线程
     *
     * @param symbol     交易对
     * @param partition  分区
     * @param messageQueue 消息队列
     */
    public void startConsumerThread(String symbol, int partition, BlockingQueue<ConsumerRecord<String, byte[]>> messageQueue) {
        String consumerKey = symbol + "-" + partition;
        
        if (consumers.containsKey(consumerKey)) {
            log.warn("消费者已存在: {}", consumerKey);
            return;
        }
        
        KafkaConsumer<String, byte[]> consumer = createConsumer(symbol, partition);
        consumers.put(consumerKey, consumer);
        
        Thread consumerThread = new Thread(() -> consumeMessages(consumer, messageQueue));
        consumerThread.setName("kafka-consumer-" + consumerKey);
        consumerThread.setDaemon(true);
        consumerThread.start();
        
        log.info("启动消费线程: {}", consumerKey);
    }
    
    /**
     * 创建Kafka消费者
     *
     * @param symbol    交易对
     * @param partition 分区
     * @return Kafka消费者
     */
    private KafkaConsumer<String, byte[]> createConsumer(String symbol, int partition) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "match-engine-" + config.getNodeId());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        
        // 订阅特定分区
        TopicPartition topicPartition = new TopicPartition(config.getOrdersTopic(), partition);
        consumer.assign(Collections.singletonList(topicPartition));
        
        return consumer;
    }
    
    /**
     * 消费消息
     *
     * @param consumer     Kafka消费者
     * @param messageQueue 消息队列
     */
    private void consumeMessages(KafkaConsumer<String, byte[]> consumer, BlockingQueue<ConsumerRecord<String, byte[]>> messageQueue) {
        try {
            while (true) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, byte[]> record : records) {
                    try {
                        messageQueue.put(record);
                    } catch (InterruptedException e) {
                        log.error("消息入队列被中断", e);
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.error("消费消息异常", e);
        } finally {
            consumer.close();
        }
    }
    
    /**
     * 关闭消费者
     *
     * @param symbol    交易对
     * @param partition 分区
     */
    public void closeConsumer(String symbol, int partition) {
        String consumerKey = symbol + "-" + partition;
        KafkaConsumer<String, byte[]> consumer = consumers.remove(consumerKey);
        if (consumer != null) {
            consumer.close();
            log.info("关闭消费者: {}", consumerKey);
        }
    }
    
    /**
     * 解析订单命令
     *
     * @param record Kafka记录
     * @return 订单命令
     */
    public OrderCommand parseOrderCommand(ConsumerRecord<String, byte[]> record) {
        String key = record.key();
        byte[] value = record.value();
        
        if (OrderCommand.TYPE_NEW_ORDER.equals(key)) {
            return ProtostuffUtils.deserialize(value, OrderCommand.class);
        } else if (OrderCommand.TYPE_CANCEL_ORDER.equals(key)) {
            return ProtostuffUtils.deserialize(value, OrderCommand.class);
        } else {
            log.warn("未知的消息类型: {}", key);
            return null;
        }
    }
    
    /**
     * 将订单命令转换为订单对象
     *
     * @param command 订单命令
     * @return 订单对象
     */
    public Order convertToOrder(OrderCommand command) {
        Order order = new Order();
        order.setId(command.getOrderId());
        order.setUserId(command.getUserId());
        order.setSymbol(command.getSymbol());
        order.setType(command.getOrderType());
        order.setPrice(command.getPrice());
        order.setQuantity(command.getQuantity());
        order.setQuantityLeft(command.getQuantity());
        order.setQuantityDone(BigDecimal.ZERO);
        order.setSide(command.getSide());
        order.setAction(command.getAction());
        order.setMarginMode(command.getMarginMode());
        order.setMarginType(command.getMarginType());
        order.setPriceStop(command.getPriceStop());
        order.setVolumeMax(command.getVolumeMax());
        order.setQuantityClose(command.getQuantityClose());
        order.setStatus(1); // 等待撮合
        order.setCreateTime(command.getTimestamp());
        order.setUpdateTime(command.getTimestamp());
        return order;
    }
} 