package com.company.user.service.inter;

import com.company.user.dto.ParsedResumeResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeParsingService {
    ParsedResumeResponse parse(MultipartFile resumeFile);
}
