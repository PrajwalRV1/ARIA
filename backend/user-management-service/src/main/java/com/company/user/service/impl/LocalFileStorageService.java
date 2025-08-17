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

    @Value("${file.storage.base-dir:uploads}") // filesystem base dir
    private String baseDir;

    @Value("${files.storage.root:uploads}") // root dir for URL exposure
    private String storageRoot;

    @Value("${app.base-url:http://localhost:8080}") // base app URL
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
        // Return full public URL (consistent with resume/profilePic behavior)
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

    // ✅ New audio support
    @Override
    public String storeAudio(MultipartFile file) {
        return save(file, "audio");
    }

    @Override
    public void delete(String urlOrPath) {
        // implement deleting filesystem file if needed
    }

    // Legacy utility method (still available for compatibility)
    public String storeFile(MultipartFile file, String subDir) throws IOException {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String uniqueName = UUID.randomUUID().toString() + extension;

        Path targetLocation = Paths.get(baseDir, subDir).toAbsolutePath().normalize();
        Files.createDirectories(targetLocation);

        Path targetFile = targetLocation.resolve(uniqueName);
        Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

        return uniqueName; // only filename (not URL)
    }
}
