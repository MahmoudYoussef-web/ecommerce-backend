package com.mahmoud.ecommerce_backend.service.common.filestorage;

import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/avif");

    private final Path uploadDir;

    public FileStorageServiceImpl(@Value("${app.upload.dir:./uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create upload directory", ex);
        }
    }

    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BadRequestException("File too large (max 5MB)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Only JPEG/PNG/WebP/GIF/AVIF images are allowed");
        }
        String ext = extensionOf(file.getOriginalFilename(), contentType);
        String fileName = UUID.randomUUID() + ext;
        try {
            Path target = uploadDir.resolve(fileName).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new BadRequestException("Invalid file name");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BadRequestException("Could not store file");
        }
        return "/uploads/" + fileName;
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
            return;
        }
        String name = fileUrl.substring("/uploads/".length());
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return;
        }
        try {
            Files.deleteIfExists(uploadDir.resolve(name).normalize());
        } catch (IOException ignored) {
        }
    }

    private String extensionOf(String original, String contentType) {
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot >= 0) {
                String ext = original.substring(dot).toLowerCase();
                if (ext.matches("\\.(jpe?g|png|webp|gif|avif)")) {
                    return ext.equals(".jpeg") ? ".jpg" : ext;
                }
            }
        }
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/avif" -> ".avif";
            default -> ".jpg";
        };
    }
}
