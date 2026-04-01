package com.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ImprovedServer {

    private static final String PYTHON_SERVICE_URL = "http://localhost:5000/api/generate_image";
    private static final String CACHE_DIR = "D:\\train\\user-ui\\cache";
    private static final String OUTPUT_DIR = "D:\\train\\user-ui\\output";
    private static final String LORA_DIR = "D:\\train\\output";
    private static final int IMAGE_EXPIRE_HOURS = 24;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        
        server.createContext("/api/image/generate", new GenerateHandler());
        server.createContext("/api/image/get", new GetImageHandler());
        server.createContext("/api/image/health", new HealthHandler());
        server.createContext("/api/lora/models", new LoraModelsHandler());
        
        server.start();
        System.out.println("Server started on port 8081");
        System.out.println("Cache directory: " + CACHE_DIR);
        System.out.println("Output directory: " + OUTPUT_DIR);
        System.out.println("LoRA directory: " + LORA_DIR);
    }

    static class GenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                
                String prompt = extractJsonValue(requestBody, "prompt");
                String loraModel = extractJsonValue(requestBody, "loraModel");
                
                System.out.println("Received request - Prompt: " + prompt + ", LoRA: " + loraModel);
                
                String translated = com.example.modules.TranslationService.translateChineseToEnglish(prompt);
                String triggerWord = com.example.modules.TranslationService.extractTriggerWord(loraModel);
                String finalPrompt = com.example.modules.TranslationService.buildFinalPrompt(translated, triggerWord);
                
                System.out.println("Final prompt: " + finalPrompt);
                
                String imageId = com.example.modules.ImageStorageService.generateImageId();
                
                String imagePath = com.example.modules.PythonServiceCaller.generateImage(finalPrompt, loraModel, null);
                
                if (imagePath != null) {
                    String imageUrl = com.example.modules.ImageStorageService.saveImage(imagePath, imageId);
                    com.example.modules.RedisService.put(imageId, imageUrl);
                    String response = "{\"imageId\": \"" + imageId + "\", \"message\": \"Image generated successfully\"}";
                    sendResponse(exchange, 200, response);
                } else {
                    String response = "{\"error\": \"Failed to generate image\"}";
                    sendResponse(exchange, 500, response);
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }
    }

    static class GetImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                String imageId = path.substring(path.lastIndexOf('/') + 1);
                
                String imageUrl = com.example.modules.RedisService.get(imageId);
                
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

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            boolean pythonAvailable = com.example.modules.PythonServiceCaller.isServiceAvailable();
            String response = "{\"status\": \"ok\", \"pythonService\": \"" + (pythonAvailable ? "available" : "unavailable") + "\"}";
            sendResponse(exchange, 200, response);
        }
    }

    static class LoraModelsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<LoraModel> models = scanLoraModels();
            
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

    private static List<LoraModel> scanLoraModels() {
        List<LoraModel> models = new ArrayList<>();
        
        try {
            File loraDir = new File(LORA_DIR);
            if (loraDir.exists() && loraDir.isDirectory()) {
                File[] files = loraDir.listFiles((dir, name) -> 
                    name.endsWith(".safetensors") || name.endsWith(".ckpt"));
                
                if (files != null) {
                    for (File file : files) {
                        String fileName = file.getName();
                        String triggerWord = com.example.modules.TranslationService.extractTriggerWord(fileName);
                        models.add(new LoraModel(fileName, triggerWord));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error scanning LoRA models: " + e.getMessage());
        }
        
        return models;
    }

    private static String extractJsonValue(String json, String key) {
        String searchPattern = "\"" + key + "\"";
        int startIndex = json.indexOf(searchPattern);
        if (startIndex < 0) {
            return "";
        }
        
        startIndex = json.indexOf(":", startIndex);
        if (startIndex < 0) {
            return "";
        }
        
        startIndex = json.indexOf("\"", startIndex);
        if (startIndex < 0) {
            return "";
        }
        
        int endIndex = json.indexOf("\"", startIndex + 1);
        if (endIndex < 0) {
            return "";
        }
        
        return json.substring(startIndex + 1, endIndex);
    }

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

    static class LoraModel {
        String fileName;
        String triggerWord;

        LoraModel(String fileName, String triggerWord) {
            this.fileName = fileName;
            this.triggerWord = triggerWord;
        }
    }
}