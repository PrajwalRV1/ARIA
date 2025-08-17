import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AddCandidatePopupComponent } from '../../components/add-candidate-popup/add-candidate-popup.component';
import { AudioUploadModalComponent } from '../../components/audio-upload-modal/audio-upload-modal.component';
import { Router } from '@angular/router';
import { CandidateService } from '../../services/candidate.service';
import { INTERVIEW_ROUNDS, CANDIDATE_STATUS, STATUS_LABELS, STATUS_CLASSES } from '../../constants/candidate.constants';

interface CandidateCard {
  id: number;
  name: string;
  jobTitle: string;
  experience: string;
  requisitionId?: string;
  status: string;
  interviewRound?: string;
  avatar?: string;
  active?: boolean;
  // Index signature to allow string indexing for dynamic field access
  [key: string]: any;
}

interface Activity {
  id: number;
  text: string;
  time: Date;
  icon: string;
}

/** NOTE: keep locally for now; in real app move to /models */
export interface CandidateNavInfo {
  fullName: string;
  appliedRole: string;
  requisitionId: string;
}

@Component({
  selector: 'app-recruiter-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, AddCandidatePopupComponent, AudioUploadModalComponent],
  templateUrl: './recruiter-dashboard.component.html',
  styleUrls: ['./recruiter-dashboard.component.scss'],
})
export class RecruiterDashboardComponent implements OnInit, OnDestroy {
  constructor(private router: Router, private candidateService: CandidateService) { }

  ngOnInit() {
    // Simulate loading data
    this.loadDashboardData();
    // Initialize recent activities
    this.initializeActivities();
  }

  ngOnDestroy() {
    // Clean up any subscriptions or timers if needed
  }

  // ---------- Loading States ----------
  isLoading = false;
  isProcessing = false;

  private loadDashboardData() {
    this.isLoading = true;
    this.candidateService.getAllCandidates().subscribe({
      next: (candidates) => {
        console.log('Loading dashboard data:', candidates);
        
        // Map backend candidates to card format with proper field mapping
        this.candidates = candidates.map(c => ({
          id: c.id || 0,
          name: c.name || 'Unknown',
          jobTitle: c.appliedRole || 'N/A',
          experience: `${c.totalExperience || 0} yrs`,
          email: c.email || '',
          phone: c.phone || '', // Properly mapped from backend
          status: c.status || 'PENDING', // Ensure valid status
          interviewRound: c.interviewRound || '', // Properly mapped interview round
          requisitionId: c.requisitionId || '',
          profilePictureUrl: c.profilePicUrl || '/assets/images/default-avatar.png',
          resumeUrl: c.resumeUrl || '',
          active: false // Default to inactive
        })) as CandidateCard[];
        
        console.log('Mapped candidates to cards:', this.candidates);
        this.isLoading = false;
        
        // Force UI update to ensure dropdowns reflect latest values
        setTimeout(() => {
          this.updateDropdownValues();
        }, 100);
      },
      error: (err) => {
        console.error('Error loading candidates:', err);
        this.showErrorNotification('Failed to load candidates. Please refresh the page.');
        this.isLoading = false;
      }
    });
  }

  // Helper method to ensure dropdown values are synchronized with data
  private updateDropdownValues() {
    this.candidates.forEach(candidate => {
      // Find and update status dropdowns
      const statusSelect = document.querySelector(`select.status-select[data-candidate-id="${candidate.id}"]`) as HTMLSelectElement;
      if (statusSelect && statusSelect.value !== candidate.status) {
        statusSelect.value = candidate.status;
      }
      
      // Find and update interview round dropdowns
      const roundSelect = document.querySelector(`select.round-select[data-candidate-id="${candidate.id}"]`) as HTMLSelectElement;
      if (roundSelect && roundSelect.value !== (candidate.interviewRound || '')) {
        roundSelect.value = candidate.interviewRound || '';
      }
    });
  }

  // ---------- Add/Edit popup ----------
  showAddCandidatePopup = false;
  popupMode: 'add' | 'edit' = 'add';
  selectedCandidate: any = null;
  
  // ---------- Candidate Selection for Interview Scheduling ----------
  selectedCandidateForInterview: CandidateCard | null = null;
  
  // ---------- Audio Upload Modal ----------
  showAudioUploadModal = false;
  audioUploadCandidate: CandidateCard | null = null;

  openAddCandidate() {
    this.popupMode = 'add';
    this.selectedCandidate = null;
    this.showAddCandidatePopup = true;
  }

