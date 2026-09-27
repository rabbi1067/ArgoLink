package com.agrolink.app.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_PUBLIC_LISTINGS = "publicListings";
    public static final String CACHE_CATEGORIES = "categories";
    public static final String CACHE_DASHBOARD_STATS = "dashboardStats";
    public static final String CACHE_WEATHER_DATA = "weatherData";
    public static final String CACHE_WEATHER_FORECASTS = CACHE_WEATHER_DATA;
    public static final String CACHE_CONVERSATIONS = "conversations";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                caffeineCache(CACHE_PUBLIC_LISTINGS, 2_000, Duration.ofMinutes(10)),
                caffeineCache(CACHE_CATEGORIES, 500, Duration.ofHours(1)),
                caffeineCache(CACHE_DASHBOARD_STATS, 250, Duration.ofMinutes(5)),
                caffeineCache(CACHE_WEATHER_DATA, 200, Duration.ofHours(1)),
                caffeineCache(CACHE_CONVERSATIONS, 5_000, Duration.ofMinutes(2))
        ));
        return cacheManager;
    }

    private CaffeineCache caffeineCache(String name, long maximumSize, Duration expireAfterWrite) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite)
                .recordStats()
                .build());
    }
}