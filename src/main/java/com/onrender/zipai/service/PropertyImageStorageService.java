package com.onrender.zipai.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PropertyImageStorageService {
    private final SharedPhotoStorageService photos;
    public PropertyImageStorageService(SharedPhotoStorageService photos) { this.photos = photos; }
    public StoredImage store(MultipartFile file) {
        String name = photos.store("lifestyle", file, 5L * 1024 * 1024);
        return new StoredImage(SharedPhotoStorageService.originalName(file.getOriginalFilename()), name, "/api/lifestyle/property-images/" + name);
    }
    public Resource load(String name) { return photos.load("lifestyle", name); }
    public String detectContentType(String name) { return photos.contentType("lifestyle", name); }
    public void delete(String name) { photos.delete("lifestyle", name); }
    public record StoredImage(String originalName, String storedName, String imageUrl) {}
}
