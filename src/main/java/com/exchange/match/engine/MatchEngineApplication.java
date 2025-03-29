package com.exchange.match.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableKafka
@EnableDiscoveryClient
@EnableScheduling
public class MatchEngineApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MatchEngineApplication.class, args);
    }
} 