  openEditCandidate(candidate: any) {
    this.popupMode = 'edit';
    // Fetch complete candidate data from backend for editing
    if (candidate.id) {
      this.candidateService.getCandidateById(candidate.id).subscribe({
        next: (fullCandidate) => {
          console.log('Fetched complete candidate data:', fullCandidate);
          this.selectedCandidate = fullCandidate;
          this.showAddCandidatePopup = true;
        },
        error: (err) => {
          console.error('Error fetching candidate details:', err);
          // Fallback to using the card data
          this.selectedCandidate = candidate;
          this.showAddCandidatePopup = true;
        }
      });
    } else {
      // Fallback if no ID
      this.selectedCandidate = candidate;
      this.showAddCandidatePopup = true;
    }
  }

  closeAddCandidatePopup() {
    this.showAddCandidatePopup = false;
  }

  onCandidateSaved(payload: any) {  // Handler for both create and update
    console.log('onCandidateSaved called with payload:', payload);
    console.log('Current mode:', this.popupMode);
    console.log('Selected candidate:', this.selectedCandidate);
    
    const resumeFile = payload.uploadResume;  // File object
    const profilePic = payload.uploadPicture;  // File object
    
    console.log('Files - Resume:', resumeFile, 'ProfilePic:', profilePic);

    const candidateData = {
      id: this.popupMode === 'edit' && this.selectedCandidate ? this.selectedCandidate.id : null, // Include ID for updates
      requisitionId: payload.requisitionId,
      name: payload.name,  // Already mapped correctly in popup component
      email: payload.email,  // Already mapped correctly in popup component
      phone: payload.phone,  // Fixed: Change from phoneNumber to phone to match backend
      appliedRole: payload.appliedRole,
      applicationDate: payload.applicationDate,  // Assume YYYY-MM-DD format
      totalExperience: payload.totalExperience,
      relevantExperience: payload.relevantExperience,
      interviewRound: payload.interviewRound,
      status: payload.status,  // Status already handled in popup component
      jobDescription: payload.jobDescription,
      keyResponsibilities: payload.keyResponsibilities,
      // Add defaults for missing backend fields if required (e.g., skills: [], source: null)
      skills: payload.skills || [],
      source: payload.source || null,
      notes: payload.notes || null,
      tags: payload.tags || null,
      recruiterId: payload.recruiterId || null
    };
    
    console.log('Prepared candidate data:', candidateData);

    if (this.popupMode === 'edit' && this.selectedCandidate && this.selectedCandidate.id) {
      // Update existing candidate
      this.candidateService.updateCandidate(this.selectedCandidate.id, candidateData, resumeFile, profilePic).subscribe({
        next: (response) => {
          this.loadDashboardData();  // Refresh data
          this.closeAddCandidatePopup(); // Close popup after successful save
          this.addActivity(`Updated candidate ${candidateData.name}`, 'fas fa-user-edit');
        },
        error: (err) => {
          console.error('Error updating candidate:', err);
        }
      });
    } else {
      // Create new candidate
      this.candidateService.addCandidate(candidateData, resumeFile, profilePic).subscribe({
        next: (response) => {
          this.loadDashboardData();  // Refresh using existing method
          this.closeAddCandidatePopup(); // Close popup after successful save
          this.addActivity(`Added new candidate ${candidateData.name}`, 'fas fa-user-plus');
        },
        error: (err) => {
          console.error('Error saving candidate:', err);
        }
      });
    }
  }

  private mapStatus(frontendStatus: string): string {  // New helper for status mapping
    const statusMap: { [key: string]: string } = {
      'Yet to schedule T1 interview': 'SCREENING',
      'Scheduled': 'TECHNICAL_1',
      'On Hold': 'ON_HOLD',
      'Rejected': 'REJECTED',
      'Selected': 'SELECTED'
    };
    return statusMap[frontendStatus] || 'SCREENING';  // Default to SCREENING
  }

  // ---------- Confirmation Modal ----------
  showConfirmation = false;
  confirmationMessage = '';
  private pendingAction: () => void = () => { };

  private showConfirmationDialog(message: string, action: () => void) {
    this.confirmationMessage = message;
    this.pendingAction = action;
    this.showConfirmation = true;
  }

  closeConfirmation() {
    this.showConfirmation = false;
    this.confirmationMessage = '';
    this.pendingAction = () => { };
  }

  confirmAction() {
    this.pendingAction();
    this.closeConfirmation();
  }

