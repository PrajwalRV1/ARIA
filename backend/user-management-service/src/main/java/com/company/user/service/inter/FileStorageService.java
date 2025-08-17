package com.company.user.service.inter;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    // returns public URL or path
    String storeResume(MultipartFile file);
    String storeProfilePic(MultipartFile file);
    void delete(String urlOrPath);
    String storeAudio(MultipartFile audio);
}
