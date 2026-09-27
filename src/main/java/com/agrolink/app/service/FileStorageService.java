package com.agrolink.app.service;

import com.agrolink.app.dto.UploadResponse;
import com.agrolink.app.model.StoredFile;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {


    UploadResponse store(MultipartFile file, String uploadedBy);

    StoredFile load(String id);
}