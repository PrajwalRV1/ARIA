// Status transition utility to match backend validation logic
// This ensures frontend UI shows only valid status transitions

export type CandidateStatus = 
  | 'PENDING'
  | 'APPLIED' 
  | 'INTERVIEW_SCHEDULED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'UNDER_REVIEW'
  | 'SELECTED'
  | 'REJECTED'
  | 'ON_HOLD'
  | 'WITHDRAWN';

/**
 * Get valid next statuses for a given current status
 * This matches the backend business logic in Candidate.canTransitionTo()
 */
export function getValidNextStatuses(currentStatus: CandidateStatus): CandidateStatus[] {
  switch (currentStatus) {
    case 'PENDING':
      return ['APPLIED', 'REJECTED', 'WITHDRAWN'];
      
    case 'APPLIED':
      return ['INTERVIEW_SCHEDULED', 'REJECTED', 'ON_HOLD', 'WITHDRAWN'];
      
    case 'INTERVIEW_SCHEDULED':
      return ['IN_PROGRESS', 'REJECTED', 'ON_HOLD', 'WITHDRAWN'];
      
    case 'IN_PROGRESS':
      return ['COMPLETED', 'REJECTED', 'ON_HOLD'];
      
    case 'COMPLETED':
      return ['UNDER_REVIEW'];
      
    case 'UNDER_REVIEW':
      return ['SELECTED', 'REJECTED', 'ON_HOLD'];
      
    case 'ON_HOLD':
      return ['APPLIED', 'INTERVIEW_SCHEDULED', 'REJECTED', 'WITHDRAWN'];
      
    case 'SELECTED':
    case 'REJECTED':
    case 'WITHDRAWN':
      return []; // Terminal states - no further transitions
      
    default:
      return [];
  }
}

/**
 * Check if a status transition is valid
 */
export function isValidStatusTransition(from: CandidateStatus, to: CandidateStatus): boolean {
  const validNextStatuses = getValidNextStatuses(from);
  return validNextStatuses.includes(to);
}

/**
 * Get all statuses that are terminal (no further transitions possible)
 */
export function getTerminalStatuses(): CandidateStatus[] {
  return ['SELECTED', 'REJECTED', 'WITHDRAWN'];
}

/**
 * Check if a status is terminal
 */
export function isTerminalStatus(status: CandidateStatus): boolean {
  return getTerminalStatuses().includes(status);
}

/**
 * Get the recommended next status for a given current status (first in the flow)
 */
export function getRecommendedNextStatus(currentStatus: CandidateStatus): CandidateStatus | null {
  const validStatuses = getValidNextStatuses(currentStatus);
  
  if (validStatuses.length === 0) {
    return null; // Terminal status
  }
  
  // Return the first "positive flow" status (not rejection/hold/withdrawal)
  const positiveFlow: CandidateStatus[] = ['APPLIED', 'INTERVIEW_SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'UNDER_REVIEW', 'SELECTED'];
  
  for (const status of positiveFlow) {
    if (validStatuses.includes(status)) {
      return status;
    }
  }
  
  // If no positive flow status available, return first valid status
  return validStatuses[0];
}

/**
 * Get human-readable description of why a transition is invalid
 */
export function getTransitionErrorMessage(from: CandidateStatus, to: CandidateStatus): string {
  if (isValidStatusTransition(from, to)) {
    return '';
  }
  
  if (isTerminalStatus(from)) {
    return `Cannot change status from ${from} as it is a final status.`;
  }
  
  const validOptions = getValidNextStatuses(from);
  const validLabels = validOptions.map(status => status.replace(/_/g, ' ').toLowerCase());
  
  return `Cannot transition from ${from.replace(/_/g, ' ').toLowerCase()} to ${to.replace(/_/g, ' ').toLowerCase()}. Valid options: ${validLabels.join(', ')}.`;
}

/**
 * Status flow visualization for debugging/documentation
 */
export const STATUS_FLOW_DIAGRAM = `
Status Transition Flow:
PENDING → APPLIED → INTERVIEW_SCHEDULED → IN_PROGRESS → COMPLETED → UNDER_REVIEW → SELECTED
   ↓         ↓              ↓                   ↓             ↓              ↓
REJECTED  REJECTED      REJECTED           REJECTED      REJECTED      REJECTED
   ↓         ↓              ↓                   ↓             ↓              ↓
ON_HOLD   ON_HOLD       ON_HOLD            ON_HOLD       ON_HOLD       ON_HOLD
   ↓         ↓              ↓
WITHDRAWN WITHDRAWN    WITHDRAWN

Terminal States: SELECTED, REJECTED, WITHDRAWN
`;
