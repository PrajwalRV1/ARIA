package com.company.user.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParsedResumeResponse {
    private String name;
    private String email;
    private String phone;
    private List<String> skills;
    private String rawText;
}
