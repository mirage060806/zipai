package com.onrender.zipai.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PropertyListingImageStorageService {
    private final SharedPhotoStorageService photos;
    public PropertyListingImageStorageService(SharedPhotoStorageService photos) { this.photos = photos; }
    public StoredImage store(MultipartFile file) {
        String name = photos.store("properties", file, 5L * 1024 * 1024);
        return new StoredImage(SharedPhotoStorageService.originalName(file.getOriginalFilename()), name, "/api/properties/images/" + name);
    }
    public Resource load(String name) { return photos.load("properties", name); }
    public String contentType(String name) { return photos.contentType("properties", name); }
    public void delete(String name) { photos.delete("properties", name); }
    public record StoredImage(String originalName, String storedName, String imageUrl) {}
}
