package com.mahmoud.ecommerce_backend.service.common.filestorage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    @Override
    public String upload(MultipartFile file) {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        return "https://cdn.example.com/" + fileName;
    }

    @Override
    public void delete(String fileUrl) {
    }
}
