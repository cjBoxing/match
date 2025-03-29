package com.exchange.match.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

/**
 * MongoDB配置类
 */
@Configuration
public class MongoConfig {
    
    /**
     * 配置GridFsTemplate
     */
    @Bean
    public GridFsTemplate gridFsTemplate(MongoDatabaseFactory mongoDatabaseFactory, MongoTemplate mongoTemplate) {
        return new GridFsTemplate(
                mongoDatabaseFactory,
                mongoTemplate.getConverter(),
                "orderbooks"); // 指定bucket名称
    }
    
    /**
     * 自定义MongoTemplate，去掉文档中的_class字段
     */
    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory, MongoMappingContext context) {
        MappingMongoConverter converter = new MappingMongoConverter(
                new DefaultDbRefResolver(mongoDatabaseFactory),
                context
        );
        converter.setTypeMapper(new DefaultMongoTypeMapper(null)); // 去掉_class字段
        return new MongoTemplate(mongoDatabaseFactory, converter);
    }
} 