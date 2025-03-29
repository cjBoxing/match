package com.exchange.match.engine.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单命令DTO，用于接收新订单或撤单请求
 */
@Data
public class OrderCommand {
    public static final String TYPE_NEW_ORDER = "NEW";
    public static final String TYPE_CANCEL_ORDER = "CANCEL";
    
    private String type; // NEW 或 CANCEL
    private Long orderId; // 订单ID
    private Long userId; // 用户ID
    private String symbol; // 交易对
    private Integer orderType; // 订单类型 1 限价 2 只做挂单 3 全成交或全取消 4 成交后取消剩余 5市价委托
    private BigDecimal price; // 价格
    private BigDecimal quantity; // 数量
    private Integer side; // 方向 1买 2卖
    private Integer action; // 动作 0现货 1开仓 2平仓 3买卖模式仓位自动
    private Integer marginMode; // 保证金模式 0普通现货 1逐仓 2全仓
    private Integer marginType; // 保证金类型 0现货 1币本位 2U本位
    private BigDecimal priceStop; // 止损价
    private BigDecimal volumeMax; // 最大成交额（市价单）
    private BigDecimal quantityClose; // 平仓数量
    private Long timestamp; // 时间戳
} 