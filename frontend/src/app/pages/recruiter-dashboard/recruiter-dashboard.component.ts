import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AddCandidatePopupComponent } from '../../components/add-candidate-popup/add-candidate-popup.component';
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
  imports: [CommonModule, FormsModule, AddCandidatePopupComponent],
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
        this.candidates = candidates.map(c => ({
          id: c.id,
          name: c.name,
          jobTitle: c.appliedRole,
          experience: `${c.totalExperience} yrs`,
          email: c.email,
          phone: c.phone, // Fixed: Change from phoneNumber to phone
          status: c.status,
          interviewRound: c.interviewRound, // Add interview round mapping
          requisitionId: c.requisitionId, // Fixed: Add missing mapping
          profilePictureUrl: c.profilePicUrl || '/assets/images/default-avatar.png', // Fixed: Match backend field name
          resumeUrl: c.resumeUrl || ''
        })) as CandidateCard[];
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error loading candidates', err);
        this.isLoading = false;
      }
    });
  }

  // ---------- Add/Edit popup ----------
  showAddCandidatePopup = false;
  popupMode: 'add' | 'edit' = 'add';
  selectedCandidate: any = null;

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
    const candidate: CandidateCard = {
      ...newCandidate,
      id,
      active: false
    };

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

  goToQuestionBank(candidate: any) {  // Stub for template
    console.log('Navigating to question bank for:', candidate);
    // Implement navigation logic (e.g., this.router.navigate([...]))
  }

  getActiveCandidate(): any {  // Stub for template
    return this.candidates.find(c => c.active) || null;
  }

  selectCard(card: CandidateCard) {  // Stub for template
    console.log('Selected card:', card);
    // Implement card selection logic
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

  // Enhanced status change with inline update and error handling
  onStatusChange(card: CandidateCard, event: Event) {
    const newStatus = (event.target as HTMLSelectElement).value;
    const oldStatus = card.status;
    
    // Prevent multiple simultaneous updates for the same candidate
    if (this.pendingUpdates.has(card.id)) {
      console.log('Update already in progress for candidate', card.id);
      // Revert the select to old value
      (event.target as HTMLSelectElement).value = oldStatus;
      return;
    }
    
    // Immediately update UI for better UX
    card.status = newStatus;
    this.pendingUpdates.add(card.id);
    
    console.log('Status changed for', card.name, 'from', oldStatus, 'to', newStatus);
    
    // Create minimal update payload
    this.updateCandidateField(card, 'status', newStatus, oldStatus);
  }
  
  // Enhanced interview round change with inline update and error handling
  onInterviewRoundChange(card: CandidateCard, event: Event) {
    const newRound = (event.target as HTMLSelectElement).value;
    const oldRound = card.interviewRound || '';
    
    // Prevent multiple simultaneous updates for the same candidate
    if (this.pendingUpdates.has(card.id)) {
      console.log('Update already in progress for candidate', card.id);
      // Revert the select to old value
      (event.target as HTMLSelectElement).value = oldRound;
      return;
    }
    
    // Immediately update UI for better UX
    (card as any).interviewRound = newRound;
    this.pendingUpdates.add(card.id);
    
    console.log('Interview round changed for', card.name, 'from', oldRound, 'to', newRound);
    
    // Create minimal update payload
    this.updateCandidateField(card, 'interviewRound', newRound, oldRound);
  }
  
  // Generic method to update a single field with error handling
  private updateCandidateField(card: CandidateCard, field: string, newValue: any, oldValue: any) {
    // First, get complete candidate data from backend to ensure we have all required fields
    this.candidateService.getCandidateById(card.id).subscribe({
      next: (fullCandidate) => {
        // Create update payload with the changed field
        const updatePayload = {
          ...fullCandidate,
          [field]: newValue
        };
        
        // Send update request
        this.candidateService.updateCandidate(card.id, updatePayload).subscribe({
          next: (response) => {
            console.log(`Successfully updated ${field} for candidate`, card.name);
            this.addActivity(`Updated ${card.name}'s ${field} to ${newValue}`, 'fas fa-edit');
            this.pendingUpdates.delete(card.id);
          },
          error: (err) => {
            console.error(`Error updating ${field} for candidate`, card.name, err);
            
            // Revert UI change on error
            if (field === 'status') {
              card.status = oldValue;
            } else if (field === 'interviewRound') {
              (card as any).interviewRound = oldValue;
            }
            
            // Show error message to user
            this.showErrorNotification(`Failed to update ${field}. Please try again.`);
            this.pendingUpdates.delete(card.id);
          }
        });
      },
      error: (err) => {
        console.error('Error fetching candidate details for update:', err);
        
        // Revert UI change on error
        if (field === 'status') {
          card.status = oldValue;
        } else if (field === 'interviewRound') {
          (card as any).interviewRound = oldValue;
        }
        
        this.showErrorNotification('Failed to fetch candidate details. Please try again.');
        this.pendingUpdates.delete(card.id);
      }
    });
  }
  
  // Simple error notification method (could be replaced with a proper toast/notification service)
  private showErrorNotification(message: string) {
    // For now, show in console and could be enhanced with a proper notification system
    console.error('Error notification:', message);
    alert(message); // Temporary solution - replace with proper toast notification
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
}