package com.onrender.zipai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class SharedPhotoStorageServiceTest {
    @Test
    void largePngIsResizedAndEncodedWithinBudget() throws Exception {
        BufferedImage input = new BufferedImage(3000,1500,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(input,"png",bytes);
        var encoded = SharedPhotoStorageService.encode(bytes.toByteArray());
        assertEquals("image/jpeg",encoded.type());
        assertTrue(encoded.bytes().length <= SharedPhotoStorageService.MAX_STORED_BYTES);
        BufferedImage result = ImageIO.read(new java.io.ByteArrayInputStream(encoded.bytes()));
        assertTrue(result.getWidth() <= 1280);
        assertEquals(result.getWidth()/2,result.getHeight());
    }

    @Test
    void transparentPngHasWhiteBackground() throws Exception {
        BufferedImage input = new BufferedImage(20,20,BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(input,"png",bytes);
        var result = SharedPhotoStorageService.encode(bytes.toByteArray());
        var image = ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()));
        assertEquals(Color.WHITE.getRGB(),image.getRGB(10,10));
    }

    @Test
    void invalidFileIsRejected() {
        assertThrows(ResponseStatusException.class, () -> SharedPhotoStorageService.encode(new byte[]{1,2,3}));
    }

    @Test
    void traversalIsRejectedBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var storage = new SharedPhotoStorageService(jdbc);
        assertThrows(ResponseStatusException.class, () -> storage.load("properties","../secret"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void exhaustedBudgetRejectsInsert() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT used_bytes FROM zipai_photo_budget WHERE id=1 FOR UPDATE",Long.class))
            .thenReturn(SharedPhotoStorageService.MAX_TOTAL_BYTES);
        BufferedImage image = new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image,"jpeg",bytes);
        var file = new MockMultipartFile("images","test.jpg","image/jpeg",bytes.toByteArray());
        var error = assertThrows(ResponseStatusException.class, () -> new SharedPhotoStorageService(jdbc).store("properties",file,5*1024*1024));
        assertEquals(409,error.getStatusCode().value());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
