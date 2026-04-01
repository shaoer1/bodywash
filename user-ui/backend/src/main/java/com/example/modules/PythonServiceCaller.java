package com.example.modules;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class PythonServiceCaller {
    private static final String PYTHON_SERVICE_URL = "http://localhost:5000/api/generate_image";
    private static final String HEALTH_CHECK_URL = "http://localhost:5000/api/image/health";

    public static String generateImage(String prompt, String loraModel, String baseModelPath) {
        try {
            String loraPath = "";
            if (loraModel != null && !loraModel.isEmpty()) {
                loraPath = "D:\\\\train\\\\output\\\\" + loraModel;
            }
            
            if (baseModelPath == null || baseModelPath.isEmpty()) {
                baseModelPath = "D:\\\\train\\\\model\\\\v1-5-pruned.ckpt";
            }
            
            String jsonPayload = String.format(
                "{\"prompt\": \"%s\", \"model_path\": \"%s\", \"loras\": [{\"path\": \"%s\", \"weight\": 1.0}], \"width\": 512, \"height\": 512, \"steps\": 28, \"cfg_scale\": 7.0}",
                prompt.replace("\"", "\\\""), baseModelPath, loraPath
            );
            
            URL url = new URL(PYTHON_SERVICE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    String imagePath = extractJsonValue(response.toString(), "image_path");
                    if (imagePath != null && !imagePath.isEmpty()) {
                        System.out.println("Python service generated image: " + imagePath);
                        return imagePath;
                    } else {
                        System.err.println("Python service response missing image_path");
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