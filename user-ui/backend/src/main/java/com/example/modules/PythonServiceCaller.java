package com.example.modules;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class PythonServiceCaller {
    private static final String PYTHON_SERVICE_URL = "http://localhost:5000/api/generate_image";
    private static final String HEALTH_CHECK_URL = "http://localhost:5000/api/check_environment";
    private static final String CACHE_DIR = "D:/train/user-ui/backend/cache";

    public static String generateImage(String prompt, String loraModel, String baseModelPath) {
        try {
            // 确保缓存目录存在
            File cacheDir = new File(CACHE_DIR);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            String loraPath = "";
            if (loraModel != null && !loraModel.isEmpty()) {
                loraPath = "D:/train/output/" + loraModel;
            }
            
            if (baseModelPath == null || baseModelPath.isEmpty()) {
                baseModelPath = "D:/train/model/v1-5-pruned.ckpt";
            }
            
            String jsonPayload = String.format(
                "{\"prompt\": \"%s\", \"model_path\": \"%s\", \"loras\": [{\"path\": \"%s\", \"weight\": 1.0}], \"width\": 512, \"height\": 512, \"steps\": 5, \"cfg_scale\": 7.0}",
                prompt.replace("\"", "\\\""), baseModelPath, loraPath
            );
            
            System.out.println("Calling Python service with payload: " + jsonPayload);
            
            URL url = new URL(PYTHON_SERVICE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            System.out.println("Python service response code: " + responseCode);
            
            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    String responseStr = response.toString();
                    System.out.println("Python service response: " + responseStr.substring(0, Math.min(200, responseStr.length())) + "...");
                    
                    // 检查是否有 images 字段（base64 编码的图片）
                    String imagesJson = extractJsonArray(responseStr, "images");
                    if (imagesJson != null && !imagesJson.isEmpty()) {
                        // 提取第一个 base64 图片
                        String base64Image = extractFirstArrayItem(imagesJson);
                        if (base64Image != null && !base64Image.isEmpty()) {
                            // 保存 base64 图片到文件
                            String imagePath = saveBase64Image(base64Image);
                            System.out.println("Saved base64 image to: " + imagePath);
                            return imagePath;
                        }
                    }
                    
                    // 兼容旧格式，检查 image_path 字段
                    String imagePath = extractJsonValue(responseStr, "image_path");
                    if (imagePath != null && !imagePath.isEmpty()) {
                        System.out.println("Python service generated image: " + imagePath);
                        return imagePath;
                    } else {
                        System.err.println("Python service response missing image data");
                        return null;
                    }
                }
            } else {
                System.err.println("Python service returned error code: " + responseCode);
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = br.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    System.err.println("Error response: " + errorResponse.toString());
                }
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error calling Python service: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private static String saveBase64Image(String base64Data) {
        try {
            // 移除 data:image/png;base64, 前缀
            if (base64Data.contains(",")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }
            
            // 解码 base64
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            
            // 生成文件名
            String fileName = UUID.randomUUID().toString().replace("-", "") + ".png";
            String filePath = CACHE_DIR + "/" + fileName;
            
            // 保存文件
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(imageBytes);
            }
            
            return filePath;
        } catch (Exception e) {
            System.err.println("Error saving base64 image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private static String extractJsonArray(String json, String key) {
        String searchPattern = "\"" + key + "\"";
        int startIndex = json.indexOf(searchPattern);
        if (startIndex < 0) {
            return null;
        }
        
        startIndex = json.indexOf("[", startIndex);
        if (startIndex < 0) {
            return null;
        }
        
        int endIndex = json.indexOf("]", startIndex);
        if (endIndex < 0) {
            return null;
        }
        
        return json.substring(startIndex, endIndex + 1);
    }
    
    private static String extractFirstArrayItem(String arrayJson) {
        int startIndex = arrayJson.indexOf("\"");
        if (startIndex < 0) {
            return null;
        }
        
        int endIndex = arrayJson.indexOf("\"", startIndex + 1);
        if (endIndex < 0) {
            return null;
        }
        
        return arrayJson.substring(startIndex + 1, endIndex);
    }

    public static boolean isServiceAvailable() {
        try {
            URL url = new URL(HEALTH_CHECK_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            
            int responseCode = conn.getResponseCode();
            boolean available = responseCode == 200;
            System.out.println("Python service status: " + (available ? "available" : "unavailable"));
            return available;
            
        } catch (Exception e) {
            System.err.println("Python service health check failed: " + e.getMessage());
            return false;
        }
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
}
