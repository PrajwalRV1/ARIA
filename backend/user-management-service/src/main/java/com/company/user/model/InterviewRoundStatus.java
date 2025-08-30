package com.company.user.model;

/**
 * Enum representing the status of individual interview rounds.
 * Each round can have its own independent status as specified in requirements.
 */
public enum InterviewRoundStatus {
    NOT_STARTED("Not Started"),
    INTERVIEW_SCHEDULED("Interview Scheduled"),
    IN_PROGRESS("In Progress"),
    UNDER_REVIEW("Under Review"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    private final String displayName;

    InterviewRoundStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this status represents a completed round (successful or failed)
     */
    public boolean isCompleted() {
        return this == COMPLETED || this == REJECTED || this == WITHDRAWN;
    }

    /**
     * Check if this status is terminal (no further transitions possible)
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == WITHDRAWN;
    }

    /**
     * Check if this status represents an active interview process
     */
    public boolean isActive() {
        return this == INTERVIEW_SCHEDULED || this == IN_PROGRESS;
    }

    /**
     * Check if this status indicates the round can be started
     */
    public boolean canBeStarted() {
        return this == NOT_STARTED;
    }

    /**
     * Check if this status indicates a positive outcome
     */
    public boolean isPositive() {
        return this == COMPLETED;
    }

    /**
     * Check if this status indicates a negative outcome
     */
    public boolean isNegative() {
        return this == REJECTED || this == WITHDRAWN;
    }

    /**
     * Check if this status indicates the round is paused/delayed
     */
    public boolean isPaused() {
        return this == ON_HOLD;
    }

    /**
     * Get CSS class name for frontend styling
     */
    public String getCssClass() {
        return switch (this) {
            case NOT_STARTED -> "status-not-started";
            case INTERVIEW_SCHEDULED -> "status-scheduled";
            case IN_PROGRESS -> "status-in-progress";
            case UNDER_REVIEW -> "status-under-review";
            case COMPLETED -> "status-completed";
            case ON_HOLD -> "status-on-hold";
            case REJECTED -> "status-rejected";
            case WITHDRAWN -> "status-withdrawn";
        };
    }

    /**
     * Get priority for determining overall candidate status
     * Lower number = higher priority
     */
    public int getPriority() {
        return switch (this) {
            case REJECTED -> 1;      // Highest priority - blocks everything
            case WITHDRAWN -> 2;     // Second highest - candidate left
            case ON_HOLD -> 3;       // Third - process paused
            case IN_PROGRESS -> 4;   // Fourth - active interview
            case INTERVIEW_SCHEDULED -> 5; // Fifth - scheduled
            case UNDER_REVIEW -> 6;  // Sixth - waiting for decision
            case COMPLETED -> 7;     // Seventh - successful completion
            case NOT_STARTED -> 8;   // Lowest - no action yet
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
