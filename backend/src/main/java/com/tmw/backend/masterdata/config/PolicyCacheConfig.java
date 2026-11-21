package com.tmw.backend.masterdata.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache Caffeine cho danh sách chính sách dung sai đang hiệu lực.
 * Bọc TransactionAwareCacheManagerProxy để việc xóa cache (evict) chỉ xảy ra SAU KHI transaction commit,
 * tránh đọc lại dữ liệu cũ trong khoảng giữa evict và commit.
 */
@Configuration
@EnableCaching
public class PolicyCacheConfig {

    public static final String TOLERANCE_POLICIES = "tolerancePolicies";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(TOLERANCE_POLICIES);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(10)));
        return new TransactionAwareCacheManagerProxy(manager);
    }
}
