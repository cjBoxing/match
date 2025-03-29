package com.exchange.match.engine.matching;

import com.exchange.match.engine.dto.OrderBookUpdate;
import com.exchange.match.engine.dto.PublicTradeRecord;
import com.exchange.match.engine.dto.TradeResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易执行结果，用于封装一次撮合的所有结果
 */
@Getter
public class TradeExecutionResult {
    // taker的成交结果
    private final TradeResult takerTradeResult;
    
    // maker的成交结果列表
    private final List<TradeResult> makerTradeResults;
    
    // 公共成交记录
    private final List<PublicTradeRecord> publicTrades;
    
    // 订单簿更新
    private final OrderBookUpdate orderBookUpdate;
    
    // Kafka偏移量
    private final Long offset;
    
    private TradeExecutionResult(Builder builder) {
        this.takerTradeResult = builder.takerTradeResult;
        this.makerTradeResults = builder.makerTradeResults;
        this.publicTrades = builder.publicTrades;
        this.orderBookUpdate = builder.orderBookUpdate;
        this.offset = builder.offset;
    }
    
    /**
     * 创建构建器
     *
     * @return 构建器对象
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 交易执行结果构建器
     */
    public static class Builder {
        private TradeResult takerTradeResult;
        private List<TradeResult> makerTradeResults = new ArrayList<>();
        private List<PublicTradeRecord> publicTrades = new ArrayList<>();
        private OrderBookUpdate orderBookUpdate;
        private Long offset;
        
        private Builder() {
        }
        
        /**
         * 设置taker的成交结果
         *
         * @param takerTradeResult taker的成交结果
         * @return Builder对象
         */
        public Builder takerTradeResult(TradeResult takerTradeResult) {
            this.takerTradeResult = takerTradeResult;
            return this;
        }
        
        /**
         * 添加maker的成交结果
         *
         * @param makerTradeResult maker的成交结果
         * @return Builder对象
         */
        public Builder addMakerTradeResult(TradeResult makerTradeResult) {
            this.makerTradeResults.add(makerTradeResult);
            return this;
        }
        
        /**
         * 添加公共成交记录
         *
         * @param publicTrade 公共成交记录
         * @return Builder对象
         */
        public Builder addPublicTrade(PublicTradeRecord publicTrade) {
            this.publicTrades.add(publicTrade);
            return this;
        }
        
        /**
         * 设置订单簿更新
         *
         * @param orderBookUpdate 订单簿更新
         * @return Builder对象
         */
        public Builder orderBookUpdate(OrderBookUpdate orderBookUpdate) {
            this.orderBookUpdate = orderBookUpdate;
            return this;
        }
        
        /**
         * 设置Kafka偏移量
         *
         * @param offset Kafka偏移量
         * @return Builder对象
         */
        public Builder offset(Long offset) {
            this.offset = offset;
            return this;
        }
        
        /**
         * 构建交易执行结果对象
         *
         * @return 交易执行结果对象
         */
        public TradeExecutionResult build() {
            return new TradeExecutionResult(this);
        }
    }
} 