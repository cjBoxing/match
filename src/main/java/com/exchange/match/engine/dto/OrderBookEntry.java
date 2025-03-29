package com.exchange.match.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 订单簿条目DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookEntry {
    private BigDecimal price; // 价格
    private BigDecimal quantity; // 数量
    private Long orderId; // 订单ID
    private Long userId; // 用户ID
} 