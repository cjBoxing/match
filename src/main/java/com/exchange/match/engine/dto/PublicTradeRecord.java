package com.exchange.match.engine.dto;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;

/**
 * 公共成交记录DTO
 */
@Data
@Builder
public class PublicTradeRecord {
    private Long tradeId; // 成交ID
    private String symbol; // 交易对
    private BigDecimal price; // 成交价格
    private BigDecimal quantity; // 成交数量
    private Integer direction; // 成交方向 1买 2卖（taker的方向）
    private Long timestamp; // 成交时间戳
} 