  // ---------- Tabs with Icons ----------
  tabs = [
    { key: 'candidates', label: 'Candidates', icon: 'fas fa-users' },
    { key: 'upload', label: 'Upload Resume', icon: 'fas fa-upload' },
    { key: 'schedule', label: 'Schedule Interview', icon: 'fas fa-calendar-alt' },
  ];
  activeTab = 'candidates';

  setActiveTab(tabKey: string) {
    this.activeTab = tabKey;
    console.log('Tab changed to:', tabKey);
  }

  // ---------- Data ----------
  // Use shared constants for consistency across the application
  interviewRounds = INTERVIEW_ROUNDS;
  statusOptions = CANDIDATE_STATUS;
  statusLabels = STATUS_LABELS;
  statusClasses = STATUS_CLASSES;
  
  candidates: CandidateCard[] = [];
  recentActivities: Activity[] = [];
  filteredCandidates: CandidateCard[] = [];
  searchTerm = '';
  
  // Track pending updates to prevent multiple simultaneous updates
  private pendingUpdates = new Set<number>();

  // ---------- Initialize Activities ----------
  private initializeActivities() {
    this.recentActivities = [
      { id: 1, text: 'Candidate added', time: new Date(), icon: 'fas fa-user-plus' },
      { id: 2, text: 'Interview scheduled', time: new Date(), icon: 'fas fa-calendar-check' },
    ];
  }

  // ---------- Filter and Search ----------
  // Removed duplicate filterCandidates at line 353, keeping the implementation at line 426
  filterCandidates(searchTerm: string): CandidateCard[] {
    if (!searchTerm) return this.candidates;

    const term = searchTerm.toLowerCase();
    return this.candidates.filter(candidate =>
      candidate.name.toLowerCase().includes(term) ||
      candidate.jobTitle.toLowerCase().includes(term) ||
      candidate.requisitionId?.toLowerCase().includes(term) ||
      candidate.status.toLowerCase().includes(term)
    );
  }

  // ---------- Bulk Upload Simulation ----------
  simulateBulkUpload() {
    this.isProcessing = true;
    setTimeout(() => {
      this.isProcessing = false;
      this.addActivity('Bulk upload initiated', 'fas fa-upload');
    }, 1500);
  }

  // ---------- Activity Management ----------
  private addActivity(text: string, icon: string) {
    const newActivity: Activity = {
      id: Date.now(),
      text,
      time: new Date(),
      icon
    };

    this.recentActivities.unshift(newActivity);

    // Keep only the latest 10 activities
    if (this.recentActivities.length > 10) {
      this.recentActivities = this.recentActivities.slice(0, 10);
    }
  }

  // ---------- Additional Helper Methods ----------

  // Method to handle tab-specific actions
  handleTabAction(tabKey: string) {
    switch (tabKey) {
      case 'upload':
        this.openAddCandidate();
        break;
      case 'schedule':
        this.scheduleInterview();
        break;
      default:
        this.setActiveTab(tabKey);
    }
  }

  // Method to get candidate by ID (useful for various operations)
  getCandidateById(id: number): CandidateCard | undefined {
    return this.candidates.find(c => c.id === id);
  }

  // Method to update candidate data (useful for popup callbacks)
  updateCandidate(updatedCandidate: CandidateCard) {
    const index = this.candidates.findIndex(c => c.id === updatedCandidate.id);
    if (index !== -1) {
      this.candidates[index] = { ...updatedCandidate };
      this.addActivity(`Updated ${updatedCandidate.name}'s information`, 'fas fa-user-edit');
    }
  }

  // Method to add new candidate (useful for popup callbacks)
  addNewCandidate(newCandidate: Omit<CandidateCard, 'id'>) {
    const id = Math.max(...this.candidates.map(c => c.id), 0) + 1;
    const candidate = {
      id,
      active: false,
      ...newCandidate // Spread newCandidate after id to ensure all required properties are included
    } as CandidateCard; // Type assertion to resolve TypeScript strict checking

    this.candidates.push(candidate);
    this.addActivity(`Added new candidate ${candidate.name}`, 'fas fa-user-plus');
  }

  // Method to delete candidate (if needed)
  deleteCandidate(candidateId: number) {
    const candidate = this.getCandidateById(candidateId);
    if (candidate) {
      this.showConfirmationDialog(
        `Are you sure you want to delete ${candidate.name}?`,
        () => {
          this.candidates = this.candidates.filter(c => c.id !== candidateId);
          this.addActivity(`Deleted candidate ${candidate.name}`, 'fas fa-user-minus');
        }
      );
    }
  }

