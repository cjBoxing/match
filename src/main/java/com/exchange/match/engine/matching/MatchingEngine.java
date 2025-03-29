package com.exchange.match.engine.matching;

import com.exchange.match.engine.dto.OrderCommand;
import com.exchange.match.engine.dto.PublicTradeRecord;
import com.exchange.match.engine.dto.TradeResult;
import com.exchange.match.engine.model.Order;
import com.exchange.match.engine.model.Symbol;
import com.exchange.match.engine.orderbook.OrderBook;
import com.exchange.match.engine.orderbook.OrderBookEntry;
import com.exchange.match.engine.orderbook.PriceBucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 撮合引擎，实现核心撮合逻辑
 */
@Slf4j
@RequiredArgsConstructor
public class MatchingEngine {
    private final OrderBook orderBook;
    private final Symbol symbolInfo;
    private final AtomicLong tradeIdGenerator;
    
    // 修改过的买单价格集合
    private final Set<BigDecimal> modifiedBidPrices = new HashSet<>();
    // 修改过的卖单价格集合
    private final Set<BigDecimal> modifiedAskPrices = new HashSet<>();
    
    /**
     * 处理新订单
     *
     * @param order 订单
     * @param offset Kafka偏移量
     * @return 交易执行结果
     */
    public TradeExecutionResult processNewOrder(Order order, long offset) {
        // 清空修改过的价格集合
        modifiedBidPrices.clear();
        modifiedAskPrices.clear();
        
        TradeExecutionResult.Builder resultBuilder = TradeExecutionResult.builder()
                .offset(offset);
        
        // 如果是市价单
        if (order.getType() == 5) {
            processMarketOrder(order, resultBuilder);
        } 
        // 如果是限价单
        else {
            processLimitOrder(order, resultBuilder);
        }
        
        // 更新订单簿的最后处理偏移量
        orderBook.updateLastProcessedOffset(offset);
        
        // 生成订单簿更新
        resultBuilder.orderBookUpdate(orderBook.generateUpdate(modifiedBidPrices, modifiedAskPrices));
        
        return resultBuilder.build();
    }
    
    /**
     * 处理限价单
     *
     * @param order 限价单
     * @param resultBuilder 结果构建器
     */
    private void processLimitOrder(Order order, TradeExecutionResult.Builder resultBuilder) {
        BigDecimal remainingQty = order.getQuantity();
        
        // 买单
        if (order.getSide() == 1) {
            // 尝试匹配卖单
            remainingQty = matchWithOrderBook(order, remainingQty, false, resultBuilder);
            
            // 如果还有剩余数量且不是IOC/FOK订单
            if (remainingQty.compareTo(BigDecimal.ZERO) > 0 && order.getType() != 3 && order.getType() != 4) {
                // 更新订单的剩余数量并添加到订单簿
                order.setQuantityLeft(remainingQty);
                orderBook.addOrder(order);
                
                // 记录修改的价格等级
                modifiedBidPrices.add(order.getPrice());
            }
        }
        // 卖单
        else {
            // 尝试匹配买单
            remainingQty = matchWithOrderBook(order, remainingQty, true, resultBuilder);
            
            // 如果还有剩余数量且不是IOC/FOK订单
            if (remainingQty.compareTo(BigDecimal.ZERO) > 0 && order.getType() != 3 && order.getType() != 4) {
                // 更新订单的剩余数量并添加到订单簿
                order.setQuantityLeft(remainingQty);
                orderBook.addOrder(order);
                
                // 记录修改的价格等级
                modifiedAskPrices.add(order.getPrice());
            }
        }
    }
    
    /**
     * 处理市价单
     *
     * @param order 市价单
     * @param resultBuilder 结果构建器
     */
    private void processMarketOrder(Order order, TradeExecutionResult.Builder resultBuilder) {
        BigDecimal remainingQty = order.getQuantity();
        
        // 买单
        if (order.getSide() == 1) {
            // 尝试匹配卖单
            remainingQty = matchWithOrderBook(order, remainingQty, false, resultBuilder);
        }
        // 卖单
        else {
            // 尝试匹配买单
            remainingQty = matchWithOrderBook(order, remainingQty, true, resultBuilder);
        }
        
        // 市价单不会添加到订单簿
    }
    
