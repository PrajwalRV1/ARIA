package com.company.user.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileUploadResponse {
    private String fileName;
    private String url;
    private long size;
    private String contentType;
}
