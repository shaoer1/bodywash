package com.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SimpleServer {

    public static void main(String[] args) throws Exception {
        // 创建HTTP服务器，监听8080端口
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // 注册路由
        server.createContext("/api/image/generate", new GenerateHandler());
        server.createContext("/api/image/get", new GetImageHandler());
        server.createContext("/api/image/health", new HealthHandler());
        server.createContext("/api/lora/models", new LoraModelsHandler());
        
        // 启动服务器
        server.start();
        System.out.println("Server started on port 8080");
    }

    // 生成图片的处理器
    static class GenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // 读取请求体
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                
                // 简单解析JSON（实际项目中应该使用JSON库）
                String prompt = "";
                String loraModel = "";
                
                if (requestBody.contains("prompt")) {
                    int start = requestBody.indexOf("prompt") + 8;
                    int end = requestBody.indexOf('"', start);
                    if (end > start) {
                        prompt = requestBody.substring(start, end);
                    }
                }
                
                if (requestBody.contains("loraModel")) {
                    int start = requestBody.indexOf("loraModel") + 11;
                    int end = requestBody.indexOf('"', start);
                    if (end > start) {
                        loraModel = requestBody.substring(start, end);
                    }
                }
                
                // 翻译中文提示词
                String translated = translate(prompt);
                
                // 提取LoRA触发词
                String triggerWord = extractTriggerWord(loraModel);
                
                // 构建最终提示词
                String finalPrompt = buildFinalPrompt(translated, triggerWord);
                
                System.out.println("Final prompt: " + finalPrompt);
                
                // 生成图片URL
                String imageUrl = "https://neeko-copilot.bytedance.net/api/text2image?prompt=" + finalPrompt.replace(" ", "+");
                
                // 生成唯一ID
                String imageId = java.util.UUID.randomUUID().toString();
                
                // 缓存到内存（实际项目中应该使用Redis）
                ImageCache.put(imageId, imageUrl);
                
                // 返回响应
                String response = "{\"imageId\": \"" + imageId + "\", \"message\": \"Generating image...\"}";
                sendResponse(exchange, 200, response);
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // 获取图片的处理器
    static class GetImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                // 获取路径参数
                String path = exchange.getRequestURI().getPath();
                String imageId = path.substring(path.lastIndexOf('/') + 1);
                
                // 从缓存获取图片URL
                String imageUrl = ImageCache.get(imageId);
                
                if (imageUrl != null) {
                    String response = "{\"imageUrl\": \"" + imageUrl + "\"}";
                    sendResponse(exchange, 200, response);
                } else {
                    String response = "{\"error\": \"Image not found\"}";
                    sendResponse(exchange, 404, response);
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }
    }

    // 健康检查的处理器
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\": \"ok\"}";
            sendResponse(exchange, 200, response);
        }
    }

    // 获取LoRA模型的处理器
    static class LoraModelsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 扫描LoRA模型目录
            List<LoraModel> models = scanLoraModels();
            
            // 构建响应
            StringBuilder response = new StringBuilder("{\"models\": [");
            for (int i = 0; i < models.size(); i++) {
                LoraModel model = models.get(i);
                response.append("{\"fileName\": \"").append(model.fileName).append("\", \"triggerWord\": \"").append(model.triggerWord).append("\"");
                if (i < models.size() - 1) {
                    response.append(",");
                }
            }
            response.append("]}");
            
            sendResponse(exchange, 200, response.toString());
        }
    }

    // 翻译中文提示词
    private static String translate(String chinesePrompt) {
        if (chinesePrompt == null || chinesePrompt.isEmpty()) {
            return "";
        }

        StringBuilder englishPrompt = new StringBuilder();
        
        // 颜色检测
        if (chinesePrompt.contains("白色")) {
            englishPrompt.append("white, ");
        }
        if (chinesePrompt.contains("黑色")) {
            englishPrompt.append("black, ");
        }
        if (chinesePrompt.contains("黄色")) {
            englishPrompt.append("yellow, ");
        }
        if (chinesePrompt.contains("红色")) {
            englishPrompt.append("red, ");
        }
        if (chinesePrompt.contains("蓝色")) {
            englishPrompt.append("blue, ");
        }
        
        // 产品
        if (chinesePrompt.contains("沐浴露")) {
            englishPrompt.append("body wash, ");
        }
        
        // 瓶盖类型
        if (chinesePrompt.contains("旋盖")) {
            englishPrompt.append("screw cap, ");
        }
        if (chinesePrompt.contains("泵头")) {
            englishPrompt.append("pump dispenser, ");
        }
        if (chinesePrompt.contains("翻盖")) {
            englishPrompt.append("flip cap, ");
        }
        
        // 比例
        if (chinesePrompt.contains("修长")) {
            englishPrompt.append("tall and slender, ");
        }
        if (chinesePrompt.contains("矮胖")) {
            englishPrompt.append("short and wide, ");
        }
        if (chinesePrompt.contains("均匀")) {
            englishPrompt.append("balanced proportion, ");
        }
        
        String result = englishPrompt.toString().trim();
        if (result.endsWith(",")) {
            result = result.substring(0, result.length() - 1);
        }
        
        return result;
    }

    // 提取LoRA触发词
    private static String extractTriggerWord(String loraFileName) {
        if (loraFileName == null) {
            return "";
        }
        
        int lastUnderscoreIndex = loraFileName.lastIndexOf('_');
        if (lastUnderscoreIndex >= 0 && lastUnderscoreIndex < loraFileName.length() - 1) {
            String triggerWord = loraFileName.substring(lastUnderscoreIndex + 1);
            int dotIndex = triggerWord.lastIndexOf('.');
            if (dotIndex >= 0) {
                triggerWord = triggerWord.substring(0, dotIndex);
            }
            return triggerWord;
        }
        
        return "";
    }

    // 构建最终提示词
    private static String buildFinalPrompt(String translatedPrompt, String loraTriggerWord) {
        StringBuilder finalPrompt = new StringBuilder();
        
        if (!loraTriggerWord.isEmpty()) {
            finalPrompt.append(loraTriggerWord).append(", ");
        }
        
        if (!translatedPrompt.isEmpty()) {
            finalPrompt.append(translatedPrompt);
        }
        
        return finalPrompt.toString().trim();
    }

    // 扫描LoRA模型
    private static List<LoraModel> scanLoraModels() {
        List<LoraModel> models = new ArrayList<>();
        
        // 模拟LoRA模型
        models.add(new LoraModel("bodywash-black.safetensors", "black"));
        models.add(new LoraModel("bodywash-blue.safetensors", "blue"));
        
        return models;
    }

    // 发送响应
    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    // LoRA模型类
    static class LoraModel {
        String fileName;
        String triggerWord;

        LoraModel(String fileName, String triggerWord) {
            this.fileName = fileName;
            this.triggerWord = triggerWord;
        }
    }

    // 简单的内存缓存
    static class ImageCache {
        private static final java.util.Map<String, String> cache = new java.util.HashMap<>();

        static void put(String key, String value) {
            cache.put(key, value);
        }

        static String get(String key) {
            return cache.get(key);
        }
    }

}
