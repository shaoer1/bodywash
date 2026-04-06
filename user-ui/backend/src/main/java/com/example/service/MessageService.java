package com.example.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class MessageService {

    private static final String REDIS_KEY = "messages";
    private static final long EXPIRATION_TIME_SECONDS = 86400; // 24 小时

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public List<Object> getMessages() {
        try {
            List<Object> messages = (List<Object>) redisTemplate.opsForValue().get(REDIS_KEY);
            return messages != null ? messages : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void saveMessages(List<Object> messages) {
        try {
            redisTemplate.opsForValue().set(
                    REDIS_KEY,
                    messages,
                    EXPIRATION_TIME_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clearMessages() {
        try {
            redisTemplate.delete(REDIS_KEY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
