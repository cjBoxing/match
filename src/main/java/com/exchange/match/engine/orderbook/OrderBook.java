package com.exchange.match.engine.orderbook;

import com.exchange.match.engine.dto.OrderBookUpdate;
import com.exchange.match.engine.model.Order;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单簿，表示一个交易对的完整订单簿
 */
public class OrderBook {
    private final String symbol; // 交易对
    @Getter
    private long lastProcessedOffset; // 最后处理的offset
    
    // 买单价格降序排列
    @Getter
    private final NavigableMap<BigDecimal, PriceBucket> bids;
    // 卖单价格升序排列
    @Getter
    private final NavigableMap<BigDecimal, PriceBucket> asks;
    // 订单ID到订单条目的映射
    private final Map<Long, OrderBookEntry> orderMap;
    
    /**
     * 构造函数
     *
     * @param symbol 交易对
     */
    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.lastProcessedOffset = -1;
        
        // 买单降序，使用TreeMap替代ConcurrentSkipListMap
        this.bids = new TreeMap<>(Comparator.reverseOrder());
        // 卖单升序，使用TreeMap替代ConcurrentSkipListMap
        this.asks = new TreeMap<>();
        // 使用HashMap替代ConcurrentHashMap
        this.orderMap = new HashMap<>();
    }
    
    /**
     * 添加订单
     *
     * @param order 订单
     */
    public void addOrder(Order order) {
        // 创建订单条目
        OrderBookEntry entry = new OrderBookEntry(order);
        
        // 买单
        if (order.getSide() == 1) {
            addToOrderBook(bids, entry);
        } 
        // 卖单
        else {
            addToOrderBook(asks, entry);
        }
        
        // 添加到订单映射
        orderMap.put(order.getId(), entry);
    }
    
    /**
     * 添加订单到订单簿
     *
     * @param book  订单簿（买单或卖单）
     * @param entry 订单条目
     */
    private void addToOrderBook(NavigableMap<BigDecimal, PriceBucket> book, OrderBookEntry entry) {
        BigDecimal price = entry.getPrice();
        PriceBucket bucket = book.computeIfAbsent(price, PriceBucket::new);
        bucket.addOrder(entry);
    }
    
    /**
     * 取消订单
     *
     * @param orderId 订单ID
     * @return 被取消的订单条目，如果不存在则返回null
     */
    public OrderBookEntry cancelOrder(Long orderId) {
        OrderBookEntry entry = orderMap.remove(orderId);
        if (entry == null) {
            return null;
        }
        
        // 买单
        if (entry.getSide() == 1) {
            removeFromOrderBook(bids, entry);
        } 
        // 卖单
        else {
            removeFromOrderBook(asks, entry);
        }
        
        return entry;
    }
    
    /**
     * 从订单簿中移除订单
     *
     * @param book  订单簿（买单或卖单）
     * @param entry 订单条目
     */
    private void removeFromOrderBook(NavigableMap<BigDecimal, PriceBucket> book, OrderBookEntry entry) {
        BigDecimal price = entry.getPrice();
        PriceBucket bucket = book.get(price);
        if (bucket != null) {
            bucket.removeOrder(entry.getOrderId());
            if (bucket.isEmpty()) {
                book.remove(price);
            }
        }
    }
    
    /**
     * 获取最优买单价格
     *
     * @return 最优买单价格，如果没有则返回null
     */
    public BigDecimal getBestBidPrice() {
        return bids.isEmpty() ? null : bids.firstKey();
    }
    
    /**
     * 获取最优卖单价格
     *
     * @return 最优卖单价格，如果没有则返回null
     */
    public BigDecimal getBestAskPrice() {
        return asks.isEmpty() ? null : asks.firstKey();
    }
    
    /**
     * 获取价格桶
     *
     * @param price 价格
     * @param side  买卖方向（1买/2卖）
     * @return 价格桶，如果不存在则返回null
     */
    public PriceBucket getBucket(BigDecimal price, int side) {
        return side == 1 ? bids.get(price) : asks.get(price);
    }
    
    /**
     * 获取最优买单的价格桶
     *
     * @return 最优买单的价格桶，如果没有则返回null
     */
    public PriceBucket getBestBidBucket() {
        return bids.isEmpty() ? null : bids.firstEntry().getValue();
    }
    
    /**
     * 获取最优卖单的价格桶
     *
     * @return 最优卖单的价格桶，如果没有则返回null
     */
    public PriceBucket getBestAskBucket() {
        return asks.isEmpty() ? null : asks.firstEntry().getValue();
    }
    
    /**
     * 获取订单条目
     *
     * @param orderId 订单ID
     * @return 订单条目，如果不存在则返回null
     */
    public OrderBookEntry getOrder(Long orderId) {
        return orderMap.get(orderId);
    }
    
    /**
     * 更新最后处理的offset
     *
     * @param offset offset
     */
    public void updateLastProcessedOffset(long offset) {
        this.lastProcessedOffset = offset;
    }
    
    /**
     * 生成订单簿更新
     *
     * @param modifiedBids 修改的买单
     * @param modifiedAsks 修改的卖单
     * @return 订单簿更新
     */
    public OrderBookUpdate generateUpdate(Set<BigDecimal> modifiedBids, Set<BigDecimal> modifiedAsks) {
        // 转换买单
        List<com.exchange.match.engine.dto.OrderBookEntry> bidUpdates = modifiedBids.stream()
                .map(price -> {
                    PriceBucket bucket = bids.get(price);
                    if (bucket == null || bucket.isEmpty()) {
                        // 如果价格桶不存在或为空，则返回数量为0的条目，表示删除该价格等级
                        return new com.exchange.match.engine.dto.OrderBookEntry(
                                price, BigDecimal.ZERO, null, null);
                    } else {
                        // 否则返回总量
                        return new com.exchange.match.engine.dto.OrderBookEntry(
                                price, bucket.getTotalVolume(), null, null);
                    }
                })
                .collect(Collectors.toList());
        
        // 转换卖单
        List<com.exchange.match.engine.dto.OrderBookEntry> askUpdates = modifiedAsks.stream()
                .map(price -> {
                    PriceBucket bucket = asks.get(price);
                    if (bucket == null || bucket.isEmpty()) {
                        // 如果价格桶不存在或为空，则返回数量为0的条目，表示删除该价格等级
                        return new com.exchange.match.engine.dto.OrderBookEntry(
                                price, BigDecimal.ZERO, null, null);
                    } else {
                        // 否则返回总量
                        return new com.exchange.match.engine.dto.OrderBookEntry(
                                price, bucket.getTotalVolume(), null, null);
                    }
                })
                .collect(Collectors.toList());
        
        // 创建更新对象
        return OrderBookUpdate.builder()
                .symbol(symbol)
                .bids(bidUpdates)
                .asks(askUpdates)
                .lastOffset(lastProcessedOffset)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 获取订单簿深度
     *
     * @param depth 深度
     * @return 订单簿深度数据
     */
    public Map<String, List<List<BigDecimal>>> getDepth(int depth) {
        Map<String, List<List<BigDecimal>>> result = new HashMap<>();
        
        List<List<BigDecimal>> bidsList = new ArrayList<>();
        List<List<BigDecimal>> asksList = new ArrayList<>();
        
        // 获取买盘深度
        int bidCount = 0;
        for (Map.Entry<BigDecimal, PriceBucket> entry : bids.entrySet()) {
            if (bidCount >= depth) break;
            if (!entry.getValue().isEmpty()) {
                List<BigDecimal> item = new ArrayList<>();
                item.add(entry.getKey()); // 价格
                item.add(entry.getValue().getTotalVolume()); // 数量
                bidsList.add(item);
                bidCount++;
            }
        }
        
        // 获取卖盘深度
        int askCount = 0;
        for (Map.Entry<BigDecimal, PriceBucket> entry : asks.entrySet()) {
            if (askCount >= depth) break;
            if (!entry.getValue().isEmpty()) {
                List<BigDecimal> item = new ArrayList<>();
                item.add(entry.getKey()); // 价格
                item.add(entry.getValue().getTotalVolume()); // 数量
                asksList.add(item);
                askCount++;
            }
        }
        
        result.put("bids", bidsList);
        result.put("asks", asksList);
        
        return result;
    }
    
    /**
     * 清空订单簿
     */
    public void clear() {
        bids.clear();
        asks.clear();
        orderMap.clear();
    }
} 