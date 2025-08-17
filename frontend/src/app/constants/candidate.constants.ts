// Shared constants for candidate-related dropdown options
// This ensures consistency across the application

export const INTERVIEW_ROUNDS = [
  'Screening',
  'Technical - T1', 
  'Technical - T2',
  'HR Round',
  'Managerial Round'
];

export const CANDIDATE_STATUS = [
  'PENDING',     // Initial status when candidate is added
  'SCHEDULED',   // When interview is scheduled
  'COMPLETED',   // When interview is completed
  'SELECTED',    // When candidate is selected
  'REJECTED',    // When candidate is rejected
  'ON_HOLD'      // When candidate is put on hold
];

// Display labels for status (for better UX)
export const STATUS_LABELS: { [key: string]: string } = {
  'PENDING': 'Pending',
  'SCHEDULED': 'Scheduled',
  'COMPLETED': 'Completed',
  'SELECTED': 'Selected',
  'REJECTED': 'Rejected',
  'ON_HOLD': 'On Hold'
};

// CSS classes for different status values
export const STATUS_CLASSES: { [key: string]: string } = {
  'PENDING': 'status-pending',
  'SCHEDULED': 'status-scheduled', 
  'COMPLETED': 'status-completed',
  'SELECTED': 'status-selected',
  'REJECTED': 'status-rejected',
  'ON_HOLD': 'status-on-hold'
};
