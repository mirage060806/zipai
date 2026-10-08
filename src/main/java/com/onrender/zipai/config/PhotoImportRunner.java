package com.onrender.zipai.config;

import com.onrender.zipai.service.SharedPhotoStorageService;
import java.nio.file.Files;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="ZIPAI_PHOTO_IMPORT_ENABLED", havingValue="true")
public class PhotoImportRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PhotoImportRunner.class);
    private final SharedPhotoStorageService photos;
    public PhotoImportRunner(SharedPhotoStorageService photos) { this.photos = photos; }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        int imported=0, skipped=0, failed=0;
        for (String scope : List.of("properties", "lifestyle", "community")) {
            var root = SharedPhotoStorageService.localRoot(scope);
            if (!Files.isDirectory(root)) continue;
            try (var paths = Files.list(root)) {
                for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    try {
                        if (photos.importFile(scope, path)) imported++; else skipped++;
                    } catch (Exception error) {
                        failed++;
                        log.warn("[PHOTO-IMPORT] scope={} file={} error={}", scope, path.getFileName(), error.getMessage());
                    }
                }
            }
        }
        log.info("[PHOTO-IMPORT] imported={} skipped={} failed={}; disable ZIPAI_PHOTO_IMPORT_ENABLED after checking results", imported, skipped, failed);
    }
}
