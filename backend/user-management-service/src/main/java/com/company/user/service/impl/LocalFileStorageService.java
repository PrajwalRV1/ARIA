package com.company.user.service.impl;

import com.company.user.exception.FileStorageException;
import com.company.user.service.inter.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    @Value("${files.storage.root:uploads}")
    private String storageRoot;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private Path ensureDir(String sub) {
        try {
            Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
            Path d = root.resolve(sub).normalize();
            Files.createDirectories(d);
            return d;
        } catch (IOException e) {
            throw new FileStorageException("Could not create storage directory", e);
        }
    }

    private String save(MultipartFile file, String sub) {
        if (file == null || file.isEmpty()) return null;
        String original = StringUtils.cleanPath(file.getOriginalFilename());
        String ext = "";
        int i = original.lastIndexOf('.');
        if (i >= 0) ext = original.substring(i);
        String name = UUID.randomUUID().toString() + ext;
        Path dir = ensureDir(sub);
        Path target = dir.resolve(name);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file", e);
        }
        // Expose via /uploads/** or return direct filesystem path. Here we return public URL.
        return baseUrl + "/" + storageRoot + "/" + sub + "/" + name;
    }

    @Override
    public String storeResume(MultipartFile file) {
        return save(file, "resumes");
    }

    @Override
    public String storeProfilePic(MultipartFile file) {
        return save(file, "profile-pics");
    }

    @Override
    public void delete(String urlOrPath) {
        // implement deleting filesystem file if desired
    }
}
