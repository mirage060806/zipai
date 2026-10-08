package com.onrender.zipai.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Immutable photos shared by deployments using the same database. */
@Service
public class SharedPhotoStorageService {
    public static final int MAX_STORED_BYTES = 512 * 1024;
    public static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    private final JdbcTemplate jdbc;

    public SharedPhotoStorageService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public String store(String scope, MultipartFile file, long maxInputBytes) {
        if (file == null || file.isEmpty()) throw bad("빈 사진은 업로드할 수 없습니다.");
        if (file.getSize() > maxInputBytes) throw bad("사진 업로드 용량을 초과했습니다.");
        try {
            Encoded encoded = encode(file.getBytes());
            String name = UUID.randomUUID().toString().replace("-", "") + encoded.extension();
            save(scope, name, encoded);
            return name;
        } catch (IOException e) { throw bad("올바른 사진 파일을 선택해 주세요."); }
    }

    @Transactional
    public boolean importFile(String scope, Path path) {
        String name = path.getFileName().toString();
        validate(scope, name);
        if (exists(scope, name)) return false;
        try {
            if (Files.size(path) > 10L * 1024 * 1024) throw bad("기존 사진이 10MB를 초과합니다.");
            save(scope, name, encode(Files.readAllBytes(path)));
            return true;
        } catch (IOException e) { throw new IllegalStateException("기존 사진을 읽을 수 없습니다.", e); }
    }

