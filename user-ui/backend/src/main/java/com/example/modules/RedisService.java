package com.example.modules;

import java.util.HashMap;
import java.util.Map;

public class RedisService {
    private static final Map<String, CacheEntry> cache = new HashMap<>();
    private static final long DEFAULT_TTL = 24 * 60 * 60 * 1000; // 24小时

    static class CacheEntry {
        String value;
        long timestamp;
        long ttl;

        CacheEntry(String value, long ttl) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    public static void put(String key, String value) {
        put(key, value, DEFAULT_TTL);
    }

    public static void put(String key, String value, long ttlMillis) {
        cache.put(key, new CacheEntry(value, ttlMillis));
    }

    public static String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }
        if (entry != null && entry.isExpired()) {
            cache.remove(key);
        }
        return null;
    }

    public static void remove(String key) {
        cache.remove(key);
    }

    public static void clear() {
        cache.clear();
    }

    public static int size() {
        return cache.size();
    }

    public static void cleanupExpired() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}