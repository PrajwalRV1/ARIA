package com.company.user.model;

/**
 * Enumeration of difficulty levels aligned with IRT theta scale
 */
public enum DifficultyLevel {
    
    /**
     * Very Easy questions - suitable for screening basic knowledge
     * IRT Difficulty Parameter: -3.0 to -2.0
     */
    VERY_EASY("Very Easy", -2.5, -3.0, -2.0),
    
    /**
     * Easy questions - basic concepts and fundamentals
     * IRT Difficulty Parameter: -2.0 to -1.0
     */
    EASY("Easy", -1.5, -2.0, -1.0),
    
    /**
     * Medium questions - intermediate knowledge and application
     * IRT Difficulty Parameter: -1.0 to 1.0
     */
    MEDIUM("Medium", 0.0, -1.0, 1.0),
    
    /**
     * Hard questions - advanced concepts and complex problem solving
     * IRT Difficulty Parameter: 1.0 to 2.0
     */
    HARD("Hard", 1.5, 1.0, 2.0),
    
    /**
     * Very Hard questions - expert level, complex system design
     * IRT Difficulty Parameter: 2.0 to 3.0
     */
    VERY_HARD("Very Hard", 2.5, 2.0, 3.0),
    
    /**
     * Expert questions - cutting-edge technology and research
     * IRT Difficulty Parameter: 3.0 to 4.0
     */
    EXPERT("Expert", 3.5, 3.0, 4.0);

    private final String displayName;
    private final double defaultDifficultyParameter;
    private final double minDifficultyParameter;
    private final double maxDifficultyParameter;

    DifficultyLevel(String displayName, double defaultDifficultyParameter, 
                   double minDifficultyParameter, double maxDifficultyParameter) {
        this.displayName = displayName;
        this.defaultDifficultyParameter = defaultDifficultyParameter;
        this.minDifficultyParameter = minDifficultyParameter;
        this.maxDifficultyParameter = maxDifficultyParameter;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getDefaultDifficultyParameter() {
        return defaultDifficultyParameter;
    }

    public double getMinDifficultyParameter() {
        return minDifficultyParameter;
    }

    public double getMaxDifficultyParameter() {
        return maxDifficultyParameter;
    }

    /**
     * Get difficulty level from string value
     */
    public static DifficultyLevel fromString(String value) {
        for (DifficultyLevel level : DifficultyLevel.values()) {
            if (level.name().equalsIgnoreCase(value) || 
                level.getDisplayName().equalsIgnoreCase(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown difficulty level: " + value);
    }

    /**
     * Get difficulty level from IRT difficulty parameter
     */
    public static DifficultyLevel fromDifficultyParameter(double difficultyParameter) {
        for (DifficultyLevel level : DifficultyLevel.values()) {
            if (difficultyParameter >= level.getMinDifficultyParameter() && 
                difficultyParameter <= level.getMaxDifficultyParameter()) {
                return level;
            }
        }
        // Default to MEDIUM if parameter is out of range
        return MEDIUM;
    }

    /**
     * Get the next higher difficulty level
     */
    public DifficultyLevel getNextLevel() {
        switch (this) {
            case VERY_EASY:
                return EASY;
            case EASY:
                return MEDIUM;
            case MEDIUM:
                return HARD;
            case HARD:
                return VERY_HARD;
            case VERY_HARD:
                return EXPERT;
            case EXPERT:
                return EXPERT; // Already at maximum
            default:
                return MEDIUM;
        }
    }

    /**
     * Get the next lower difficulty level
     */
    public DifficultyLevel getPreviousLevel() {
        switch (this) {
            case EXPERT:
                return VERY_HARD;
            case VERY_HARD:
                return HARD;
            case HARD:
                return MEDIUM;
            case MEDIUM:
                return EASY;
            case EASY:
                return VERY_EASY;
            case VERY_EASY:
                return VERY_EASY; // Already at minimum
            default:
                return MEDIUM;
        }
    }

    /**
     * Check if this difficulty level is suitable for a given candidate theta level
     */
    public boolean isSuitableForTheta(double thetaLevel) {
        // Question is most informative when difficulty parameter is close to theta
        double difference = Math.abs(this.defaultDifficultyParameter - thetaLevel);
        return difference <= 1.0; // Within 1 logit unit
    }

    /**
     * Get expected success rate for a candidate at given theta level
     */
    public double getExpectedSuccessRate(double thetaLevel) {
        // Using 2PL IRT model with default discrimination of 1.0
        double exponent = 1.0 * (thetaLevel - this.defaultDifficultyParameter);
        return Math.exp(exponent) / (1 + Math.exp(exponent));
    }

    /**
     * Get color code for UI representation
     */
    public String getColorCode() {
        switch (this) {
            case VERY_EASY:
                return "#4CAF50"; // Green
            case EASY:
                return "#8BC34A"; // Light Green
            case MEDIUM:
                return "#FFC107"; // Amber
            case HARD:
                return "#FF9800"; // Orange
            case VERY_HARD:
                return "#F44336"; // Red
            case EXPERT:
                return "#9C27B0"; // Purple
            default:
                return "#757575"; // Grey
        }
    }

    /**
     * Get icon name for UI representation
     */
    public String getIconName() {
        switch (this) {
            case VERY_EASY:
                return "sentiment_very_satisfied";
            case EASY:
                return "sentiment_satisfied";
            case MEDIUM:
                return "sentiment_neutral";
            case HARD:
                return "sentiment_dissatisfied";
            case VERY_HARD:
                return "sentiment_very_dissatisfied";
            case EXPERT:
                return "psychology";
            default:
                return "help";
        }
    }

    /**
     * Get numerical value for sorting and comparison
     */
    public int getNumericalValue() {
        return this.ordinal() + 1; // 1-6 scale
    }

    /**
     * Get target interview stage for this difficulty level
     */
    public String getTargetStage() {
        switch (this) {
            case VERY_EASY:
            case EASY:
                return "screening";
            case MEDIUM:
                return "first_round";
            case HARD:
                return "technical_round";
            case VERY_HARD:
            case EXPERT:
                return "final_round";
            default:
                return "first_round";
        }
    }

    /**
     * Get recommended number of questions for this difficulty level
     */
    public int getRecommendedQuestionCount() {
        switch (this) {
            case VERY_EASY:
                return 8; // Quick screening
            case EASY:
                return 6; // Basic assessment
            case MEDIUM:
                return 4; // Standard evaluation
            case HARD:
                return 3; // In-depth assessment
            case VERY_HARD:
                return 2; // Complex problem solving
            case EXPERT:
                return 1; // Single comprehensive question
            default:
                return 4;
        }
    }

    /**
     * Check if this difficulty level is appropriate for a job role
     */
    public boolean isAppropriateForRole(String jobRole) {
        String role = jobRole.toLowerCase();
        
        switch (this) {
            case VERY_EASY:
            case EASY:
                return role.contains("intern") || role.contains("junior") || role.contains("entry");
            case MEDIUM:
                return role.contains("mid") || role.contains("associate") || 
                       (!role.contains("senior") && !role.contains("lead") && !role.contains("principal"));
            case HARD:
                return role.contains("senior") || role.contains("lead");
            case VERY_HARD:
            case EXPERT:
                return role.contains("principal") || role.contains("architect") || 
                       role.contains("staff") || role.contains("director");
            default:
                return true;
        }
    }
}
