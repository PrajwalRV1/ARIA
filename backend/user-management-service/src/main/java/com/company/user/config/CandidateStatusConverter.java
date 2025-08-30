package com.company.user.config;

import com.company.user.model.CandidateStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * PostgreSQL enum converter for CandidateStatus to ensure proper conversion
 * between Java enum and PostgreSQL custom enum type.
 * 
 * This converter handles the explicit casting required for PostgreSQL enum types.
 */
@Converter(autoApply = false)
public class CandidateStatusConverter implements AttributeConverter<CandidateStatus, String> {

    @Override
    public String convertToDatabaseColumn(CandidateStatus attribute) {
        if (attribute == null) {
            return null;
        }
        // Convert Java enum to string for PostgreSQL
        return attribute.name();
    }

    @Override
    public CandidateStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Convert PostgreSQL enum value back to Java enum
            return CandidateStatus.valueOf(dbData.trim());
        } catch (IllegalArgumentException e) {
            // Log the error and return a default value
            System.err.println("Unknown CandidateStatus value: " + dbData + ". Using PENDING as default.");
            return CandidateStatus.PENDING;
        }
    }
}
