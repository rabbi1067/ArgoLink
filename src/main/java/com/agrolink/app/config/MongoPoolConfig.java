package com.agrolink.app.config;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoPoolConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoPoolCustomizer() {
        return builder -> builder.applyToConnectionPoolSettings(pool -> {
            pool.maxSize(15);
            pool.minSize(2);
        });
    }
}
