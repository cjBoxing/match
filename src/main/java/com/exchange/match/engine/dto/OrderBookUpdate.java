package com.exchange.match.engine.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;

/**
 * 订单簿更新DTO
 */
@Data
@Builder
public class OrderBookUpdate {
    private String symbol; // 交易对
    private List<OrderBookEntry> bids; // 买盘更新
    private List<OrderBookEntry> asks; // 卖盘更新
    private Long lastOffset; // 最后处理的Kafka消息的offset
    private Long timestamp; // 更新时间戳
} 