  // Method to sort candidates
  sortCandidates(sortBy: 'name' | 'jobTitle' | 'status', order: 'asc' | 'desc' = 'asc'): CandidateCard[] {
    return [...this.candidates].sort((a, b) => {
      let aValue = a[sortBy].toString().toLowerCase();
      let bValue = b[sortBy].toString().toLowerCase();

      if (order === 'asc') {
        return aValue.localeCompare(bValue);
      } else {
        return bValue.localeCompare(aValue);
      }
    });
  }

  // Method to export candidates data (if needed)
  exportCandidatesData() {
    const dataStr = JSON.stringify(this.candidates, null, 2);
    const dataUri = 'data:application/json;charset=utf-8,' + encodeURIComponent(dataStr);

    const exportFileDefaultName = `candidates-${new Date().toISOString().split('T')[0]}.json`;

    const linkElement = document.createElement('a');
    linkElement.setAttribute('href', dataUri);
    linkElement.setAttribute('download', exportFileDefaultName);
    linkElement.click();

    this.addActivity('Exported candidates data', 'fas fa-download');
  }

  // ---------- Schedule Interview Simulation ----------
  public scheduleInterview() {  // Changed from private to public
    this.isProcessing = true;
    setTimeout(() => {
      this.isProcessing = false;
      this.addActivity('Interview scheduled', 'fas fa-calendar-check');
    }, 1500);
  }

  // ---------- New Methods to Fix Template Errors ----------
  getTotalCandidates(): number {  // Retained
    return this.candidates.length;
  }

  getPendingInterviews(): number {  // Retained
    const pendingStatuses = ['PENDING', 'SCHEDULED'];
    return this.candidates.filter(c => pendingStatuses.includes(c.status)).length;
  }

  getCompletedInterviews(): number {  // Retained
    const completedStatuses = ['COMPLETED', 'REJECTED', 'SELECTED'];
    return this.candidates.filter(c => completedStatuses.includes(c.status)).length;
  }

  getHiringInterviews(): number {  // Retained
    const hiringStatuses = ['ON_HOLD'];
    return this.candidates.filter(c => hiringStatuses.includes(c.status)).length;
  }

  // Method to navigate to question bank with selected candidate details
  goToQuestionBank() {
    if (!this.selectedCandidateForInterview) {
      this.showErrorNotification('Please select a candidate first before scheduling an interview.');
      return;
    }

    const candidateNavInfo: CandidateNavInfo = {
      fullName: this.selectedCandidateForInterview.name,
      appliedRole: this.selectedCandidateForInterview.jobTitle,
      requisitionId: this.selectedCandidateForInterview.requisitionId || 'N/A'
    };

    console.log('Navigating to question bank with candidate:', candidateNavInfo);

    // Store candidate data in sessionStorage before navigation
    sessionStorage.setItem('selectedCandidate', JSON.stringify(candidateNavInfo));
    
    // Navigate to question bank dashboard with candidate details
    this.router.navigate(['/question-bank'], {
      queryParams: {
        candidateId: this.selectedCandidateForInterview.id,
        candidateName: candidateNavInfo.fullName
      }
    });

    // Add activity log
    this.addActivity(`Started interview preparation for ${candidateNavInfo.fullName}`, 'fas fa-question-circle');
  }

  getActiveCandidate(): any {  // Stub for template
    return this.candidates.find(c => c.active) || null;
  }

  // Method to select a candidate card for interview scheduling
  selectCard(card: CandidateCard) {
    // Deselect all cards first
    this.candidates.forEach(c => c.active = false);
    
    // Select the clicked card
    card.active = true;
    this.selectedCandidateForInterview = card;
    
    console.log('Selected candidate for interview:', card.name);
    
    // Show a brief success notification to confirm selection
    this.showSuccessNotification(`${card.name} selected for interview scheduling`);
  }

  getInitials(name: string): string {  // Stub for template
    return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
  }

  getStatusClass(status: string): string {
    // Use shared constants for consistency
    return this.statusClasses[status] || 'status-default';
  }

  // Get display label for status
  getStatusLabel(status: string): string {
    return this.statusLabels[status] || status;
  }

