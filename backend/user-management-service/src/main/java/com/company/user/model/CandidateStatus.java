package com.company.user.model;

public enum CandidateStatus {
    PENDING,     // Initial status when candidate is added
    APPLIED,     // When candidate has applied but not yet screened
    SCREENING,   // During initial screening phase
    SCHEDULED,   // When interview is scheduled
    COMPLETED,   // When interview is completed
    SELECTED,    // When candidate is selected
    REJECTED,    // When candidate is rejected
    ON_HOLD      // When candidate is put on hold
}
