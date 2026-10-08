package com.onrender.zipai.web;

import com.onrender.zipai.service.SharedPhotoStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommunityImageController {
    private final SharedPhotoStorageService photos;
    public CommunityImageController(SharedPhotoStorageService photos) { this.photos = photos; }

    @GetMapping("/static/uploads/community/{name:.+}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        Resource photo = photos.load("community", name);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
            .contentType(MediaType.parseMediaType(photos.contentType("community", name))).body(photo);
    }
}
