package com.company.user.dto;

public class AudioUploadResponse {
    private String filename;
    private String url;
    private long size;

    public AudioUploadResponse(String filename, String url, long size) {
        this.filename = filename;
        this.url = url;
        this.size = size;
    }

    public String getFilename() {
        return filename;
    }

    public String getUrl() {
        return url;
    }

    public long getSize() {
        return size;
    }
}
