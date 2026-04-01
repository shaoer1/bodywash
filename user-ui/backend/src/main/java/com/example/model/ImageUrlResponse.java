package com.example.model;

public class ImageUrlResponse {
    private String imageId;
    private String imageUrl;

    public ImageUrlResponse(String imageId, String imageUrl) {
        this.imageId = imageId;
        this.imageUrl = imageUrl;
    }

    public String getImageId() {
        return imageId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

}