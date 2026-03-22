package com.mahmoud.ecommerce_backend.service.common.filestorage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String upload(MultipartFile file);

    void delete(String fileUrl);
}