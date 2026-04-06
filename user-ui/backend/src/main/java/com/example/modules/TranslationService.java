package com.example.modules;

import java.util.HashMap;
import java.util.Map;

public class TranslationService {
    private static final Map<String, String> DICTIONARY = new HashMap<>();

    static {
        // 产品类型
        DICTIONARY.put("旋盖式", "screw cap");
        DICTIONARY.put("泵头式", "pump dispenser");
        DICTIONARY.put("翻盖式", "flip cap");
        DICTIONARY.put("喷雾式", "spray");
        
        // 产品品类
        DICTIONARY.put("沐浴露", "body wash");
        
        // 比例
        DICTIONARY.put("修长", "tall and slender");
        DICTIONARY.put("矮胖", "short and wide");
        DICTIONARY.put("均匀", "balanced proportion");
        
        // 颜色
        DICTIONARY.put("白色", "white");
        DICTIONARY.put("黑色", "black");
        DICTIONARY.put("黄色", "yellow");
        DICTIONARY.put("红色", "red");
        DICTIONARY.put("蓝色", "blue");
        
        // 形状
        DICTIONARY.put("长方形", "rectangular");
        DICTIONARY.put("正方形", "square");
        DICTIONARY.put("圆形", "round");
        DICTIONARY.put("椭圆形", "oval");
    }

    public static String translateChineseToEnglish(String chinesePrompt) {
        if (chinesePrompt == null || chinesePrompt.isEmpty()) {
            return "";
        }

        StringBuilder englishPrompt = new StringBuilder();
        
        for (Map.Entry<String, String> entry : DICTIONARY.entrySet()) {
            if (chinesePrompt.contains(entry.getKey())) {
                englishPrompt.append(entry.getValue()).append(", ");
            }
        }
        
        String result = englishPrompt.toString().trim();
        if (result.endsWith(",")) {
            result = result.substring(0, result.length() - 1);
        }
        
        return result;
    }

    public static String buildFinalPrompt(String translatedPrompt, String loraTriggerWord) {
        StringBuilder finalPrompt = new StringBuilder();
        
        if (loraTriggerWord != null && !loraTriggerWord.isEmpty()) {
            finalPrompt.append(loraTriggerWord).append(", ");
        }
        
        if (translatedPrompt != null && !translatedPrompt.isEmpty()) {
            finalPrompt.append(translatedPrompt);
        }
        
        return finalPrompt.toString().trim();
    }

    public static String extractTriggerWord(String loraFileName) {
        if (loraFileName == null || loraFileName.isEmpty()) {
            return "";
        }
        
        int lastHyphenIndex = loraFileName.lastIndexOf('-');
        if (lastHyphenIndex >= 0 && lastHyphenIndex < loraFileName.length() - 1) {
            String triggerWord = loraFileName.substring(lastHyphenIndex + 1);
            int dotIndex = triggerWord.lastIndexOf('.');
            if (dotIndex >= 0) {
                triggerWord = triggerWord.substring(0, dotIndex);
            }
            return triggerWord;
        }
        
        return "";
    }
}