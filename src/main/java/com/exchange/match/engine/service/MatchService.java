package com.exchange.match.engine.service;

import com.exchange.match.engine.config.MatchEngineConfig;
import com.exchange.match.engine.dto.OrderCommand;
import com.exchange.match.engine.matching.MatchingEngine;
import com.exchange.match.engine.matching.TradeExecutionResult;
import com.exchange.match.engine.model.Order;
import com.exchange.match.engine.model.Symbol;
import com.exchange.match.engine.orderbook.OrderBook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 撮合服务，负责撮合交易
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService implements ApplicationRunner {
    
    private final MatchEngineConfig config;
    private final SymbolService symbolService;
    private final KafkaConsumerService kafkaConsumerService;
    private final KafkaProducerService kafkaProducerService;
    private final OrderBookPersistenceService orderBookPersistenceService;
    
    private final Map<String, MatchingEngine> matchingEngines = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<ConsumerRecord<String, byte[]>>> messageQueues = new ConcurrentHashMap<>();
    private final Map<String, Thread> matchThreads = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> tradeIdGenerators = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private volatile boolean running = true;
    
    /**
     * 应用启动时运行
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("启动撮合服务，节点ID: {}", config.getNodeId());
        
        try {
            // 获取该节点负责的交易对列表
            List<Symbol> symbols = symbolService.getSymbolsByNodeId(config.getNodeId());
            
            if (symbols.isEmpty()) {
                log.warn("节点{}没有分配到交易对", config.getNodeId());
                return;
            }
            
            // 为每个交易对启动撮合引擎
            for (Symbol symbol : symbols) {
                startMatchingEngine(symbol);
            }
            
            log.info("撮合服务启动完成，共加载{}个交易对", symbols.size());
        } catch (Exception e) {
            log.error("启动撮合服务失败", e);
        }
    }
    
    /**
     * 启动交易对的撮合引擎
     *
     * @param symbol 交易对信息
     */
    private void startMatchingEngine(Symbol symbol) {
        String symbolName = symbol.getSymbol();
        int partition = symbol.getPartition();
        
        log.info("启动交易对{}的撮合引擎，分区: {}", symbolName, partition);
        
        // 创建消息队列
        BlockingQueue<ConsumerRecord<String, byte[]>> messageQueue = new LinkedBlockingQueue<>();
        messageQueues.put(symbolName, messageQueue);
        
        // 创建订单簿
        OrderBook orderBook = new OrderBook(symbolName);
        
        // 尝试从MongoDB恢复订单簿数据
        boolean restored = orderBookPersistenceService.restoreOrderBook(symbolName);
        if (!restored) {
            log.info("未找到交易对{}的订单簿备份，使用新的订单簿", symbolName);
        }
        
        // 注册订单簿到持久化服务
        orderBookPersistenceService.registerOrderBook(symbolName, orderBook);
        
        // 创建交易ID生成器
        AtomicLong tradeIdGenerator = new AtomicLong(0);
        tradeIdGenerators.put(symbolName, tradeIdGenerator);
        
        // 创建撮合引擎
        MatchingEngine matchingEngine = new MatchingEngine(orderBook, symbol, tradeIdGenerator);
        matchingEngines.put(symbolName, matchingEngine);
        
        // 启动Kafka消费者
        kafkaConsumerService.startConsumerThread(symbolName, partition, messageQueue);
        
        // 启动撮合线程
        Thread matchThread = new Thread(() -> matchingThread(symbolName));
        matchThread.setName("match-thread-" + symbolName);
        matchThread.setDaemon(true);
        matchThread.start();
        matchThreads.put(symbolName, matchThread);
        
        log.info("交易对{}的撮合引擎启动完成", symbolName);
    }
    
    /**
     * 撮合线程
     *
     * @param symbol 交易对
     */
    private void matchingThread(String symbol) {
        MatchingEngine matchingEngine = matchingEngines.get(symbol);
        BlockingQueue<ConsumerRecord<String, byte[]>> messageQueue = messageQueues.get(symbol);
        
        log.info("启动交易对{}的撮合线程", symbol);
        
        try {
            while (running) {
                try {
                    ConsumerRecord<String, byte[]> record = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (record != null) {
                        // 解析订单命令
                        OrderCommand command = kafkaConsumerService.parseOrderCommand(record);
                        if (command != null) {
                            // 处理订单命令
                            TradeExecutionResult result = processOrderCommand(matchingEngine, command, record.offset());
                            
                            // 发送撮合结果
                            if (result != null) {
                                kafkaProducerService.sendTradeExecutionResult(result);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    log.error("交易对{}的撮合线程被中断", symbol, e);
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.error("处理交易对{}的订单异常", symbol, e);
                }
            }
        } finally {
            log.info("交易对{}的撮合线程结束", symbol);
        }
    }
    
    /**
     * 处理订单命令
     *
     * @param matchingEngine 撮合引擎
     * @param command        订单命令
     * @param offset         Kafka偏移量
     * @return 撮合结果
     */
    private TradeExecutionResult processOrderCommand(MatchingEngine matchingEngine, OrderCommand command, long offset) {
        if (OrderCommand.TYPE_NEW_ORDER.equals(command.getType())) {
            // 将订单命令转换为订单对象
            Order order = kafkaConsumerService.convertToOrder(command);
            // 处理新订单
            return matchingEngine.processNewOrder(order, offset);
        } else if (OrderCommand.TYPE_CANCEL_ORDER.equals(command.getType())) {
            // 处理撤单
            return matchingEngine.processCancelOrder(command, offset);
        } else {
            log.warn("未知的订单命令类型: {}", command.getType());
            return null;
        }
    }
    
    /**
     * 应用关闭时调用
     */
    @PreDestroy
    public void shutdown() {
        log.info("关闭撮合服务");
        
        running = false;
        
        // 关闭撮合线程
        for (Map.Entry<String, Thread> entry : matchThreads.entrySet()) {
            String symbol = entry.getKey();
            Thread thread = entry.getValue();
            
            try {
                log.info("关闭交易对{}的撮合线程", symbol);
                thread.interrupt();
                thread.join(5000);
            } catch (InterruptedException e) {
                log.error("等待撮合线程结束被中断", e);
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭Kafka消费者
        for (String symbol : matchingEngines.keySet()) {
            Symbol symbolInfo = symbolService.getSymbol(symbol);
            if (symbolInfo != null) {
                kafkaConsumerService.closeConsumer(symbol, symbolInfo.getPartition());
            }
        }
        
        // 关闭线程池
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("撮合服务关闭完成");
        shutdownLatch.countDown();
    }
    
    /**
     * 等待服务关闭
     */
    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }
} 