    /**
     * 与订单簿中的订单进行匹配
     *
     * @param takerOrder    taker订单
     * @param remainingQty  剩余数量
     * @param isBuyMatching 是否是买单匹配（卖单是taker）
     * @param resultBuilder 结果构建器
     * @return 剩余未成交数量
     */
    private BigDecimal matchWithOrderBook(Order takerOrder, BigDecimal remainingQty, boolean isBuyMatching,
                                       TradeExecutionResult.Builder resultBuilder) {
        // 获取对应的订单簿（买或卖）
        NavigableMap<BigDecimal, PriceBucket> bookSide = isBuyMatching ? orderBook.getBids() : orderBook.getAsks();
        
        // 匹配价格上限或下限
        BigDecimal limitPrice = takerOrder.getPrice();
        
        // 持续匹配，直到没有可匹配的订单或剩余数量为0
        while (remainingQty.compareTo(BigDecimal.ZERO) > 0 && !bookSide.isEmpty()) {
            // 获取最优价格
            BigDecimal bestPrice = bookSide.firstKey();
            
            // 检查价格是否满足条件（买单要价格大于等于卖单价格，卖单要价格小于等于买单价格）
            if ((isBuyMatching && bestPrice.compareTo(limitPrice) < 0)
                    || (!isBuyMatching && bestPrice.compareTo(limitPrice) > 0)) {
                break;
            }
            
            // 获取最优价格的价格桶
            PriceBucket bucket = bookSide.get(bestPrice);
            if (bucket == null || bucket.isEmpty()) {
                bookSide.remove(bestPrice);
                continue;
            }
            
            // 获取第一个订单
            OrderBookEntry makerEntry = bucket.getFirstOrder();
            if (makerEntry == null) {
                bookSide.remove(bestPrice);
                continue;
            }
            
            // 计算成交数量（取两者的较小值）
            BigDecimal tradeQty = remainingQty.min(makerEntry.getQuantity());
            
            // 成交价格
            BigDecimal tradePrice = makerEntry.getPrice();
            
            // 生成成交ID
            Long tradeId = tradeIdGenerator.incrementAndGet();
            
            // 创建taker成交结果
            TradeResult takerTradeResult = createTradeResult(takerOrder, tradeId, tradePrice, tradeQty, false);
            
            // 创建maker成交结果
            TradeResult makerTradeResult = createTradeResult(makerEntry, tradeId, tradePrice, tradeQty, true);
            
            // 创建公共成交记录
            PublicTradeRecord publicTrade = PublicTradeRecord.builder()
                    .tradeId(tradeId)
                    .symbol(takerOrder.getSymbol())
                    .price(tradePrice)
                    .quantity(tradeQty)
                    .direction(takerOrder.getSide())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            // 设置taker成交结果
            resultBuilder.takerTradeResult(takerTradeResult);
            
            // 添加maker成交结果
            resultBuilder.addMakerTradeResult(makerTradeResult);
            
            // 添加公共成交记录
            resultBuilder.addPublicTrade(publicTrade);
            
            // 更新剩余数量
            remainingQty = remainingQty.subtract(tradeQty);
            
            // 更新maker订单的数量
            boolean shouldRemoveMaker = bucket.executeTrade(tradeQty);
            
            // 如果maker订单已完全成交，则从订单簿中移除
            if (shouldRemoveMaker) {
                bookSide.remove(bestPrice);
            }
            
            // 记录修改的价格等级
            if (isBuyMatching) {
                modifiedBidPrices.add(bestPrice);
            } else {
                modifiedAskPrices.add(bestPrice);
            }
            
            // 如果是FOK订单且不能完全成交，则返回原数量
            if (takerOrder.getType() == 3 && remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                return takerOrder.getQuantity();
            }
        }
        
        return remainingQty;
    }
    
