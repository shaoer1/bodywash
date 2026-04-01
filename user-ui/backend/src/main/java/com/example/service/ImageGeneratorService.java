package com.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class ImageGeneratorService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PromptTranslatorService promptTranslatorService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String REDIS_KEY_PREFIX = "image:";
    private static final long EXPIRATION_TIME_SECONDS = 86400; // 24 小时
    private static final long EXPIRATION_TIME_MILLIS = EXPIRATION_TIME_SECONDS * 1000L;
    private static final String PYTHON_INFER_URL = "http://127.0.0.1:5000/api/generate_image";
    private static final String BASE_MODEL_PATH = "D:\\train\\model\\v1-5-pruned.ckpt";
    private static final String LORA_DIR = "D:\\train\\output";
    private static final Path CACHE_DIR = Paths.get("cache");

    // Redis 不可用时的兜底缓存，避免接口 500
    private final Map<String, String> localUrlCache = new ConcurrentHashMap<>();

    @Async
    public CompletableFuture<String> generateImage(String prompt, String loraModel) {
        try {
            Files.createDirectories(CACHE_DIR);

            String translatedPrompt = promptTranslatorService.translateFixedPrompt(prompt);
            String loraTriggerWord = promptTranslatorService.extractLoraTriggerWord(loraModel);
            String finalPrompt = promptTranslatorService.buildFinalPrompt(translatedPrompt, loraTriggerWord);

            String imageDataUrl = callPythonInference(finalPrompt, loraModel);
            if (imageDataUrl == null || imageDataUrl.isBlank()) {
                return CompletableFuture.failedFuture(new RuntimeException("推理服务未返回图片"));
            }

            String imageId = UUID.randomUUID().toString().replace("-", "");
            saveDataUrlToPng(imageId, imageDataUrl);

            String imageUrl = "/api/image/file/" + imageId;
            putUrlToCache(imageId, imageUrl);

            return CompletableFuture.completedFuture(imageId);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public String getImageUrl(String imageId) {
        String key = REDIS_KEY_PREFIX + imageId;

        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (val instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Exception ignored) {
        }

        String local = localUrlCache.get(imageId);
        if (local != null) {
            return local;
        }

        Path file = CACHE_DIR.resolve(imageId + ".png");
        if (Files.exists(file)) {
            String imageUrl = "/api/image/file/" + imageId;
            putUrlToCache(imageId, imageUrl);
            return imageUrl;
        }

        return null;
    }

    public byte[] getImageBytes(String imageId) throws IOException {
        Path file = CACHE_DIR.resolve(imageId + ".png");
        if (!Files.exists(file)) {
            return null;
        }
        return Files.readAllBytes(file);
    }

    private String callPythonInference(String finalPrompt, String loraModel) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model_path", BASE_MODEL_PATH);
        payload.put("is_sdxl", false);
        payload.put("prompt", finalPrompt);
        payload.put("negative_prompt", "text, watermark, blurry, low quality");
        payload.put("width", 512);
        payload.put("height", 512);
        payload.put("steps", 20);
        payload.put("cfg_scale", 7.0);
        payload.put("seed", -1);
        payload.put("num_images", 1);
        payload.put("scheduler", "euler_a");

        List<Map<String, Object>> loras = new ArrayList<>();
        if (loraModel != null && !loraModel.isBlank()) {
            Map<String, Object> lora = new LinkedHashMap<>();
            lora.put("path", Paths.get(LORA_DIR, loraModel).toString());
            lora.put("weight", 0.8);
            loras.add(lora);
        }
        payload.put("loras", loras);

        String body = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PYTHON_INFER_URL))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("推理服务调用失败: HTTP " + response.statusCode());
        }

        Map<String, Object> result = objectMapper.readValue(response.body(), new TypeReference<>() {});
        Object success = result.get("success");
        if (!(success instanceof Boolean) || !((Boolean) success)) {
            throw new RuntimeException("推理服务返回失败: " + result.getOrDefault("message", "unknown"));
        }

        Object imagesObj = result.get("images");
        if (!(imagesObj instanceof List<?> images) || images.isEmpty()) {
            return null;
        }
        Object first = images.get(0);
        return first == null ? null : String.valueOf(first);
    }

    private void saveDataUrlToPng(String imageId, String dataUrl) throws IOException {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new IOException("图片数据格式错误");
        }
        String base64 = dataUrl.substring(comma + 1);
        byte[] bytes = Base64.getDecoder().decode(base64);
        Path file = CACHE_DIR.resolve(imageId + ".png");
        Files.write(file, bytes);
    }

    private void putUrlToCache(String imageId, String imageUrl) {
        localUrlCache.put(imageId, imageUrl);
        try {
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + imageId,
                    imageUrl,
                    EXPIRATION_TIME_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception ignored) {
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void cleanupExpiredCacheFiles() {
        try {
            if (!Files.exists(CACHE_DIR)) {
                return;
            }
            long now = System.currentTimeMillis();
            Files.list(CACHE_DIR)
                    .filter(p -> p.getFileName().toString().endsWith(".png"))
                    .forEach(p -> {
                        try {
                            long lastModified = Files.getLastModifiedTime(p).toMillis();
                            if (now - lastModified > EXPIRATION_TIME_MILLIS) {
                                Files.deleteIfExists(p);
                            }
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    public void clearCache(String imageId) {
        localUrlCache.remove(imageId);
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + imageId);
        } catch (Exception ignored) {
        }
        try {
            Files.deleteIfExists(CACHE_DIR.resolve(imageId + ".png"));
        } catch (Exception ignored) {
        }
    }
}