    private void save(String scope, String name, Encoded encoded) {
        validate(scope, name);
        // One locked row serializes uploads across Render and EC2.
        Long used = jdbc.queryForObject("SELECT used_bytes FROM zipai_photo_budget WHERE id=1 FOR UPDATE", Long.class);
        if (exists(scope, name)) return;
        if (used == null || used + encoded.bytes().length > MAX_TOTAL_BYTES)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "사진 저장 한도(100MB)에 도달했습니다. 관리자에게 문의해 주세요.");
        jdbc.update("INSERT INTO zipai_photo_blob(scope,stored_name,content_type,byte_size,photo_data) VALUES(?,?,?,?,?)",
            scope, name, encoded.type(), encoded.bytes().length, encoded.bytes());
        jdbc.update("UPDATE zipai_photo_budget SET used_bytes=used_bytes+? WHERE id=1", encoded.bytes().length);
    }

    private boolean exists(String scope, String name) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM zipai_photo_blob WHERE scope=? AND stored_name=?", Integer.class, scope, name);
        return count != null && count > 0;
    }

    public Resource load(String scope, String name) {
        validate(scope, name);
        List<byte[]> rows = jdbc.query("SELECT photo_data FROM zipai_photo_blob WHERE scope=? AND stored_name=?",
            (rs, i) -> rs.getBytes(1), scope, name);
        if (!rows.isEmpty()) {
            if (rows.get(0) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 사진입니다.");
            return new ByteArrayResource(rows.get(0));
        }
        // Existing server files remain readable until explicitly imported.
        Path path = localPath(scope, name);
        try {
            if (Files.isRegularFile(path) && Files.isReadable(path)) return new UrlResource(path.toUri());
        } catch (IOException e) { throw new IllegalStateException("사진을 읽을 수 없습니다.", e); }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다.");
    }

    public String contentType(String scope, String name) {
        validate(scope, name);
        List<String> rows = jdbc.query("SELECT content_type FROM zipai_photo_blob WHERE scope=? AND stored_name=?",
            (rs, i) -> rs.getString(1), scope, name);
        if (!rows.isEmpty()) return rows.get(0);
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    @Transactional
    public void delete(String scope, String name) {
        if (name == null || name.isBlank()) return;
        validate(scope, name);
        jdbc.queryForObject("SELECT used_bytes FROM zipai_photo_budget WHERE id=1 FOR UPDATE", Long.class);
        List<Integer> sizes = jdbc.query("SELECT byte_size FROM zipai_photo_blob WHERE scope=? AND stored_name=?",
            (rs, i) -> rs.getInt(1), scope, name);
        if (!sizes.isEmpty()) {
            // Keep a tombstone so old files on another server cannot reappear.
            jdbc.update("UPDATE zipai_photo_blob SET photo_data=NULL,byte_size=0 WHERE scope=? AND stored_name=?", scope, name);
            jdbc.update("UPDATE zipai_photo_budget SET used_bytes=used_bytes-? WHERE id=1", sizes.get(0));
        }
        try { Files.deleteIfExists(localPath(scope, name)); }
        catch (IOException e) { throw new IllegalStateException("기존 사진 삭제에 실패했습니다.", e); }
    }

    public static Path localRoot(String scope) {
        return switch (scope) {
            case "properties" -> Path.of("uploads", "properties");
            case "lifestyle" -> Path.of("uploads", "lifestyle");
            case "community" -> Path.of("static", "uploads", "community");
            default -> throw bad("올바르지 않은 사진 종류입니다.");
        };
    }

    private static Path localPath(String scope, String name) {
        Path root = localRoot(scope).toAbsolutePath().normalize();
        Path path = root.resolve(name).normalize();
        if (!root.equals(path.getParent())) throw bad("올바르지 않은 사진 경로입니다.");
        return path;
    }

    private static void validate(String scope, String name) {
        localRoot(scope);
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) throw bad("올바르지 않은 사진 이름입니다.");
    }

    public static String originalName(String name) {
        if (name == null || name.isBlank()) return "image";
        String safe = name.replace('\\', '/');
        return safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "_");
    }

    static Encoded encode(byte[] input) throws IOException {
        // Java's built-in ImageIO does not decode WebP. Preserve small WebP files.
        if (input.length >= 16 && input[0]=='R' && input[1]=='I' && input[2]=='F' && input[3]=='F'
                && input[8]=='W' && input[9]=='E' && input[10]=='B' && input[11]=='P') {
            if (input.length > MAX_STORED_BYTES) throw bad("WEBP 사진은 512KB 이하로 줄이거나 JPG·PNG로 변환해 주세요.");
            return new Encoded(input, "image/webp", ".webp");
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(input))) {
            if (stream == null) throw bad("사진 형식을 확인해 주세요.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw bad("JPG·PNG·WEBP 사진을 선택해 주세요.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!List.of("jpeg", "jpg", "png").contains(format)) throw bad("JPG·PNG·WEBP 사진을 선택해 주세요.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long)width * height > 40_000_000L) throw bad("사진 해상도가 너무 큽니다.");
                // Subsample before decoding to bound memory on the 1GB EC2 instance.
                var readParam = reader.getDefaultReadParam();
                int step = Math.max(1, (int)Math.ceil(Math.max(width, height) / 1600.0));
                readParam.setSourceSubsampling(step, step, 0, 0);
                BufferedImage source = reader.read(0, readParam);
                int edge = 1280;
                for (int attempt=0; attempt<4; attempt++, edge/=2) {
                    double ratio = Math.min(1.0, (double)edge / Math.max(source.getWidth(), source.getHeight()));
                    BufferedImage image = new BufferedImage(Math.max(1,(int)(source.getWidth()*ratio)), Math.max(1,(int)(source.getHeight()*ratio)), BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE); graphics.fillRect(0,0,image.getWidth(),image.getHeight());
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        graphics.drawImage(source,0,0,image.getWidth(),image.getHeight(),null);
                    } finally { graphics.dispose(); }
                    byte[] bytes = jpeg(image);
                    if (bytes.length <= MAX_STORED_BYTES) return new Encoded(bytes, "image/jpeg", ".jpg");
                }
                throw bad("사진 크기를 줄여 다시 올려 주세요.");
            } finally { reader.dispose(); }
        }
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); param.setCompressionQuality(0.78f);
            writer.write(null, new javax.imageio.IIOImage(image,null,null), param);
        } finally { writer.dispose(); }
        return bytes.toByteArray();
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    record Encoded(byte[] bytes, String type, String extension) {}
}
