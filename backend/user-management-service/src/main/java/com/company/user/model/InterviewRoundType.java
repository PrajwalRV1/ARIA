package com.company.user.model;

/**
 * Enum representing the standard interview round types.
 * These match exactly with the requirements: Screening, Technical T1/T2, HR, Managerial.
 */
public enum InterviewRoundType {
    SCREENING("Screening", 1),
    TECHNICAL_T1("Technical - T1", 2),
    TECHNICAL_T2("Technical - T2", 3),
    HR_ROUND("HR - Round", 4),
    MANAGERIAL_ROUND("Managerial Round", 5);

    private final String displayName;
    private final int defaultOrder;

    InterviewRoundType(String displayName, int defaultOrder) {
        this.displayName = displayName;
        this.defaultOrder = defaultOrder;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultOrder() {
        return defaultOrder;
    }

    /**
     * Get the InterviewRoundType by its order
     */
    public static InterviewRoundType getByOrder(int order) {
        for (InterviewRoundType type : values()) {
            if (type.defaultOrder == order) {
                return type;
            }
        }
        throw new IllegalArgumentException("No InterviewRoundType with order: " + order);
    }

    /**
     * Check if this round is a technical round
     */
    public boolean isTechnical() {
        return this == TECHNICAL_T1 || this == TECHNICAL_T2;
    }

    /**
     * Check if this round typically requires coding assessment
     */
    public boolean requiresCoding() {
        return isTechnical();
    }

    /**
     * Get the next round type in the sequence, or null if this is the last round
     */
    public InterviewRoundType getNextRound() {
        if (defaultOrder < 5) {
            return getByOrder(defaultOrder + 1);
        }
        return null;
    }

    /**
     * Get the previous round type in the sequence, or null if this is the first round
     */
    public InterviewRoundType getPreviousRound() {
        if (defaultOrder > 1) {
            return getByOrder(defaultOrder - 1);
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
