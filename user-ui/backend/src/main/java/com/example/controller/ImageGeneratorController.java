package com.example.controller;

import com.example.model.HealthResponse;
import com.example.model.ImageRequest;
import com.example.model.ImageResponse;
import com.example.model.ImageUrlResponse;
import com.example.service.ImageGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/image")
public class ImageGeneratorController {

    @Autowired
    private ImageGeneratorService imageGeneratorService;

    @PostMapping("/generate")
    public CompletableFuture<ImageResponse> generateImage(@RequestBody ImageRequest request) {
        return imageGeneratorService.generateImage(request.getPrompt(), request.getLoraModel())
                .thenApply(imageId -> new ImageResponse(imageId, "Generating image..."));
    }

    @GetMapping("/get/{imageId}")
    public ImageUrlResponse getImageUrl(@PathVariable String imageId) {
        String imageUrl = imageGeneratorService.getImageUrl(imageId);
        return new ImageUrlResponse(imageId, imageUrl);
    }

    @GetMapping("/file/{imageId}")
    public ResponseEntity<byte[]> getImageFile(@PathVariable String imageId) {
        try {
            byte[] bytes = imageGeneratorService.getImageBytes(imageId);
            if (bytes == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .contentType(MediaType.IMAGE_PNG)
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("OK");
    }
}
