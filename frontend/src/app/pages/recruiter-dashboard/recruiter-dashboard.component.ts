import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AddCandidatePopupComponent } from '../../components/add-candidate-popup/add-candidate-popup.component';
import { Router } from '@angular/router';
import { CandidateService } from '../../services/candidate.service';

interface CandidateCard {
  id: number;
  name: string;
  jobTitle: string;
  experience: string;
  requisitionId?: string;
  status: string;
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
          phoneNumber: c.phoneNumber,
          status: c.status,
          profilePictureUrl: c.profilePictureUrl || '/assets/images/default-avatar.png',
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
    this.selectedCandidate = candidate;
    this.showAddCandidatePopup = true;
  }

  closeAddCandidatePopup() {
    this.showAddCandidatePopup = false;
  }

  onCandidateSaved(payload: any) {  // New handler for save event
    const resumeFile = payload.uploadResume;  // File object
    const profilePic = payload.uploadPicture;  // File object

    const candidateData = {
      requisitionId: payload.requisitionId,
      name: payload.candidateName,  // Map to backend 'name'
      email: payload.candidateEmail,  // Map to backend 'email'
      phoneNumber: payload.candidatePhone,  // Map to backend 'phoneNumber' to match Candidate interface
      appliedRole: payload.appliedRole,
      applicationDate: payload.applicationDate,  // Assume YYYY-MM-DD format
      totalExperience: payload.totalExperience,
      relevantExperience: payload.relevantExperience,
      interviewRound: payload.interviewRound,
      status: this.mapStatus(payload.status),  // Map to backend enum
      jobDescription: payload.jobDescription,
      keyResponsibilities: payload.keyResponsibilities,
      // Add defaults for missing backend fields if required (e.g., skills: [], source: null)
      skills: [],
      source: null,
      notes: null,
      tags: null,
      recruiterId: null
    };

    this.candidateService.addCandidate(candidateData, resumeFile, profilePic).subscribe({
      next: (response) => {
        this.loadDashboardData();  // Refresh using existing method
      },
      error: (err) => {
        console.error('Error saving candidate:', err);
      }
    });
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
  statusOptions = ['Screening', 'T1', 'T2', 'On Hold', 'Rejected', 'Selected'];
  candidates: CandidateCard[] = [];
  recentActivities: Activity[] = [];
  filteredCandidates: CandidateCard[] = [];
  searchTerm = '';

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
    const pendingStatuses = ['SCREENING', 'TECHNICAL_1'];
    return this.candidates.filter(c => pendingStatuses.includes(c.status)).length;
  }

  getCompletedInterviews(): number {  // Retained
    const completedStatuses = ['REJECTED', 'SELECTED'];
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

  getStatusClass(status: string): string {  // Stub for template
    const statusClasses: { [key: string]: string } = {
      'SCREENING': 'status-screening',
      'TECHNICAL_1': 'status-technical',
      'ON_HOLD': 'status-on-hold',
      'REJECTED': 'status-rejected',
      'SELECTED': 'status-selected'
    };
    return statusClasses[status] || 'status-default';
  }

  onStatusChange(card: CandidateCard, event: Event) {  // Stub for template
    const newStatus = (event.target as HTMLSelectElement).value;
    card.status = newStatus;
    console.log('Status changed for', card.name, 'to', newStatus);
    // Implement status update logic (e.g., call updateCandidate)
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