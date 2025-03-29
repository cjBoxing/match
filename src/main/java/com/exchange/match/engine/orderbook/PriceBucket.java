package com.exchange.match.engine.orderbook;

import lombok.Getter;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 价格桶，表示订单簿中同一价格的所有订单
 */
@Getter
public class PriceBucket {
    private final BigDecimal price; // 价格
    private final Map<Long, OrderBookEntry> orders; // 订单ID -> 订单条目映射
    private BigDecimal totalVolume; // 总量
    
    /**
     * 构造函数
     *
     * @param price 价格
     */
    public PriceBucket(BigDecimal price) {
        this.price = price;
        this.orders = new LinkedHashMap<>();
        this.totalVolume = BigDecimal.ZERO;
    }
    
    /**
     * 添加订单
     *
     * @param entry 订单条目
     */
    public void addOrder(OrderBookEntry entry) {
        orders.put(entry.getOrderId(), entry);
        totalVolume = totalVolume.add(entry.getQuantity());
    }
    
    /**
     * 移除订单
     *
     * @param orderId 订单ID
     * @return 被移除的订单条目，如果不存在则返回null
     */
    public OrderBookEntry removeOrder(Long orderId) {
        OrderBookEntry entry = orders.remove(orderId);
        if (entry != null) {
            totalVolume = totalVolume.subtract(entry.getQuantity());
        }
        return entry;
    }
    
    /**
     * 获取第一个订单条目
     *
     * @return 第一个订单条目，如果没有则返回null
     */
    public OrderBookEntry getFirstOrder() {
        if (orders.isEmpty()) {
            return null;
        }
        return orders.values().iterator().next();
    }
    
    /**
     * 执行订单成交
     *
     * @param executedQty 成交数量
     * @return 是否需要从订单簿中删除该价格桶
     */
    public boolean executeTrade(BigDecimal executedQty) {
        Iterator<OrderBookEntry> it = orders.values().iterator();
        if (!it.hasNext()) {
            return true;
        }
        
        OrderBookEntry entry = it.next();
        BigDecimal remainingQty = entry.updateQuantity(executedQty);
        
        if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
            it.remove();
        }
        
        // 更新总量
        totalVolume = totalVolume.subtract(executedQty);
        
        return orders.isEmpty();
    }
    
    /**
     * 获取价格桶中的订单数量
     *
     * @return 订单数量
     */
    public int size() {
        return orders.size();
    }
    
    /**
     * 价格桶是否为空
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return orders.isEmpty();
    }
} 