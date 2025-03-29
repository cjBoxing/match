package com.exchange.match.engine.matching;

import com.exchange.match.engine.dto.OrderBookUpdate;
import com.exchange.match.engine.dto.PublicTradeRecord;
import com.exchange.match.engine.dto.TradeResult;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易执行结果，用于封装一次撮合的所有结果
 */
@Data
@Builder
public class TradeExecutionResult {
    // taker的成交结果
    private TradeResult takerTradeResult;
    
    // maker的成交结果列表
    @Builder.Default
    private List<TradeResult> makerTradeResults = new ArrayList<>();
    
    // 公共成交记录
    @Builder.Default
    private List<PublicTradeRecord> publicTrades = new ArrayList<>();
    
    // 订单簿更新
    private OrderBookUpdate orderBookUpdate;
    
    // Kafka偏移量
    private Long offset;
    
    /**
     * 添加maker的成交结果
     *
     * @param makerTradeResult maker的成交结果
     */
    public void addMakerTradeResult(TradeResult makerTradeResult) {
        makerTradeResults.add(makerTradeResult);
    }
    
    /**
     * 添加公共成交记录
     *
     * @param publicTrade 公共成交记录
     */
    public void addPublicTrade(PublicTradeRecord publicTrade) {
        publicTrades.add(publicTrade);
    }
} 