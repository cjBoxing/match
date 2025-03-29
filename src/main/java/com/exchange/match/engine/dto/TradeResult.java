package com.exchange.match.engine.dto;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;

/**
 * 成交结果DTO
 */
@Data
@Builder
public class TradeResult {
    private Long tradeId; // 成交ID
    private Long orderId; // 订单ID
    private Long userId; // 用户ID
    private String symbol; // 交易对
    private Integer side; // 方向 1买 2卖
    private BigDecimal price; // 成交价格
    private BigDecimal quantity; // 成交数量
    private BigDecimal fee; // 手续费
    private String feeCoin; // 手续费币种
    private BigDecimal pnl; // 盈亏
    private Integer action; // 动作 0现货 1开仓 2平仓 3买卖模式仓位自动
    private Integer marginType; // 保证金类型
    private Integer marginMode; // 保证金模式
    private Boolean isMaker; // 是否是挂单方
    private Long timestamp; // 成交时间戳
} 