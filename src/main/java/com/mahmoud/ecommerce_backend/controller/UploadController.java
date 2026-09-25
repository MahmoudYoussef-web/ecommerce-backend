package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.service.common.filestorage.FileStorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Tag(name = "Uploads", description = "Product image upload APIs")
public class UploadController {

    private final FileStorageService fileStorageService;

    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    @PostMapping
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileStorageService.upload(file);
        return ApiResponse.success(Map.of("url", url), "File uploaded");
    }
}
