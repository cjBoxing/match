package com.exchange.match.engine.config;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.exchange.match.engine.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos配置类，用于配置主备节点
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class NacosConfig {
    
    private final DiscoveryClient discoveryClient;
    private final NacosDiscoveryProperties nacosDiscoveryProperties;
    private final MatchEngineConfig matchEngineConfig;
    private final MatchService matchService;
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    private final AtomicBoolean isPrimary = new AtomicBoolean(false);
    
    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        // 添加节点类型元数据
        Map<String, String> metadata = new HashMap<>(nacosDiscoveryProperties.getMetadata());
        metadata.put("nodeId", String.valueOf(matchEngineConfig.getNodeId()));
        metadata.put("isPrimary", "false");
        nacosDiscoveryProperties.setMetadata(metadata);
        
        log.info("初始化Nacos配置，节点ID: {}", matchEngineConfig.getNodeId());
    }
    
    /**
     * 定时检测主备节点状态
     */
    @Scheduled(fixedDelayString = "10000") // 10秒检测一次
    public void checkPrimaryNode() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(applicationName);
            
            if (instances.isEmpty()) {
                log.warn("没有发现服务实例");
                return;
            }
            
            // 按节点ID排序
            instances.sort(Comparator.comparing(instance -> 
                    Integer.parseInt(instance.getMetadata().getOrDefault("nodeId", "0"))));
            
            // 获取当前节点ID
            int currentNodeId = matchEngineConfig.getNodeId();
            
            // 检查是否有主节点
            boolean hasPrimary = instances.stream()
                    .anyMatch(instance -> "true".equals(instance.getMetadata().get("isPrimary")));
            
            // 如果没有主节点，则ID最小的节点成为主节点
            if (!hasPrimary && instances.get(0).getMetadata().get("nodeId").equals(String.valueOf(currentNodeId))) {
                // 当前节点成为主节点
                updatePrimaryStatus(true);
                log.info("节点{}成为主节点", currentNodeId);
            }
            
            // 输出所有节点状态
            if (log.isDebugEnabled()) {
                for (ServiceInstance instance : instances) {
                    log.debug("节点ID: {}, 主节点: {}", 
                            instance.getMetadata().get("nodeId"),
                            instance.getMetadata().get("isPrimary"));
                }
            }
        } catch (Exception e) {
            log.error("检测主备节点状态异常", e);
        }
    }
    
    /**
     * 更新节点主备状态
     *
     * @param primary 是否为主节点
     */
    private void updatePrimaryStatus(boolean primary) {
        if (isPrimary.compareAndSet(!primary, primary)) {
            Map<String, String> metadata = new HashMap<>(nacosDiscoveryProperties.getMetadata());
            metadata.put("isPrimary", String.valueOf(primary));
            nacosDiscoveryProperties.setMetadata(metadata);
            
            log.info("更新节点状态为: {}", primary ? "主节点" : "备节点");
            
            // 如果从备节点变为主节点，则启动撮合服务
            if (primary) {
                // TODO: 启动撮合服务的逻辑
            }
            // 如果从主节点变为备节点，则停止撮合服务
            else {
                // TODO: 停止撮合服务的逻辑
            }
        }
    }
    
    /**
     * 获取当前节点是否为主节点
     *
     * @return 是否为主节点
     */
    public boolean isPrimaryNode() {
        return isPrimary.get();
    }
} 