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
        
        // Secure filename handling to prevent path traversal attacks
        String ext = sanitizeAndExtractExtension(file.getOriginalFilename());
        String name = UUID.randomUUID().toString() + ext;
        
        Path dir = ensureDir(sub);
        Path target = dir.resolve(name);
        
        // Additional security: ensure target path is within expected directory
        if (!target.normalize().startsWith(dir.normalize())) {
            throw new FileStorageException("Invalid file path detected - potential security risk");
        }
        
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file", e);
        }
        // Return full public URL (consistent with resume/profilePic behavior)
        return baseUrl + "/" + storageRoot + "/" + sub + "/" + name;
    }
    
    /**
     * Securely sanitizes filename and extracts extension to prevent path traversal attacks
     */
    private String sanitizeAndExtractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return ".file"; // Default extension for files without names
        }
        
        // Remove dangerous characters and path traversal attempts
        String cleaned = originalFilename.trim()
            .replaceAll("[^a-zA-Z0-9._-]", "_")  // Allow only safe characters
            .replaceAll("\\.\\.", "_")              // Remove .. sequences
            .replaceAll("/+", "_")               // Remove forward slashes
            .replaceAll("\\\\+", "_")              // Remove backslashes
            .replaceAll("\\x00", "_");             // Remove null bytes
        
        // Extract extension safely
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot > 0 && lastDot < cleaned.length() - 1) {
            String extension = cleaned.substring(lastDot).toLowerCase();
            
            // Validate extension length and characters
            if (extension.length() > 10 || !extension.matches("\\.[a-zA-Z0-9]{1,9}")) {
                return ".file"; // Default for invalid extensions
            }
            
            return extension;
        }
        
        return ".file"; // Default extension if none found
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