  // Enhanced status change with optimistic UI updates and proper error handling
  onStatusChange(card: CandidateCard, event: Event) {
    const newStatus = (event.target as HTMLSelectElement).value;
    const oldStatus = card.status;
    
    // Validate that the new status is different
    if (newStatus === oldStatus) {
      return; // No change needed
    }
    
    // Prevent multiple simultaneous updates for the same candidate
    if (this.pendingUpdates.has(card.id)) {
      console.log('Update already in progress for candidate', card.id);
      // Revert the select to old value
      (event.target as HTMLSelectElement).value = oldStatus;
      return;
    }
    
    // Optimistically update UI immediately for better UX
    card.status = newStatus;
    this.pendingUpdates.add(card.id);
    
    console.log(`Status change initiated for ${card.name}: ${oldStatus} → ${newStatus}`);
    
    // Update backend with proper error handling
    this.updateCandidateFieldOptimistically(card, 'status', newStatus, oldStatus);
  }
  
  // Enhanced interview round change with optimistic UI updates and proper error handling
  onInterviewRoundChange(card: CandidateCard, event: Event) {
    const newRound = (event.target as HTMLSelectElement).value;
    const oldRound = card.interviewRound || '';
    
    // Validate that the new round is different
    if (newRound === oldRound) {
      return; // No change needed
    }
    
    // Prevent multiple simultaneous updates for the same candidate
    if (this.pendingUpdates.has(card.id)) {
      console.log('Update already in progress for candidate', card.id);
      // Revert the select to old value
      (event.target as HTMLSelectElement).value = oldRound;
      return;
    }
    
    // Optimistically update UI immediately for better UX
    card.interviewRound = newRound;
    this.pendingUpdates.add(card.id);
    
    console.log(`Interview round change initiated for ${card.name}: ${oldRound} → ${newRound}`);
    
    // Update backend with proper error handling
    this.updateCandidateFieldOptimistically(card, 'interviewRound', newRound, oldRound);
  }
  
  // Optimistic update method with better error handling and user feedback
  private updateCandidateFieldOptimistically(card: CandidateCard, field: string, newValue: any, oldValue: any) {
    // Get complete candidate data from backend to ensure we have all required fields
    this.candidateService.getCandidateById(card.id).subscribe({
      next: (fullCandidate) => {
        // Create update payload with the changed field
        const updatePayload = {
          ...fullCandidate,
          [field]: newValue
        };
        
        console.log(`Sending update request for ${field}:`, updatePayload);
        
        // Send update request
        this.candidateService.updateCandidate(card.id, updatePayload).subscribe({
          next: (response) => {
            console.log(`✅ Successfully updated ${field} for ${card.name}: ${oldValue} → ${newValue}`);
            
            // Ensure the card reflects the latest backend state
            if (response && response[field] !== undefined) {
              if (field === 'status') {
                card.status = response[field];
              } else if (field === 'interviewRound') {
                card.interviewRound = response[field];
              }
            }
            
            // Add activity and cleanup
            this.addActivity(`Updated ${card.name}'s ${field} to ${this.getDisplayValue(field, newValue)}`, 'fas fa-edit');
            this.pendingUpdates.delete(card.id);
            
            // Show success feedback
            this.showSuccessNotification(`${this.getFieldDisplayName(field)} updated successfully!`);
          },
          error: (err) => {
            console.error(`❌ Error updating ${field} for ${card.name}:`, err);
            
            // Revert optimistic UI change on error
            this.revertFieldChange(card, field, oldValue);
            
            // Show user-friendly error message
            const errorMessage = this.getErrorMessage(err, field);
            this.showErrorNotification(errorMessage);
            
            // Cleanup pending state
            this.pendingUpdates.delete(card.id);
          }
        });
      },
      error: (err) => {
        console.error('❌ Error fetching candidate details for update:', err);
        
        // Revert optimistic UI change on fetch error
        this.revertFieldChange(card, field, oldValue);
        
        this.showErrorNotification('Failed to fetch latest candidate data. Please refresh and try again.');
        this.pendingUpdates.delete(card.id);
      }
    });
  }

  // Helper method to revert field changes
  private revertFieldChange(card: CandidateCard, field: string, oldValue: any) {
    if (field === 'status') {
      card.status = oldValue;
    } else if (field === 'interviewRound') {
      card.interviewRound = oldValue;
    }
    
    // Force UI update by finding and updating the select element
    setTimeout(() => {
      const selectElement = document.querySelector(`select[value="${card[field]}"]`) as HTMLSelectElement;
      if (selectElement) {
        selectElement.value = oldValue;
      }
    }, 0);
  }

  // Helper method to get user-friendly field display names
  private getFieldDisplayName(field: string): string {
    const fieldNames: { [key: string]: string } = {
      'status': 'Status',
      'interviewRound': 'Interview Round'
    };
    return fieldNames[field] || field;
  }