    /**
     * 创建成交结果
     *
     * @param order     订单（可能是Order或OrderBookEntry）
     * @param tradeId   成交ID
     * @param price     成交价格
     * @param quantity  成交数量
     * @param isMaker   是否是挂单方
     * @return 成交结果
     */
    private TradeResult createTradeResult(Object order, Long tradeId, BigDecimal price, BigDecimal quantity, boolean isMaker) {
        Long orderId;
        Long userId;
        String symbol;
        Integer side;
        Integer action;
        Integer marginType;
        Integer marginMode;
        BigDecimal fee;
        
        // 如果是Order对象
        if (order instanceof Order) {
            Order o = (Order) order;
            orderId = o.getId();
            userId = o.getUserId();
            symbol = o.getSymbol();
            side = o.getSide();
            action = o.getAction();
            marginType = o.getMarginType();
            marginMode = o.getMarginMode();
            
            // 计算手续费
            if (side == 1) { // 买
                fee = isMaker ? 
                        price.multiply(quantity).multiply(symbolInfo.getBuyMakerFee()) : 
                        price.multiply(quantity).multiply(symbolInfo.getBuyTakerFee());
            } else { // 卖
                fee = isMaker ? 
                        price.multiply(quantity).multiply(symbolInfo.getSellMakerFee()) : 
                        price.multiply(quantity).multiply(symbolInfo.getSellTakerFee());
            }
        }
        // 如果是OrderBookEntry对象
        else {
            OrderBookEntry e = (OrderBookEntry) order;
            orderId = e.getOrderId();
            userId = e.getUserId();
            symbol = symbolInfo.getSymbol();
            side = e.getSide();
            action = 0; // 假设为现货
            marginType = 0; // 假设为现货
            marginMode = 0; // 假设为普通现货
            
            // 计算手续费
            if (side == 1) { // 买
                fee = isMaker ? 
                        price.multiply(quantity).multiply(symbolInfo.getBuyMakerFee()) : 
                        price.multiply(quantity).multiply(symbolInfo.getBuyTakerFee());
            } else { // 卖
                fee = isMaker ? 
                        price.multiply(quantity).multiply(symbolInfo.getSellMakerFee()) : 
                        price.multiply(quantity).multiply(symbolInfo.getSellTakerFee());
            }
        }
        
        // 四舍五入手续费到8位小数
        fee = fee.setScale(8, RoundingMode.HALF_UP);
        
        // 创建成交结果
        return TradeResult.builder()
                .tradeId(tradeId)
                .orderId(orderId)
                .userId(userId)
                .symbol(symbol)
                .side(side)
                .price(price)
                .quantity(quantity)
                .fee(fee)
                .feeCoin(symbolInfo.getMarginCoin())
                .pnl(BigDecimal.ZERO) // 盈亏需要另外计算
                .action(action)
                .marginType(marginType)
                .marginMode(marginMode)
                .isMaker(isMaker)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 处理撤单请求
     *
     * @param command 撤单命令
     * @param offset Kafka偏移量
     * @return 交易执行结果
     */
    public TradeExecutionResult processCancelOrder(OrderCommand command, long offset) {
        // 清空修改过的价格集合
        modifiedBidPrices.clear();
        modifiedAskPrices.clear();
        
        // 从订单簿中取消订单
        OrderBookEntry cancelledEntry = orderBook.cancelOrder(command.getOrderId());
        
        // 如果订单不存在，返回空结果
        if (cancelledEntry == null) {
            return TradeExecutionResult.builder()
                    .offset(offset)
                    .orderBookUpdate(orderBook.generateUpdate(modifiedBidPrices, modifiedAskPrices))
                    .build();
        }
        
        // 记录修改的价格等级
        if (cancelledEntry.getSide() == 1) { // 买单
            modifiedBidPrices.add(cancelledEntry.getPrice());
        } else { // 卖单
            modifiedAskPrices.add(cancelledEntry.getPrice());
        }
        
        // 更新订单簿的最后处理偏移量
        orderBook.updateLastProcessedOffset(offset);
        
        // 构建结果
        return TradeExecutionResult.builder()
                .offset(offset)
                .orderBookUpdate(orderBook.generateUpdate(modifiedBidPrices, modifiedAskPrices))
                .build();
    }
} 