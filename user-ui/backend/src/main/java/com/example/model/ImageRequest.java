package com.example.model;

public class ImageRequest {
    private String prompt;
    private String loraModel;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getLoraModel() {
        return loraModel;
    }

    public void setLoraModel(String loraModel) {
        this.loraModel = loraModel;
    }

}