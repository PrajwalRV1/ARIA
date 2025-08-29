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
  'PENDING',     // Initial status when candidate is added
  'APPLIED',     // When candidate has applied but not yet screened
  'SCREENING',   // During initial screening phase
  'SCHEDULED',   // When interview is scheduled
  'COMPLETED',   // When interview is completed
  'SELECTED',    // When candidate is selected
  'REJECTED',    // When candidate is rejected
  'ON_HOLD'      // When candidate is put on hold
];

// Display labels for status (for better UX)
export const STATUS_LABELS: { [key: string]: string } = {
  'PENDING': 'Pending',
  'APPLIED': 'Applied',
  'SCREENING': 'Screening',
  'SCHEDULED': 'Scheduled',
  'COMPLETED': 'Completed',
  'SELECTED': 'Selected',
  'REJECTED': 'Rejected',
  'ON_HOLD': 'On Hold'
};

// CSS classes for different status values
export const STATUS_CLASSES: { [key: string]: string } = {
  'PENDING': 'status-pending',
  'APPLIED': 'status-applied',
  'SCREENING': 'status-screening',
  'SCHEDULED': 'status-scheduled', 
  'COMPLETED': 'status-completed',
  'SELECTED': 'status-selected',
  'REJECTED': 'status-rejected',
  'ON_HOLD': 'status-on-hold'
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
