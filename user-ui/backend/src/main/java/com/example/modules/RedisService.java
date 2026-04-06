package com.example.modules;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisService {
    private static final long DEFAULT_TTL = 24; // 24小时
    private static RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        RedisService.redisTemplate = redisTemplate;
    }

    public static void put(String key, String value) {
        put(key, value, DEFAULT_TTL);
    }

    public static void put(String key, String value, long ttlHours) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(key, value, ttlHours, TimeUnit.HOURS);
        }
    }

    public static String get(String key) {
        if (redisTemplate != null) {
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        }
        return null;
    }

    public static void remove(String key) {
        if (redisTemplate != null) {
            redisTemplate.delete(key);
        }
    }

    public static void clear() {
        if (redisTemplate != null) {
            // 注意：此操作会删除所有键，谨慎使用
            redisTemplate.getConnectionFactory().getConnection().flushDb();
        }
    }

    public static long size() {
        if (redisTemplate != null) {
            return redisTemplate.getConnectionFactory().getConnection().dbSize();
        }
        return 0;
    }

    // Redis 会自动处理过期，无需手动清理
}