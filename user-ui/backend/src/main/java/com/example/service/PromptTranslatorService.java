package com.example.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PromptTranslatorService {

    private static final Map<String, String> TOKEN_MAP = new LinkedHashMap<>();

    static {
        TOKEN_MAP.put("沐浴露", "body wash");

        TOKEN_MAP.put("旋盖式", "screw cap");
        TOKEN_MAP.put("旋盖", "screw cap");
        TOKEN_MAP.put("压泵式", "pump dispenser");
        TOKEN_MAP.put("压泵", "pump dispenser");
        TOKEN_MAP.put("泵头式", "pump dispenser");
        TOKEN_MAP.put("泵头", "pump dispenser");
        TOKEN_MAP.put("翻盖式", "flip cap");
        TOKEN_MAP.put("翻盖", "flip cap");
        TOKEN_MAP.put("喷雾式", "spray nozzle");
        TOKEN_MAP.put("喷雾", "spray nozzle");

        TOKEN_MAP.put("长方形", "rectangular");
        TOKEN_MAP.put("方形", "square");
        TOKEN_MAP.put("圆形", "round");

        TOKEN_MAP.put("修长", "tall and slender");
        TOKEN_MAP.put("矮胖", "short and wide");
        TOKEN_MAP.put("均匀", "balanced proportion");

        TOKEN_MAP.put("白色", "white");
        TOKEN_MAP.put("黑色", "black");
        TOKEN_MAP.put("黄色", "yellow");
        TOKEN_MAP.put("红色", "red");
        TOKEN_MAP.put("蓝色", "blue");
    }

    public String translateFixedPrompt(String chinesePrompt) {
        if (chinesePrompt == null || chinesePrompt.isBlank()) {
            return "";
        }

        String normalized = chinesePrompt.replace('，', ',').trim();
        String[] tokens = normalized.split("\\s*,\\s*");
        List<String> english = new ArrayList<>();

        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String mapped = TOKEN_MAP.get(token.trim());
            if (mapped != null && !english.contains(mapped)) {
                english.add(mapped);
            }
        }

        return String.join(", ", english);
    }

    public String extractLoraTriggerWord(String loraFileName) {
        if (loraFileName == null || loraFileName.isBlank()) {
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

    public String buildFinalPrompt(String translatedPrompt, String loraTriggerWord) {
        StringBuilder finalPrompt = new StringBuilder();

        if (loraTriggerWord != null && !loraTriggerWord.isBlank()) {
            finalPrompt.append(loraTriggerWord);
        }

        if (translatedPrompt != null && !translatedPrompt.isBlank()) {
            if (!finalPrompt.isEmpty()) {
                finalPrompt.append(", ");
            }
            finalPrompt.append(translatedPrompt);
        }

        return finalPrompt.toString().trim();
    }
}
