package com.exchange.match.engine.service;

import com.exchange.match.engine.model.Symbol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易对服务，负责管理交易对信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolService {
    
    private final StringRedisTemplate redisTemplate;
    private final Map<String, Symbol> symbolCache = new HashMap<>();
    
    /**
     * 获取交易对信息
     *
     * @param symbol 交易对
     * @return 交易对信息
     */
    public Symbol getSymbol(String symbol) {
        return symbolCache.get(symbol);
    }
    
    /**
     * 根据分区获取交易对列表
     *
     * @param nodeId 节点ID
     * @return 交易对列表
     */
    public List<Symbol> getSymbolsByNodeId(int nodeId) {
        List<Symbol> symbols = new ArrayList<>();
        
        // 从Redis中获取该节点负责的分区列表
        String key = "match:node:" + nodeId + ":partitions";
        List<String> partitionStrings = redisTemplate.opsForList().range(key, 0, -1);
        
        if (partitionStrings == null || partitionStrings.isEmpty()) {
            log.warn("节点{}没有分配到分区", nodeId);
            return symbols;
        }
        
        // 根据分区ID获取交易对列表
        for (String partitionString : partitionStrings) {
            int partition = Integer.parseInt(partitionString);
            String symbolsKey = "match:partition:" + partition + ":symbols";
            List<String> symbolNames = redisTemplate.opsForList().range(symbolsKey, 0, -1);
            
            if (symbolNames != null && !symbolNames.isEmpty()) {
                for (String symbolName : symbolNames) {
                    Symbol symbol = getSymbolBySymbolName(symbolName);
                    if (symbol != null) {
                        symbols.add(symbol);
                        symbolCache.put(symbolName, symbol);
                    }
                }
            }
        }
        
        return symbols;
    }
    
    /**
     * 根据交易对名称获取交易对信息
     *
     * @param symbolName 交易对名称
     * @return 交易对信息
     */
    private Symbol getSymbolBySymbolName(String symbolName) {
        String key = "match:symbol:" + symbolName;
        Map<Object, Object> symbolMap = redisTemplate.opsForHash().entries(key);
        
        if (symbolMap == null || symbolMap.isEmpty()) {
            log.warn("交易对{}不存在", symbolName);
            return null;
        }
        
        Symbol symbol = new Symbol();
        symbol.setId(Integer.valueOf(symbolMap.get("id").toString()));
        symbol.setSymbol(symbolName);
        symbol.setType(Integer.valueOf(symbolMap.get("type").toString()));
        symbol.setBaseCoin(symbolMap.get("base_coin").toString());
        symbol.setQuoteCoin(symbolMap.get("quote_coin").toString());
        symbol.setMarginCoin(symbolMap.get("margin_coin").toString());
        symbol.setPartition(Integer.valueOf(symbolMap.get("partition").toString()));
        
        // 其他字段也可以根据需要从Redis中获取
        
        return symbol;
    }
} 