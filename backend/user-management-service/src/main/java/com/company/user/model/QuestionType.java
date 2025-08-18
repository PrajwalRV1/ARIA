package com.company.user.model;

/**
 * Enumeration of different question types supported in ARIA
 */
public enum QuestionType {
    
    /**
     * Multiple choice questions with single correct answer
     */
    MULTIPLE_CHOICE("Multiple Choice"),
    
    /**
     * Multiple select questions with multiple correct answers
     */
    MULTIPLE_SELECT("Multiple Select"),
    
    /**
     * Open-ended text questions requiring written responses
     */
    OPEN_ENDED("Open Ended"),
    
    /**
     * Coding questions requiring code implementation
     */
    CODING("Coding"),
    
    /**
     * True/False questions
     */
    TRUE_FALSE("True/False"),
    
    /**
     * Fill in the blank questions
     */
    FILL_IN_BLANK("Fill in the Blank"),
    
    /**
     * Scenario-based questions with context
     */
    SCENARIO("Scenario"),
    
    /**
     * Behavioral questions focusing on soft skills
     */
    BEHAVIORAL("Behavioral"),
    
    /**
     * Technical questions focusing on specific technologies
     */
    TECHNICAL("Technical"),
    
    /**
     * System design questions for architecture discussions
     */
    SYSTEM_DESIGN("System Design"),
    
    /**
     * Problem-solving questions requiring analytical thinking
     */
    PROBLEM_SOLVING("Problem Solving"),
    
    /**
     * Communication questions to assess verbal skills
     */
    COMMUNICATION("Communication");

    private final String displayName;

    QuestionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get question type from string value
     */
    public static QuestionType fromString(String value) {
        for (QuestionType type : QuestionType.values()) {
            if (type.name().equalsIgnoreCase(value) || 
                type.getDisplayName().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown question type: " + value);
    }

    /**
     * Check if question type requires coding evaluation
     */
    public boolean requiresCoding() {
        return this == CODING || this == SYSTEM_DESIGN;
    }

    /**
     * Check if question type has predefined options
     */
    public boolean hasOptions() {
        return this == MULTIPLE_CHOICE || this == MULTIPLE_SELECT || this == TRUE_FALSE;
    }

    /**
     * Check if question type is subjective
     */
    public boolean isSubjective() {
        return this == OPEN_ENDED || this == BEHAVIORAL || this == COMMUNICATION || 
               this == SCENARIO || this == SYSTEM_DESIGN;
    }

    /**
     * Check if question type is technical
     */
    public boolean isTechnical() {
        return this == CODING || this == TECHNICAL || this == SYSTEM_DESIGN || this == PROBLEM_SOLVING;
    }

    /**
     * Get expected response format for this question type
     */
    public String getExpectedResponseFormat() {
        switch (this) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                return "single_option";
            case MULTIPLE_SELECT:
                return "multiple_options";
            case CODING:
                return "code";
            case OPEN_ENDED:
            case BEHAVIORAL:
            case COMMUNICATION:
            case SCENARIO:
            case SYSTEM_DESIGN:
            case PROBLEM_SOLVING:
                return "text";
            case FILL_IN_BLANK:
                return "text_with_blanks";
            default:
                return "text";
        }
    }

    /**
     * Get typical time allocation for this question type (in minutes)
     */
    public int getTypicalTimeAllocation() {
        switch (this) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                return 2;
            case MULTIPLE_SELECT:
            case FILL_IN_BLANK:
                return 3;
            case TECHNICAL:
            case PROBLEM_SOLVING:
                return 5;
            case OPEN_ENDED:
            case BEHAVIORAL:
            case COMMUNICATION:
                return 8;
            case SCENARIO:
                return 10;
            case CODING:
                return 20;
            case SYSTEM_DESIGN:
                return 30;
            default:
                return 5;
        }
    }

    /**
     * Get IRT model recommendation for this question type
     */
    public String getRecommendedIRTModel() {
        switch (this) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                return "3PL"; // 3-Parameter Logistic (with guessing)
            case MULTIPLE_SELECT:
                return "GRM"; // Graded Response Model
            case CODING:
            case SYSTEM_DESIGN:
                return "GRM"; // Graded Response Model for partial credit
            case OPEN_ENDED:
            case BEHAVIORAL:
            case COMMUNICATION:
            case SCENARIO:
            case PROBLEM_SOLVING:
                return "2PL"; // 2-Parameter Logistic (no guessing)
            default:
                return "2PL";
        }
    }
}
