package com.exchange.match.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka消息包装器，用于所有消息的统一包装
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageWrapper<T> {
    private String messageId; // 消息ID，用于业务端排重
    private String topic; // 消息主题
    private Integer partition; // 分区
    private String type; // 消息类型
    private T data; // 消息数据
    private Long timestamp; // 时间戳
    
    /**
     * 为taker和maker的成交结果生成唯一的消息ID
     *
     * @param takerId taker订单ID
     * @param makerId maker订单ID
     * @param isTaker 是否为taker的消息
     * @return 消息ID
     */
    public static String generateTradeMessageId(Long takerId, Long makerId, boolean isTaker) {
        return String.format("T-%d-%d-%d", takerId, makerId, isTaker ? 1 : 0);
    }
    
    /**
     * 为公共成交数据生成唯一的消息ID
     *
     * @param takerId taker订单ID
     * @param makerId maker订单ID
     * @return 消息ID
     */
    public static String generatePublicTradeMessageId(Long takerId, Long makerId) {
        return String.format("PT-%d-%d", takerId, makerId);
    }
    
    /**
     * 为订单簿更新生成唯一的消息ID
     *
     * @param symbol 交易对
     * @param timestamp 时间戳
     * @return 消息ID
     */
    public static String generateOrderBookUpdateMessageId(String symbol, Long timestamp) {
        return String.format("OB-%s-%d", symbol, timestamp);
    }
} 