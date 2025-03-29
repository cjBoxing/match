package com.exchange.match.engine.service;

import com.exchange.match.engine.config.MatchEngineConfig;
import com.exchange.match.engine.dto.MessageWrapper;
import com.exchange.match.engine.dto.OrderBookUpdate;
import com.exchange.match.engine.dto.PublicTradeRecord;
import com.exchange.match.engine.dto.TradeResult;
import com.exchange.match.engine.matching.TradeExecutionResult;
import com.exchange.match.engine.util.ProtostuffUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka生产者服务，负责发送撮合结果消息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    
    private final MatchEngineConfig config;
    private KafkaProducer<String, byte[]> producer;
    private final BlockingQueue<ProducerRecord<String, byte[]>> messageQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread producerThread;
    
    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        producer = createProducer();
        startProducerThread();
    }
    
    /**
     * 创建Kafka生产者
     *
     * @return Kafka生产者
     */
    private KafkaProducer<String, byte[]> createProducer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        props.put("acks", "all");
        props.put("retries", 3);
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        
        return new KafkaProducer<>(props);
    }
    
    /**
     * 启动生产者线程
     */
    private void startProducerThread() {
        if (running.get()) {
            return;
        }
        
        running.set(true);
        producerThread = new Thread(this::produceMessages);
        producerThread.setName("kafka-producer-thread");
        producerThread.setDaemon(true);
        producerThread.start();
        
        log.info("启动Kafka生产者线程");
    }
    
    /**
     * 生产消息
     */
    private void produceMessages() {
        try {
            while (running.get()) {
                try {
                    ProducerRecord<String, byte[]> record = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (record != null) {
                        producer.send(record, (metadata, exception) -> {
                            if (exception != null) {
                                log.error("发送消息失败: {}", exception.getMessage(), exception);
                                try {
                                    // 重新入队
                                    messageQueue.put(record);
                                } catch (InterruptedException e) {
                                    log.error("重新入队失败", e);
                                    Thread.currentThread().interrupt();
                                }
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    log.error("消息出队列被中断", e);
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } catch (Exception e) {
            log.error("生产消息异常", e);
        }
    }
    
    /**
     * 发送撮合结果
     *
     * @param result 撮合结果
     */
    public void sendTradeExecutionResult(TradeExecutionResult result) {
        // 发送Taker的成交结果
        if (result.getTakerTradeResult() != null) {
            sendTradeResult(result.getTakerTradeResult());
        }
        
        // 发送Maker的成交结果
        for (TradeResult makerTradeResult : result.getMakerTradeResults()) {
            sendTradeResult(makerTradeResult);
        }
        
        // 发送公共成交记录
        for (PublicTradeRecord publicTradeRecord : result.getPublicTrades()) {
            sendPublicTradeRecord(publicTradeRecord);
        }
        
        // 发送订单簿更新
        if (result.getOrderBookUpdate() != null) {
            sendOrderBookUpdate(result.getOrderBookUpdate());
        }
    }
    
    /**
     * 发送成交结果
     *
     * @param tradeResult 成交结果
     */
    private void sendTradeResult(TradeResult tradeResult) {
        String topic = config.getUserTasksTopic();
        int partition = config.calculateUserPartition(tradeResult.getUserId());
        
        // 生成消息ID，用于业务端排重
        String messageId = MessageWrapper.generateTradeMessageId(
                tradeResult.getOrderId(),
                0L,  // 这里应该是对手方订单ID，但我们在TradeResult中没有保存
                !tradeResult.getIsMaker());
        
        MessageWrapper<TradeResult> messageWrapper = new MessageWrapper<>(
                messageId,
                topic,
                partition,
                "TRADE_RESULT",
                tradeResult,
                System.currentTimeMillis()
        );
        
        byte[] messageBytes = ProtostuffUtils.serialize(messageWrapper);
        
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                topic,
                partition,
                String.valueOf(tradeResult.getUserId()),
                messageBytes
        );
        
        try {
            messageQueue.put(record);
        } catch (InterruptedException e) {
            log.error("入队列失败", e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 发送公共成交记录
     *
     * @param publicTradeRecord 公共成交记录
     */
    private void sendPublicTradeRecord(PublicTradeRecord publicTradeRecord) {
        String topic = config.getTradesTopic();
        int partition = 0; // 根据实际情况确定分区
        
        // 生成消息ID，用于业务端排重
        String messageId = MessageWrapper.generatePublicTradeMessageId(
                publicTradeRecord.getTradeId(),
                0L);  // 这里应该是对手方订单ID，但我们在PublicTradeRecord中没有保存
        
        MessageWrapper<PublicTradeRecord> messageWrapper = new MessageWrapper<>(
                messageId,
                topic,
                partition,
                "PUBLIC_TRADE",
                publicTradeRecord,
                System.currentTimeMillis()
        );
        
        byte[] messageBytes = ProtostuffUtils.serialize(messageWrapper);
        
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                topic,
                partition,
                publicTradeRecord.getSymbol(),
                messageBytes
        );
        
        try {
            messageQueue.put(record);
        } catch (InterruptedException e) {
            log.error("入队列失败", e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 发送订单簿更新
     *
     * @param orderBookUpdate 订单簿更新
     */
    private void sendOrderBookUpdate(OrderBookUpdate orderBookUpdate) {
        String topic = config.getOrderBookTopic();
        int partition = 0; // 根据实际情况确定分区
        
        // 生成消息ID，用于业务端排重
        String messageId = MessageWrapper.generateOrderBookUpdateMessageId(
                orderBookUpdate.getSymbol(),
                orderBookUpdate.getTimestamp());
        
        MessageWrapper<OrderBookUpdate> messageWrapper = new MessageWrapper<>(
                messageId,
                topic,
                partition,
                "ORDER_BOOK_UPDATE",
                orderBookUpdate,
                System.currentTimeMillis()
        );
        
        byte[] messageBytes = ProtostuffUtils.serialize(messageWrapper);
        
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                topic,
                partition,
                orderBookUpdate.getSymbol(),
                messageBytes
        );
        
        try {
            messageQueue.put(record);
        } catch (InterruptedException e) {
            log.error("入队列失败", e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 销毁方法
     */
    @PreDestroy
    public void destroy() {
        running.set(false);
        if (producerThread != null) {
            producerThread.interrupt();
            try {
                producerThread.join(5000);
            } catch (InterruptedException e) {
                log.error("等待线程结束被中断", e);
                Thread.currentThread().interrupt();
            }
        }
        
        if (producer != null) {
            producer.close();
        }
        
        log.info("关闭Kafka生产者");
    }
} 