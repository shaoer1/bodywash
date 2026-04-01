package com.example.modules;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

public class ImageStorageService {
    private static final String CACHE_DIR = "D:\\train\\user-ui\\cache";
    private static final String OUTPUT_DIR = "D:\\train\\user-ui\\output";
    private static final long IMAGE_EXPIRE_HOURS = 24;

    public static String saveImage(String sourcePath, String imageId) {
        try {
            Files.createDirectories(Paths.get(CACHE_DIR));
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            
            String extension = getFileExtension(sourcePath);
            String targetFileName = imageId + extension;
            Path targetPath = Paths.get(OUTPUT_DIR, targetFileName);
            
            if (Files.exists(Paths.get(sourcePath))) {
                Files.copy(Paths.get(sourcePath), targetPath, StandardCopyOption.REPLACE_EXISTING);
                
                String imageUrl = "/output/" + targetFileName;
                System.out.println("Image saved to: " + targetPath);
                
                scheduleImageCleanup(targetPath);
                
                return imageUrl;
            }
            
            return null;
            
        } catch (Exception e) {
            System.err.println("Error saving image to cache: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static boolean deleteImage(String imageId) {
        try {
            Files.walk(Paths.get(OUTPUT_DIR))
                .filter(path -> path.getFileName().toString().startsWith(imageId))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        System.out.println("Deleted image: " + path);
                    } catch (Exception e) {
                        System.err.println("Error deleting image: " + e.getMessage());
                    }
                });
            return true;
        } catch (Exception e) {
            System.err.println("Error deleting image: " + e.getMessage());
            return false;
        }
    }

    private static void scheduleImageCleanup(Path imagePath) {
        java.util.Timer timer = new java.util.Timer();
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    if (Files.exists(imagePath)) {
                        Files.delete(imagePath);
                        System.out.println("Expired image deleted: " + imagePath);
                    }
                } catch (Exception e) {
                    System.err.println("Error deleting expired image: " + e.getMessage());
                }
            }
        }, IMAGE_EXPIRE_HOURS * 60 * 60 * 1000);
    }

    private static String getFileExtension(String filePath) {
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex);
        }
        return ".png";
    }

    public static String generateImageId() {
        return UUID.randomUUID().toString();
    }
}