package com.exchange.match.engine.service;

import com.exchange.match.engine.config.MatchEngineConfig;
import com.exchange.match.engine.orderbook.OrderBook;
import com.exchange.match.engine.util.ProtostuffUtils;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单簿持久化服务，负责备份和恢复订单簿数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBookPersistenceService {
    
    private final MatchEngineConfig config;
    private final GridFsTemplate gridFsTemplate;
    private final MongoDatabaseFactory mongoDatabaseFactory;
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    
    /**
     * 注册订单簿
     *
     * @param symbol    交易对
     * @param orderBook 订单簿
     */
    public void registerOrderBook(String symbol, OrderBook orderBook) {
        orderBooks.put(symbol, orderBook);
    }
    
    /**
     * 从MongoDB恢复订单簿数据
     *
     * @param symbol 交易对
     * @return 是否成功
     */
    public boolean restoreOrderBook(String symbol) {
        try {
            GridFSFile file = gridFsTemplate.findOne(
                    Query.query(
                            Criteria.where("metadata.symbol").is(symbol)
                                    .and("metadata.nodeId").is(config.getNodeId())
                    ).with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "uploadDate")).limit(1)
            );
            
            if (file == null) {
                log.warn("没有找到交易对{}的订单簿备份", symbol);
                return false;
            }
            
            GridFSBucket gridFSBucket = GridFSBuckets.create(mongoDatabaseFactory.getMongoDatabase());
            GridFsResource resource = new GridFsResource(file, gridFSBucket.openDownloadStream(file.getObjectId()));
            
            try (InputStream inputStream = resource.getInputStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
                
                OrderBook orderBook = ProtostuffUtils.deserialize(outputStream.toByteArray(), OrderBook.class);
                orderBooks.put(symbol, orderBook);
                
                log.info("恢复交易对{}的订单簿数据成功，最后处理偏移量: {}", symbol, orderBook.getLastProcessedOffset());
                return true;
            }
        } catch (Exception e) {
            log.error("恢复交易对{}的订单簿数据失败", symbol, e);
            return false;
        }
    }
    
    /**
     * 定时备份订单簿数据
     */
    @Scheduled(fixedDelayString = "${match.backup.save-interval}")
    public void backupOrderBooks() {
        if (orderBooks.isEmpty()) {
            return;
        }
        
        for (Map.Entry<String, OrderBook> entry : orderBooks.entrySet()) {
            String symbol = entry.getKey();
            OrderBook orderBook = entry.getValue();
            
            try {
                byte[] orderBookBytes = ProtostuffUtils.serialize(orderBook);
                
                Document metadata = new Document()
                        .append("symbol", symbol)
                        .append("nodeId", config.getNodeId())
                        .append("lastOffset", orderBook.getLastProcessedOffset())
                        .append("timestamp", System.currentTimeMillis());
                
                try (InputStream inputStream = new ByteArrayInputStream(orderBookBytes)) {
                    String filename = symbol + "_" + orderBook.getLastProcessedOffset() + "_" + System.currentTimeMillis();
                    gridFsTemplate.store(inputStream, filename, metadata);
                    log.info("备份交易对{}的订单簿数据成功，文件名: {}", symbol, filename);
                }
            } catch (IOException e) {
                log.error("备份交易对{}的订单簿数据失败", symbol, e);
            }
        }
    }
} 