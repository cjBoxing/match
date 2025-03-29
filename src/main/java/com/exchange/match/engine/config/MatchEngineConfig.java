package com.exchange.match.engine.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 撮合引擎配置类
 */
@Data
@Configuration
public class MatchEngineConfig {

    @Value("${match.topic.orders}")
    private String ordersTopic;

    @Value("${match.topic.trades}")
    private String tradesTopic;

    @Value("${match.topic.order-book}")
    private String orderBookTopic;

    @Value("${match.topic.user-tasks}")
    private String userTasksTopic;

    @Value("${match.backup.save-interval}")
    private Long backupSaveInterval;

    @Value("${match.node.id}")
    private Integer nodeId;
    
    // 计算用户分区
    public Integer calculateUserPartition(Long userId) {
        return (int) ((userId / 19) % 100);
    }
}