package com.company.user.model;

public enum CandidateStatus {
    PENDING,        // Initial status when candidate is added
    APPLIED,        // When candidate has applied but not yet screened
    INTERVIEW,      // During interview process
    SCHEDULED,      // When interview is scheduled
    IN_PROGRESS,    // Interview in progress
    COMPLETED,      // When interview is completed
    UNDER_REVIEW,   // Under review by hiring team
    SELECTED,       // When candidate is selected
    REJECTED,       // When candidate is rejected
    ON_HOLD,        // When candidate is put on hold
    WITHDRAWN       // When candidate withdraws
}
