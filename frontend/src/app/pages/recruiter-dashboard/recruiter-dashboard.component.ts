import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AddCandidatePopupComponent } from '../../components/add-candidate-popup/add-candidate-popup.component';
import { AudioUploadModalComponent } from '../../components/audio-upload-modal/audio-upload-modal.component';
import { ToastComponent } from '../../components/toast/toast.component';
import { Router } from '@angular/router';
import { CandidateService } from '../../services/candidate.service';
import { InterviewService, InterviewScheduleRequest } from '../../services/interview.service';
import { SessionService } from '../../services/session.service';
import { ToastService } from '../../services/toast.service';
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
  imports: [CommonModule, FormsModule, AddCandidatePopupComponent, AudioUploadModalComponent, ToastComponent],
  templateUrl: './recruiter-dashboard.component.html',
  styleUrls: ['./recruiter-dashboard.component.scss'],
})
export class RecruiterDashboardComponent implements OnInit, OnDestroy {
  constructor(
    private router: Router, 
    private candidateService: CandidateService,
    private interviewService: InterviewService,
    private sessionService: SessionService,
    private toastService: ToastService
  ) { }

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

  private loadDashboardData(forceRefresh: boolean = false) {
    console.log('🔄 Starting loadDashboardData() - Dashboard refresh initiated');
    this.isLoading = true;
    
    // Clear cache if force refresh is requested
    if (forceRefresh) {
      console.log('📋 Force refresh requested - clearing all caches');
      this.candidateService.clearAllCache();
    }
    
    // Store current candidates for comparison
    const previousCandidates = [...this.candidates];
    const previousCount = previousCandidates.length;
    
    this.candidateService.getAllCandidates().subscribe({
      next: (candidates) => {
        console.log('📊 Backend response received - Raw candidates data:', candidates);
        console.log('📊 Number of candidates returned:', candidates.length);
        
        // Map backend candidates to card format with proper field mapping
        const newCandidates = candidates.map(c => ({
          id: c.id || 0,
          name: c.name || 'Unknown',
          jobTitle: c.appliedRole || 'N/A',
          experience: `${c.totalExperience || 0} yrs`,
          email: c.email || '',
          phone: c.phone || '',
          status: c.status || 'PENDING',
          interviewRound: c.interviewRound || '',
          requisitionId: c.requisitionId || '',
          profilePictureUrl: c.profilePicUrl || '/assets/images/default-avatar.png',
          resumeUrl: c.resumeUrl || '',
          active: false // Reset selection state for all
        })) as CandidateCard[];
        
        // Preserve selection state if candidate still exists
        const currentlySelected = previousCandidates.find(c => c.active);
        if (currentlySelected) {
          const stillExists = newCandidates.find(c => c.id === currentlySelected.id);
          if (stillExists) {
            stillExists.active = true;
            this.selectedCandidateForInterview = stillExists;
            console.log('🎯 Preserved selection for candidate:', stillExists.name);
          } else {
            this.selectedCandidateForInterview = null;
            console.log('🚫 Previously selected candidate no longer exists - clearing selection');
          }
        }
        
        // Update the candidates array
        this.candidates = newCandidates;
        
        // Analyze changes
        const changeAnalysis = this.analyzeDataChanges(previousCandidates, newCandidates);
        console.log('🔍 Change analysis:', changeAnalysis);
        
        // Show appropriate notifications for significant changes
        if (changeAnalysis.newCandidates.length > 0 && !forceRefresh) {
          this.toastService.showInfo('New Candidates', `${changeAnalysis.newCandidates.length} new candidate(s) added.`);
        }
        
        if (changeAnalysis.updatedCandidates.length > 0 && !forceRefresh) {
          console.log('🔄 Updated candidates detected:', changeAnalysis.updatedCandidates);
        }
        
        this.isLoading = false;
        console.log('🏁 Dashboard loading completed successfully');
        
        // Update UI components after a short delay to ensure DOM is ready
        setTimeout(() => {
          this.updateDropdownValues();
          this.applyCurrentFilters(); // Reapply any active filters
          console.log('✅ Dashboard refresh cycle completed - UI should now show latest data');
        }, 50);
      },
      error: (err) => {
        console.error('❌ Error loading candidates in loadDashboardData():', err);
        this.isLoading = false;
        
        // Provide more specific error messages based on error type
        if (err.status === 0) {
          this.toastService.showError('Network Error', 'Unable to connect to server. Please check your internet connection.', true);
        } else if (err.status === 401) {
          this.toastService.showError('Authentication Required', 'Please log in again to access candidates.', true);
        } else if (err.status >= 500) {
          this.toastService.showError('Server Error', 'Server is experiencing issues. Please try again later.', true);
        } else {
          this.toastService.showError('Failed to Load Candidates', err.user || 'Unable to fetch candidates. Please refresh the page.', true);
        }
      }
    });
  }
  
