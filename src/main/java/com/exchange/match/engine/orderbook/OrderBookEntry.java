package com.exchange.match.engine.orderbook;

import com.exchange.match.engine.model.Order;
import lombok.Getter;
import java.math.BigDecimal;

/**
 * 订单簿条目，表示订单簿中的一个条目
 */
@Getter
public class OrderBookEntry {
    private final Long orderId; // 订单ID
    private final Long userId; // 用户ID
    private final BigDecimal price; // 价格
    private BigDecimal quantity; // 数量
    private final Integer orderType; // 订单类型
    private final Integer side; // 买卖方向
    private final Long timestamp; // 时间戳
    
    /**
     * 从Order对象构建OrderBookEntry
     *
     * @param order 订单对象
     */
    public OrderBookEntry(Order order) {
        this.orderId = order.getId();
        this.userId = order.getUserId();
        this.price = order.getPrice();
        this.quantity = order.getQuantityLeft();
        this.orderType = order.getType();
        this.side = order.getSide();
        this.timestamp = order.getCreateTime();
    }
    
    /**
     * 更新数量
     *
     * @param executedQty 成交数量
     * @return 剩余数量
     */
    public BigDecimal updateQuantity(BigDecimal executedQty) {
        this.quantity = this.quantity.subtract(executedQty);
        return this.quantity;
    }
    
    /**
     * 是否已完全成交
     *
     * @return 是否已完全成交
     */
    public boolean isFullyExecuted() {
        return this.quantity.compareTo(BigDecimal.ZERO) <= 0;
    }
    
    /**
     * 转换为传输对象
     *
     * @return 传输对象
     */
    public com.exchange.match.engine.dto.OrderBookEntry toDto() {
        return new com.exchange.match.engine.dto.OrderBookEntry(
                this.price, this.quantity, this.orderId, this.userId
        );
    }
} 