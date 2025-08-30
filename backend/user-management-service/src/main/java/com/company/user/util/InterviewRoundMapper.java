package com.company.user.util;

import com.company.user.dto.InterviewRoundDto;
import com.company.user.model.Candidate;
import com.company.user.model.InterviewRound;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for mapping between InterviewRound entity and InterviewRoundDto.
 * Provides centralized conversion logic with proper null handling and validation.
 */
@Component
public class InterviewRoundMapper {

    /**
     * Convert InterviewRound entity to DTO
     */
    public InterviewRoundDto toDto(InterviewRound entity) {
        if (entity == null) {
            return null;
        }

        InterviewRoundDto dto = new InterviewRoundDto();
        dto.setId(entity.getId());
        dto.setCandidateId(entity.getCandidate() != null ? entity.getCandidate().getId() : null);
        dto.setRoundType(entity.getRoundType());
        dto.setRoundName(entity.getRoundName());
        dto.setRoundOrder(entity.getRoundOrder());
        dto.setStatus(entity.getStatus());
        dto.setScheduledDate(entity.getScheduledDate());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setDurationMinutes(entity.getDurationMinutes());
        dto.setInterviewerName(entity.getInterviewerName());
        dto.setInterviewerEmail(entity.getInterviewerEmail());
        dto.setMeetingLink(entity.getMeetingLink());
        dto.setNotes(entity.getNotes());
        dto.setFeedback(entity.getFeedback());
        dto.setScore(entity.getScore());
        dto.setMaxScore(entity.getMaxScore());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        return dto;
    }

    /**
     * Convert InterviewRoundDto to entity
     */
    public InterviewRound toEntity(InterviewRoundDto dto) {
        if (dto == null) {
            return null;
        }

        InterviewRound entity = new InterviewRound();
        entity.setId(dto.getId());
        // Note: Candidate will be set separately as it requires repository lookup
        entity.setRoundType(dto.getRoundType());
        entity.setRoundName(dto.getRoundName());
        entity.setRoundOrder(dto.getRoundOrder());
        entity.setStatus(dto.getStatus());
        entity.setScheduledDate(dto.getScheduledDate());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setDurationMinutes(dto.getDurationMinutes());
        entity.setInterviewerName(dto.getInterviewerName());
        entity.setInterviewerEmail(dto.getInterviewerEmail());
        entity.setMeetingLink(dto.getMeetingLink());
        entity.setNotes(dto.getNotes());
        entity.setFeedback(dto.getFeedback());
        entity.setScore(dto.getScore());
        entity.setMaxScore(dto.getMaxScore());

        return entity;
    }

    /**
     * Convert InterviewRoundDto to entity with candidate
     */
    public InterviewRound toEntity(InterviewRoundDto dto, Candidate candidate) {
        InterviewRound entity = toEntity(dto);
        if (entity != null) {
            entity.setCandidate(candidate);
        }
        return entity;
    }

    /**
     * Update existing entity from DTO
     */
    public void updateEntityFromDto(InterviewRound entity, InterviewRoundDto dto) {
        if (entity == null || dto == null) {
            return;
        }

        // Don't update ID and candidate
        if (dto.getRoundType() != null) {
            entity.setRoundType(dto.getRoundType());
        }
        if (dto.getRoundName() != null) {
            entity.setRoundName(dto.getRoundName());
        }
        if (dto.getRoundOrder() != null) {
            entity.setRoundOrder(dto.getRoundOrder());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getScheduledDate() != null) {
            entity.setScheduledDate(dto.getScheduledDate());
        }
        if (dto.getStartTime() != null) {
            entity.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            entity.setEndTime(dto.getEndTime());
        }
        if (dto.getDurationMinutes() != null) {
            entity.setDurationMinutes(dto.getDurationMinutes());
        }
        if (dto.getInterviewerName() != null) {
            entity.setInterviewerName(dto.getInterviewerName());
        }
        if (dto.getInterviewerEmail() != null) {
            entity.setInterviewerEmail(dto.getInterviewerEmail());
        }
        if (dto.getMeetingLink() != null) {
            entity.setMeetingLink(dto.getMeetingLink());
        }
        if (dto.getNotes() != null) {
            entity.setNotes(dto.getNotes());
        }
        if (dto.getFeedback() != null) {
            entity.setFeedback(dto.getFeedback());
        }
        if (dto.getScore() != null) {
            entity.setScore(dto.getScore());
        }
        if (dto.getMaxScore() != null) {
            entity.setMaxScore(dto.getMaxScore());
        }
    }

    /**
     * Convert list of entities to DTOs
     */
    public List<InterviewRoundDto> toDtoList(List<InterviewRound> entities) {
        if (entities == null) {
            return null;
        }

        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Convert list of DTOs to entities (without candidate references)
     */
    public List<InterviewRound> toEntityList(List<InterviewRoundDto> dtos) {
        if (dtos == null) {
            return null;
        }

        return dtos.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * Create a partial DTO for summary views (excludes heavy fields like notes/feedback)
     */
    public InterviewRoundDto toSummaryDto(InterviewRound entity) {
        if (entity == null) {
            return null;
        }

        InterviewRoundDto dto = new InterviewRoundDto();
        dto.setId(entity.getId());
        dto.setCandidateId(entity.getCandidate() != null ? entity.getCandidate().getId() : null);
        dto.setRoundType(entity.getRoundType());
        dto.setRoundName(entity.getRoundName());
        dto.setRoundOrder(entity.getRoundOrder());
        dto.setStatus(entity.getStatus());
        dto.setScheduledDate(entity.getScheduledDate());
        dto.setInterviewerName(entity.getInterviewerName());
        dto.setScore(entity.getScore());
        dto.setMaxScore(entity.getMaxScore());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        return dto;
    }

    /**
     * Convert list of entities to summary DTOs
     */
    public List<InterviewRoundDto> toSummaryDtoList(List<InterviewRound> entities) {
        if (entities == null) {
            return null;
        }

        return entities.stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }
}