  // Method to analyze changes between old and new candidate data
  private analyzeDataChanges(oldCandidates: CandidateCard[], newCandidates: CandidateCard[]) {
    const newCandidateIds = new Set(newCandidates.map(c => c.id));
    const oldCandidateIds = new Set(oldCandidates.map(c => c.id));
    
    return {
      newCandidates: newCandidates.filter(c => !oldCandidateIds.has(c.id)),
      removedCandidates: oldCandidates.filter(c => !newCandidateIds.has(c.id)),
      updatedCandidates: newCandidates.filter(newCandidate => {
        const oldCandidate = oldCandidates.find(c => c.id === newCandidate.id);
        return oldCandidate && this.hasCandidateChanged(oldCandidate, newCandidate);
      }),
      totalBefore: oldCandidates.length,
      totalAfter: newCandidates.length
    };
  }
  
  // Helper method to detect if a candidate has changed
  private hasCandidateChanged(oldCandidate: CandidateCard, newCandidate: CandidateCard): boolean {
    const fieldsToCompare = ['name', 'status', 'interviewRound', 'jobTitle', 'experience', 'requisitionId'];
    return fieldsToCompare.some(field => oldCandidate[field] !== newCandidate[field]);
  }
  
  // Method to refresh data with proper cache busting
  public refreshDashboard(): void {
    console.log('🔄 Manual refresh triggered');
    this.toastService.showInfo('Refreshing...', 'Loading latest candidate data.');
    this.loadDashboardData(true); // Force refresh
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
          this.loadDashboardData(true);  // Force refresh after update
          this.closeAddCandidatePopup(); // Close popup after successful save
          this.addActivity(`Updated candidate ${candidateData.name}`, 'fas fa-user-edit');
        },
        error: (err) => {
          console.error('Error updating candidate:', err);
          
          let errorMessage = 'Failed to update candidate.';
          if (err.status === 500) {
            errorMessage = 'Server error: The backend update service has a bug. Try refreshing the page and editing again, or contact support.';
          } else if (err.status === 403) {
            errorMessage = 'Authentication error: Please log in again.';
          } else if (err.user) {
            errorMessage = err.user;
          }
          
          this.toastService.showError('Update Failed', errorMessage, true);
          // Don't close popup on error so user can retry
        }
      });
    } else {
      // Create new candidate
      console.log('🔄 Subscribing to CandidateService.addCandidate()...');
      const addCandidateSubscription = this.candidateService.addCandidate(candidateData, resumeFile, profilePic);
      console.log('🔄 Observable created:', addCandidateSubscription);
      
      addCandidateSubscription.subscribe({
        next: (response) => {
          console.log('✅ Candidate created successfully:', response);
          
          // Close popup immediately
          this.closeAddCandidatePopup();
          
          // Show success message
          this.toastService.showSuccess('Candidate Added!', `${candidateData.name} has been successfully added to the system.`);
          
          // Add activity log
          this.addActivity(`Added new candidate ${candidateData.name}`, 'fas fa-user-plus');
          
          // Refresh dashboard data with proper cache invalidation
          console.log('🔄 Refreshing dashboard data after candidate creation...');
          setTimeout(() => {
            this.loadDashboardData(true); // Force refresh after creation
          }, 300); // Reduced delay for better UX
        },
        error: (err) => {
          console.error('❌ Error saving candidate:', err);
          console.error('Error details:', {
            status: err.status,
            technical: err.technical,
            user: err.user,
            originalError: err.originalError
          });
          
          // Enhanced error messaging for 400 Bad Request errors
          let errorMessage = 'Failed to save candidate.';
          if (err.status === 400) {
            errorMessage = `Validation Error: ${err.user || 'Please check all required fields and file uploads.'}\n\nTechnical Details: ${err.technical || 'Invalid request data'}`;
            
            // Log specific validation details for debugging
            console.error('400 Bad Request Details:');
            console.error('- Candidate data sent:', candidateData);
            console.error('- Resume file:', resumeFile ? `${resumeFile.name} (${resumeFile.size} bytes, ${resumeFile.type})` : 'None');
            console.error('- Profile pic:', profilePic ? `${profilePic.name} (${profilePic.size} bytes, ${profilePic.type})` : 'None');
            
            // Check for common issues
            if (!resumeFile) {
              errorMessage += '\n\n⚠️ Missing resume file - this is required for candidate creation.';
            }
            if (!candidateData.applicationDate) {
              errorMessage += '\n\n⚠️ Missing application date - this is required.';
            }
            if (!candidateData.phone || candidateData.phone.length < 10) {
              errorMessage += '\n\n⚠️ Invalid phone number format - ensure it has at least 10 digits.';
            }
          } else if (err.status === 415) {
            errorMessage = 'Media Type Error: The request format is not supported by the server. This is likely a configuration issue.';
          } else if (err.status === 500) {
            errorMessage = 'Server error: There may be a backend issue. Please try again or contact support.';
          } else if (err.status === 403) {
            errorMessage = 'Authentication error: Please log in again.';
          } else if (err.user) {
            errorMessage = err.user;
          }
          
          this.toastService.showError('Creation Failed', errorMessage, true);
          // Don't close popup on error so user can retry
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
  // Current active filters
  private currentFilters = {
    searchTerm: '',
    statusFilter: '',
    requisitionFilter: ''
  };
  
  // Method to apply current filters (used after data refresh)
  private applyCurrentFilters(): void {
    // This method ensures filters remain active after data refresh
    // Implementation would depend on how filtering UI is implemented
    console.log('Applying current filters:', this.currentFilters);
    
    // If search term exists, filter the displayed candidates
    if (this.currentFilters.searchTerm) {
      this.filteredCandidates = this.filterCandidates(this.currentFilters.searchTerm);
    } else {
      this.filteredCandidates = [...this.candidates];
    }
  }
  
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
  
  // Method to update search term and apply filter
  onSearchChanged(searchTerm: string): void {
    this.currentFilters.searchTerm = searchTerm;
    this.searchTerm = searchTerm;
    this.applyCurrentFilters();
  }
  
  // Method to filter by status using backend API
  onStatusFilterChanged(status: string): void {
    this.currentFilters.statusFilter = status;
    if (status && status !== 'ALL') {
      this.isLoading = true;
      this.candidateService.getCandidatesByStatus(status).subscribe({
        next: (candidates) => {
          this.candidates = candidates.map(c => ({
            id: c.id || 0,
            name: c.name || 'Unknown',
            jobTitle: c.appliedRole || 'N/A',
            experience: `${c.totalExperience || 0} yrs`,
            email: c.email || '',
            phone: c.phone || '',
            status: c.status || 'PENDING',
            interviewRound: c.interviewRound || '',
            requisitionId: c.requisitionId || '',
            profilePictureUrl: c.profilePicUrl || '/assets/images/default-avatar.png',
            resumeUrl: c.resumeUrl || '',
            active: false
          })) as CandidateCard[];
          this.isLoading = false;
        },
        error: (err) => {
          console.error('Error filtering by status:', err);
          this.toastService.showError('Filter Failed', 'Unable to filter candidates by status.');
          this.isLoading = false;
        }
      });
    } else {
      // Reset to show all candidates
      this.loadDashboardData();
    }
  }
  
  // Method to filter by requisition using backend API
  onRequisitionFilterChanged(requisitionId: string): void {
    this.currentFilters.requisitionFilter = requisitionId;
    if (requisitionId && requisitionId !== 'ALL') {
      this.isLoading = true;
      this.candidateService.getCandidatesByRequisitionId(requisitionId).subscribe({
        next: (candidates) => {
          this.candidates = candidates.map(c => ({
            id: c.id || 0,
            name: c.name || 'Unknown',
            jobTitle: c.appliedRole || 'N/A',
            experience: `${c.totalExperience || 0} yrs`,
            email: c.email || '',
            phone: c.phone || '',
            status: c.status || 'PENDING',
            interviewRound: c.interviewRound || '',
            requisitionId: c.requisitionId || '',
            profilePictureUrl: c.profilePicUrl || '/assets/images/default-avatar.png',
            resumeUrl: c.resumeUrl || '',
            active: false
          })) as CandidateCard[];
          this.isLoading = false;
        },
        error: (err) => {
          console.error('Error filtering by requisition:', err);
          this.toastService.showError('Filter Failed', 'Unable to filter candidates by requisition ID.');
          this.isLoading = false;
        }
      });
    } else {
      // Reset to show all candidates
      this.loadDashboardData();
    }
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

  // ---------- Schedule Interview Implementation ----------
  public scheduleInterview() {
    if (!this.selectedCandidateForInterview) {
      this.toastService.showWarning('No Candidate Selected', 'Please select a candidate first before scheduling an interview.');
      return;
    }

    // Show scheduling options modal
    this.showSchedulingOptionsModal();
  }

  // ---------- Interview Scheduling Modal Implementation ----------
  showSchedulingModal = false;
  showDateTimeModal = false;
  selectedScheduleTime: string = '';
  isSchedulingNow = false;

  private showSchedulingOptionsModal() {
    this.showSchedulingModal = true;
  }

  closeSchedulingModal() {
    this.showSchedulingModal = false;
    this.showDateTimeModal = false;
    this.selectedScheduleTime = '';
  }

  scheduleNow() {
    this.isSchedulingNow = true;
    this.closeSchedulingModal();
    
    // Schedule interview 5 minutes from now to ensure it's in the future
    // This provides enough buffer for backend validation while keeping it immediate
    const futureTime = new Date();
    futureTime.setMinutes(futureTime.getMinutes() + 5);
    
    console.log('🕐 Schedule Now - Current time (local):', new Date().toLocaleString());
    console.log('🕐 Schedule Now - Current time (UTC):', new Date().toISOString());
    console.log('🕐 Schedule Now - Future time (local):', futureTime.toLocaleString());
    console.log('🕐 Schedule Now - Future time (UTC):', futureTime.toISOString());
    console.log('🌍 Browser timezone:', Intl.DateTimeFormat().resolvedOptions().timeZone);
    
    this.processInterviewScheduling(futureTime);
  }

  scheduleForLater() {
    this.showDateTimeModal = true;
  }

  confirmScheduleForLater() {
    if (!this.selectedScheduleTime) {
      this.toastService.showWarning('Missing Schedule Time', 'Please select a date and time for the interview.');
      return;
    }

    // Parse datetime-local input as local time, not UTC
    const scheduleDate = new Date(this.selectedScheduleTime);
    const now = new Date();
    
    // Check if selected time is at least 5 minutes in the future
    const minimumFutureTime = new Date(now.getTime() + 5 * 60 * 1000); // 5 minutes from now
    if (scheduleDate <= minimumFutureTime) {
      this.toastService.showWarning('Invalid Schedule Time', 'Please select a time at least 5 minutes in the future.');
      return;
    }

    // Remove excessive timezone buffer - send the selected time as-is
    // The backend should handle timezone conversion properly
    console.log('Selected time (local):', scheduleDate.toLocaleString());
    console.log('Selected time (ISO):', scheduleDate.toISOString());
    
    this.closeSchedulingModal();
    this.processInterviewScheduling(scheduleDate);
  }

  /**
   * Converts scheduled time to local datetime string format for backend
   * Backend expects LocalDateTime which should be in server timezone
   */
  private adjustScheduledTime(selectedTime: Date): string {
    // Format as YYYY-MM-DDTHH:mm:ss without timezone info
    // This matches LocalDateTime format expected by backend
    const year = selectedTime.getFullYear();
    const month = String(selectedTime.getMonth() + 1).padStart(2, '0');
    const day = String(selectedTime.getDate()).padStart(2, '0');
    const hours = String(selectedTime.getHours()).padStart(2, '0');
    const minutes = String(selectedTime.getMinutes()).padStart(2, '0');
    const seconds = String(selectedTime.getSeconds()).padStart(2, '0');
    
    const localDateTimeString = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
    
    console.log('🕐 Converting scheduled time:');
    console.log('  - Original Date object:', selectedTime);
    console.log('  - Local string representation:', selectedTime.toLocaleString());
    console.log('  - ISO string (UTC):', selectedTime.toISOString());
    console.log('  - LocalDateTime format (for backend):', localDateTimeString);
    console.log('  - Browser timezone:', Intl.DateTimeFormat().resolvedOptions().timeZone);
    
    return localDateTimeString;
  }

  private processInterviewScheduling(scheduledTime: Date) {
    this.isProcessing = true;
    
    // Validate that we have a selected candidate
    if (!this.selectedCandidateForInterview) {
      this.toastService.showError('No Candidate Selected', 'No candidate selected for interview scheduling.');
      this.isProcessing = false;
      return;
    }
    
    // Get recruiter info from session service or set defaults
    const currentUser = this.sessionService.getCurrentUser();
    const recruiterId = currentUser?.id ? parseInt(currentUser.id) : 1;
    const recruiterName = currentUser?.name || 'Hiring Manager';
    const recruiterEmail = currentUser?.email || 'recruiter@company.com';
    
    // Ensure candidate has required fields with fallbacks
    const candidateName = this.selectedCandidateForInterview.name || 'Unknown Candidate';
    const candidateEmail = this.selectedCandidateForInterview['email'] || 
                          this.selectedCandidateForInterview['candidateEmail'] || 
                          `candidate${this.selectedCandidateForInterview.id}@example.com`;
    
    // Log for debugging
    console.log('Candidate data:', {
      id: this.selectedCandidateForInterview.id,
      name: candidateName,
      email: candidateEmail,
      originalCandidate: this.selectedCandidateForInterview
    });
    
    // Create interview schedule request with adjusted scheduled time
    const scheduleRequest: InterviewScheduleRequest = {
      candidateId: parseInt(this.selectedCandidateForInterview.id?.toString() || '0'),
      candidateName: candidateName,
      candidateEmail: candidateEmail,
      recruiterId: recruiterId,
      recruiterName: recruiterName,
      recruiterEmail: recruiterEmail,
      scheduledStartTime: this.adjustScheduledTime(scheduledTime),
      jobRole: this.selectedCandidateForInterview.jobTitle || 'Software Engineer',
      experienceLevel: this.selectedCandidateForInterview.experience || '0 years',
      requiredTechnologies: ['JavaScript', 'TypeScript', 'Angular'], // TODO: Get from candidate profile
      minQuestions: 5,
      maxQuestions: 15,
      interviewType: 'ADAPTIVE_AI',
      languagePreference: 'en',
      enableBiasDetection: true,
      enableCodeChallenges: true,
      enableVideoAnalytics: true
    };

    console.log('Scheduling interview with request:', scheduleRequest);

    this.interviewService.scheduleInterview(scheduleRequest).subscribe({
      next: (response) => {
        console.log('✅ Interview scheduled successfully:', response);
        
        this.isProcessing = false;
        
        // Update candidate status to SCHEDULED
        this.selectedCandidateForInterview!.status = 'SCHEDULED';
        
        // Add activity log
        this.addActivity(
          `Interview scheduled for ${this.selectedCandidateForInterview!.name}`, 
          'fas fa-calendar-check'
        );
        
        // Show success notification with meeting link
        this.showMeetingLinkNotification(
          this.selectedCandidateForInterview!.name,
          response.meetingLink,
          response.sessionId
        );
        
        // Share meeting link with participants
        this.shareMeetingLink(response.sessionId, response.meetingLink);
        
      },
      error: (err) => {
        console.error('❌ Error scheduling interview:', err);
        this.isProcessing = false;
        this.toastService.showError('Scheduling Failed', 'Failed to schedule interview. Please try again.');
      }
    });
  }

  /**
   * Show meeting link notification to recruiter
   */
  private showMeetingLinkNotification(candidateName: string, meetingLink: string, sessionId: string) {
    const message = `
      🎉 Interview scheduled successfully for ${candidateName}!
      
      📅 Meeting Link: ${meetingLink}
      📋 Session ID: ${sessionId}
      
      The meeting link has been shared with all participants.
      Click 'Join Interview' when ready to start.
    `;
    
    // Create a more sophisticated notification
    const notification = document.createElement('div');
    notification.style.cssText = `
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      background-color: #ffffff;
      color: #333;
      padding: 24px;
      border-radius: 8px;
      z-index: 10000;
      box-shadow: 0 8px 32px rgba(0,0,0,0.2);
      font-family: Arial, sans-serif;
      max-width: 500px;
      border: 2px solid #4CAF50;
    `;
    
    notification.innerHTML = `
      <div style="text-align: center;">
        <h3 style="color: #4CAF50; margin-bottom: 16px;">✅ Interview Scheduled!</h3>
        <p style="margin-bottom: 16px;"><strong>Candidate:</strong> ${candidateName}</p>
        <p style="margin-bottom: 16px;"><strong>Meeting Link:</strong></p>
        <input type="text" value="${meetingLink}" readonly 
               style="width: 100%; padding: 8px; margin-bottom: 16px; border: 1px solid #ddd; border-radius: 4px;" 
               onclick="this.select()">
        <div style="display: flex; gap: 12px; justify-content: center;">
          <button onclick="navigator.clipboard.writeText('${meetingLink}'); alert('Link copied!')" 
                  style="background: #2196F3; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">
            📋 Copy Link
          </button>
          <button onclick="window.joinInterviewFromNotification('${sessionId}')" 
                  style="background: #4CAF50; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">
            🚀 Join Interview
          </button>
          <button onclick="this.parentNode.parentNode.parentNode.remove()" 
                  style="background: #f44336; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer;">
            ✕ Close
          </button>
        </div>
      </div>
    `;
    
    document.body.appendChild(notification);
    
    // Auto-remove after 30 seconds
    setTimeout(() => {
      if (notification.parentNode) {
        notification.parentNode.removeChild(notification);
      }
    }, 30000);
  }

  /**
   * Share meeting link with all participants via enhanced email system
   */
  private shareMeetingLink(sessionId: string, meetingLink: string) {
    if (!this.selectedCandidateForInterview) {
      console.error('No candidate selected for meeting link sharing');
      return;
    }

    console.log('🚀 Generating interview tokens and sending invitations...');

    // Step 1: Generate session tokens for all participants
    this.generateSessionTokens(sessionId).then((tokens) => {
      // Step 2: Send personalized emails with tokens
      this.sendInterviewInvitations(sessionId, meetingLink, tokens);
    }).catch((error) => {
      console.error('❌ Failed to generate session tokens:', error);
      this.toastService.showError('Token Generation Failed', 'Failed to generate session tokens. Please try again.', true);
    });
  }

  /**
   * Generate session tokens for all interview participants
   */
  private async generateSessionTokens(sessionId: string): Promise<any> {
    try {
      const tokenRequests = [
        {
          userId: '1', // TODO: Get actual recruiter ID from session
          userType: 'recruiter' as const,
          sessionId: sessionId,
          recruiterName: 'Hiring Manager', // TODO: Get from session
          position: this.selectedCandidateForInterview!.jobTitle
        },
        {
          userId: this.selectedCandidateForInterview!.id.toString(),
          userType: 'candidate' as const,
          sessionId: sessionId,
          candidateName: this.selectedCandidateForInterview!.name,
          position: this.selectedCandidateForInterview!.jobTitle
        },
        {
          userId: 'aria_ai',
          userType: 'ai_avatar' as const,
          sessionId: sessionId
        }
      ];

      // Generate tokens for all participants
      const tokenPromises = tokenRequests.map(request => 
        this.sessionService.login(request).toPromise()
      );

      const tokens = await Promise.all(tokenPromises);
      
      return {
        recruiterToken: tokens[0]?.token || '',
        candidateToken: tokens[1]?.token || '',
        aiToken: tokens[2]?.token || ''
      };
    } catch (error) {
      console.error('Token generation failed:', error);
      throw error;
    }
  }

  /**
   * Send personalized interview invitations with embedded tokens
   */
  private sendInterviewInvitations(sessionId: string, meetingLink: string, tokens: any) {
    const candidate = this.selectedCandidateForInterview!;
    const scheduledDateTime = this.isSchedulingNow ? 
      new Date().toLocaleString() : 
      new Date(this.selectedScheduleTime).toLocaleString();
    
    // Prepare participant data with enhanced email templates
    const emailData = {
      sessionId: sessionId,
      meetingLink: meetingLink,
      scheduledDateTime: scheduledDateTime,
      candidateInfo: {
        email: candidate['email'] || 'candidate@example.com',
        name: candidate.name,
        token: tokens.candidateToken,
        interviewUrl: `${window.location.origin}/interview/${sessionId}?token=${tokens.candidateToken}`
      },
      recruiterInfo: {
        email: 'recruiter@company.com', // TODO: Get from session
        name: 'Hiring Manager',
        token: tokens.recruiterToken,
        monitorUrl: `${window.location.origin}/interview/${sessionId}?token=${tokens.recruiterToken}&role=recruiter`
      },
      position: candidate.jobTitle,
      companyName: 'TechCorp' // TODO: Get from company settings
    };

    console.log('📧 Sending interview invitations with tokens:', {
      candidateEmail: emailData.candidateInfo.email,
      recruiterEmail: emailData.recruiterInfo.email,
      sessionId: sessionId
    });

    // Send via enhanced email service
    this.interviewService.shareMeetingLink(sessionId, meetingLink, [
      {
        email: emailData.candidateInfo.email,
        role: 'candidate',
        name: emailData.candidateInfo.name,
        token: emailData.candidateInfo.token,
        interviewUrl: emailData.candidateInfo.interviewUrl
      },
      {
        email: emailData.recruiterInfo.email,
        role: 'recruiter', 
        name: emailData.recruiterInfo.name,
        token: emailData.recruiterInfo.token,
        monitorUrl: emailData.recruiterInfo.monitorUrl
      }
    ]).subscribe({
      next: (response) => {
        console.log('✅ Interview invitations sent successfully:', response);
        this.addActivity(
          `🎯 Interview invitations sent to ${candidate.name} and recruiter with secure access tokens`, 
          'fas fa-envelope-open-text'
        );
        this.toastService.showSuccess(
          'Invitations Sent!', 
          `Interview invitations sent to ${candidate.name} with secure access links.`
        );
      },
      error: (err) => {
        console.error('❌ Error sending interview invitations:', err);
        this.addActivity(
          'Failed to send interview invitations - manual sharing required', 
          'fas fa-exclamation-triangle'
        );
        this.toastService.showError(
          'Invitation Failed',
          'Failed to send email invitations. Please share the meeting links manually.',
          true
        );
      }
    });
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
      this.toastService.showWarning('No Candidate Selected', 'Please select a candidate first before scheduling an interview.');
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
    this.toastService.showInfo('Candidate Selected', `${card.name} selected for interview scheduling`);
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
            this.toastService.showSuccess('Updated!', `${this.getFieldDisplayName(field)} updated successfully for ${card.name}.`);
          },
          error: (err) => {
            console.error(`❌ Error updating ${field} for ${card.name}:`, err);
            
            // Revert optimistic UI change on error
            this.revertFieldChange(card, field, oldValue);
            
            // Show user-friendly error message
            const errorMessage = this.getErrorMessage(err, field);
            this.toastService.showError('Update Failed', errorMessage);
            
            // Cleanup pending state
            this.pendingUpdates.delete(card.id);
          }
        });
      },
      error: (err) => {
        console.error('❌ Error fetching candidate details for update:', err);
        
        // Revert optimistic UI change on fetch error
        this.revertFieldChange(card, field, oldValue);
        
        this.toastService.showError('Fetch Failed', 'Failed to fetch latest candidate data. Please refresh and try again.');
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

  // ---------- DateTime Helper Methods for Modal ----------
  getCurrentDateTime(): string {
    const now = new Date();
    // Add 10 minutes to current time as minimum for datetime picker
    // This gives users flexibility while ensuring backend timezone validation passes
    now.setMinutes(now.getMinutes() + 10);
    
    // Format as YYYY-MM-DDTHH:mm for datetime-local input
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    
    console.log('🕐 Default datetime for picker (10min buffer):', `${year}-${month}-${day}T${hours}:${minutes}`);
    console.log('🌍 Browser timezone:', Intl.DateTimeFormat().resolvedOptions().timeZone);
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  }

  getCurrentTimezone(): string {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
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
        this.toastService.showSuccess('Audio Uploaded!', `Phone screening audio for ${candidate?.name ?? 'Unknown Candidate'} uploaded successfully!`);
        
        // Close the modal
        this.closeAudioUpload();
      },
      error: (err) => {
        console.error('❌ Error uploading audio:', err);
        
        // Show error notification
        this.toastService.showError('Upload Failed', 'Failed to upload audio. Please try again.');
        
        // Reset upload state in the modal component (if it has this method)
        // Note: We'll need to add a reference to the modal component for this
      }
    });
  }
}
