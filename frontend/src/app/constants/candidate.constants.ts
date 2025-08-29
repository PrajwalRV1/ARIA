// Shared constants for candidate-related dropdown options
// This ensures consistency across the application and backend integration

// Interview rounds as strings (backend uses String field, not enum)
export const INTERVIEW_ROUNDS = [
  'Screening',
  'Technical - T1', 
  'Technical - T2',
  'HR Round',
  'Managerial Round'
];

// Candidate status enum values - MUST match backend CandidateStatus enum exactly
export const CANDIDATE_STATUS = [
  'PENDING',        // Initial status when candidate is added
  'APPLIED',        // When candidate has applied but not yet screened
  'SCREENING',      // During initial screening phase
  'INTERVIEW',      // During interview process
  'SCHEDULED',      // When interview is scheduled
  'IN_PROGRESS',    // Interview in progress
  'COMPLETED',      // When interview is completed
  'UNDER_REVIEW',   // Under review by hiring team
  'SELECTED',       // When candidate is selected
  'REJECTED',       // When candidate is rejected
  'ON_HOLD',        // When candidate is put on hold
  'WITHDRAWN'       // When candidate withdraws
];

// Display labels for status (for better UX)
export const STATUS_LABELS: { [key: string]: string } = {
  'PENDING': 'Pending',
  'APPLIED': 'Applied',
  'SCREENING': 'Screening',
  'INTERVIEW': 'Interview',
  'SCHEDULED': 'Scheduled',
  'IN_PROGRESS': 'In Progress',
  'COMPLETED': 'Completed',
  'UNDER_REVIEW': 'Under Review',
  'SELECTED': 'Selected',
  'REJECTED': 'Rejected',
  'ON_HOLD': 'On Hold',
  'WITHDRAWN': 'Withdrawn'
};

// CSS classes for different status values
export const STATUS_CLASSES: { [key: string]: string } = {
  'PENDING': 'status-pending',
  'APPLIED': 'status-applied',
  'SCREENING': 'status-screening',
  'INTERVIEW': 'status-interview',
  'SCHEDULED': 'status-scheduled',
  'IN_PROGRESS': 'status-in-progress',
  'COMPLETED': 'status-completed',
  'UNDER_REVIEW': 'status-under-review',
  'SELECTED': 'status-selected',
  'REJECTED': 'status-rejected',
  'ON_HOLD': 'status-on-hold',
  'WITHDRAWN': 'status-withdrawn'
};

// Validation constants
export const VALIDATION_MESSAGES = {
  REQUIRED: 'This field is required',
  EMAIL: 'Please enter a valid email address',
  PHONE: 'Please enter a valid phone number',
  MIN_EXPERIENCE: 'Experience cannot be negative',
  FILE_REQUIRED: 'Please select a file',
  FILE_SIZE_LIMIT: 'File size cannot exceed 5MB',
  INVALID_FILE_TYPE: 'Invalid file type'
};

// File validation constants
export const FILE_CONSTRAINTS = {
  RESUME: {
    MAX_SIZE: 5 * 1024 * 1024, // 5MB
    ALLOWED_TYPES: ['.pdf', '.doc', '.docx']
  },
  PROFILE_PIC: {
    MAX_SIZE: 2 * 1024 * 1024, // 2MB
    ALLOWED_TYPES: ['.jpg', '.jpeg', '.png', '.gif']
  }
};
