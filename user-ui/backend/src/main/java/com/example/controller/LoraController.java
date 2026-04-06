package com.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/lora")
public class LoraController {

    private static final String LORA_DIR = "D:\\train\\output";

    @GetMapping("/models")
    public LoraModelsResponse getLoraModels() {
        List<LoraModel> models = new ArrayList<>();
        
        File loraDir = new File(LORA_DIR);
        if (loraDir.exists() && loraDir.isDirectory()) {
            File[] files = loraDir.listFiles((dir, name) -> name.endsWith(".safetensors"));
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    String triggerWord = extractTriggerWord(fileName);
                    models.add(new LoraModel(fileName, triggerWord));
                }
            }
        }
        
        return new LoraModelsResponse(models);
    }

    private String extractTriggerWord(String fileName) {
        if (fileName == null) {
            return "";
        }
        
        int lastUnderscoreIndex = fileName.lastIndexOf('_');
        int lastDashIndex = fileName.lastIndexOf('-');
        
        int splitIndex = Math.max(lastUnderscoreIndex, lastDashIndex);
        
        if (splitIndex >= 0 && splitIndex < fileName.length() - 1) {
            String triggerWord = fileName.substring(splitIndex + 1);
            int dotIndex = triggerWord.lastIndexOf('.');
            if (dotIndex >= 0) {
                triggerWord = triggerWord.substring(0, dotIndex);
            }
            return triggerWord;
        }
        
        return "";
    }

    public static class LoraModel {
        private String fileName;
        private String triggerWord;

        public LoraModel(String fileName, String triggerWord) {
            this.fileName = fileName;
            this.triggerWord = triggerWord;
        }

        public String getFileName() {
            return fileName;
        }

        public String getTriggerWord() {
            return triggerWord;
        }
    }

    public static class LoraModelsResponse {
        private List<LoraModel> models;

        public LoraModelsResponse(List<LoraModel> models) {
            this.models = models;
        }

        public List<LoraModel> getModels() {
            return models;
        }
    }

}