  // Helper method to get display values for fields
  private getDisplayValue(field: string, value: any): string {
    if (field === 'status') {
      return this.getStatusLabel(value);
    }
    return value || 'Not Set';
  }

  // Helper method to extract user-friendly error messages
  private getErrorMessage(error: any, field: string): string {
    // If error has user-friendly message from service, use it
    if (error.user) {
      return error.user;
    }
    
    // Fallback to status-based messages
    if (error.status === 404) {
      return 'Candidate not found. The record may have been deleted.';
    } else if (error.status === 400) {
      return `Invalid ${this.getFieldDisplayName(field).toLowerCase()} value. Please try again.`;
    } else if (error.status === 500) {
      return 'Server error occurred. Please try again later.';
    } else if (error.status === 0) {
      return 'Network error. Please check your connection.';
    }
    return `Failed to update ${this.getFieldDisplayName(field).toLowerCase()}. Please try again.`;
  }

  // Helper method to check if a candidate is currently being updated
  isUpdatingCandidate(candidateId: number): boolean {
    return this.pendingUpdates.has(candidateId);
  }
  
  // Simple error notification method (could be replaced with a proper toast/notification service)
  private showErrorNotification(message: string) {
    // For now, show in console and could be enhanced with a proper notification system
    console.error('Error notification:', message);
    alert(`❌ ${message}`); // Temporary solution - replace with proper toast notification
  }

  // Simple success notification method (could be replaced with a proper toast/notification service)
  private showSuccessNotification(message: string) {
    console.log('Success notification:', message);
    // Show a brief success message - in a real app, this would be a toast notification
    // For now, using a temporary approach
    const notification = document.createElement('div');
    notification.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background-color: #4CAF50;
      color: white;
      padding: 12px 24px;
      border-radius: 4px;
      z-index: 10000;
      box-shadow: 0 4px 8px rgba(0,0,0,0.1);
      font-family: Arial, sans-serif;
    `;
    notification.innerHTML = `✅ ${message}`;
    document.body.appendChild(notification);
    
    // Auto-remove after 3 seconds
    setTimeout(() => {
      if (notification.parentNode) {
        notification.parentNode.removeChild(notification);
      }
    }, 3000);
  }

  editDetails(card: CandidateCard) {  // Stub for template
    console.log('Editing details for:', card);
    this.openEditCandidate(card);
    // Implement edit logic
  }

  openFeedback(card: CandidateCard) {  // Stub for template
    console.log('Opening feedback for:', card);
    // Implement feedback popup logic
  }

  quickAction(action: string) {  // Stub for template
    console.log('Quick action triggered:', action);
    if (action === 'bulk-upload') this.simulateBulkUpload();
    // Implement additional action logic
  }

  getHiredCandidates(): number {  // Added for template
    return this.getCompletedInterviews(); // Assuming hired = selected candidates
  }

  // ---------- Audio Upload Methods ----------
  
  // Method to open the audio upload modal for a specific candidate
  openAudioUpload(card: CandidateCard) {
    console.log('Opening audio upload modal for:', card.name);
    this.audioUploadCandidate = card;
    this.showAudioUploadModal = true;
  }

  // Method to close the audio upload modal
  closeAudioUpload() {
    this.showAudioUploadModal = false;
    this.audioUploadCandidate = null;
  }

  // Method to handle audio file upload
  onAudioUpload(payload: {candidateId: number, audioFile: File}) {
    console.log('Audio upload initiated for candidate:', payload.candidateId);
    console.log('Audio file:', payload.audioFile);
    
    // Upload the audio file using the candidate service
    this.candidateService.uploadAudio(payload.candidateId, payload.audioFile).subscribe({
      next: (response) => {
        console.log('✅ Audio uploaded successfully:', response);
        
        // Update the candidate card if needed
        const candidate = this.getCandidateById(payload.candidateId);
        if (candidate) {
          // Add activity log
          this.addActivity(`Uploaded phone screening audio for ${candidate.name}`, 'fas fa-microphone');
        }
        
        // Show success notification
        this.showSuccessNotification('Phone screening audio uploaded successfully!');
        
        // Close the modal
        this.closeAudioUpload();
      },
      error: (err) => {
        console.error('❌ Error uploading audio:', err);
        
        // Show error notification
        this.showErrorNotification('Failed to upload audio. Please try again.');
        
        // Reset upload state in the modal component (if it has this method)
        // Note: We'll need to add a reference to the modal component for this
      }
    });
  }
}
