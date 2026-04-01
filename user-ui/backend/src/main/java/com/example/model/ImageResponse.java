package com.example.model;

public class ImageResponse {
    private String imageId;
    private String status;

    public ImageResponse(String imageId, String status) {
        this.imageId = imageId;
        this.status = status;
    }

    public String getImageId() {
        return imageId;
    }

    public String getStatus() {
        return status;
    }

}