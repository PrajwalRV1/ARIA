import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AddCandidatePopupComponent } from '../../components/add-candidate-popup/add-candidate-popup.component';
import { Router } from '@angular/router';

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
  constructor(private router: Router) {}

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
    // Simulate API call delay
    setTimeout(() => {
      this.isLoading = false;
    }, 1500);
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

  // ---------- Confirmation Modal ----------
  showConfirmation = false;
  confirmationMessage = '';
  private pendingAction: () => void = () => {};

  private showConfirmationDialog(message: string, action: () => void) {
    this.confirmationMessage = message;
    this.pendingAction = action;
    this.showConfirmation = true;
  }

  closeConfirmation() {
    this.showConfirmation = false;
    this.confirmationMessage = '';
    this.pendingAction = () => {};
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

  candidates: CandidateCard[] = [
    {
      id: 0,
      name: 'Ashoka G S',
      jobTitle: 'Senior Software Engineer',
      experience: '5 years of experience',
      requisitionId: 'TTSD101',
      status: 'Screening',
      avatar: '/assets/sample-avatar-1.svg',
      active: true,
    },
    {
      id: 1,
      name: 'Jane Smith',
      jobTitle: 'Frontend Developer',
      experience: '3 years React, Angular',
      requisitionId: 'TTSD102',
      status: 'T1',
      avatar: '/assets/sample-avatar-2.svg',
      active: false,
    },
    {
      id: 2,
      name: 'John Doe',
      jobTitle: 'Full Stack Developer',
      experience: '4 years MERN Stack',
      requisitionId: 'TTSD103',
      status: 'Screening',
      avatar: '/assets/sample-avatar-3.svg',
      active: false,
    },
    {
      id: 3,
      name: 'Sarah Wilson',
      jobTitle: 'UI/UX Designer',
      experience: '6 years Design Systems',
      requisitionId: 'TTSD104',
      status: 'T2',
      avatar: '/assets/sample-avatar-4.svg',
      active: false,
    },
    {
      id: 4,
      name: 'Mike Johnson',
      jobTitle: 'DevOps Engineer',
      experience: '7 years AWS, Docker',
      requisitionId: 'TTSD105',
      status: 'Selected',
      avatar: '/assets/sample-avatar-5.svg',
      active: false,
    },
  ];

  // ---------- Recent Activities ----------
  recentActivities: Activity[] = [];

  private initializeActivities() {
    this.recentActivities = [
      {
        id: 1,
        text: 'Jane Smith interview scheduled for tomorrow',
        time: new Date(Date.now() - 2 * 60 * 60 * 1000), // 2 hours ago
        icon: 'fas fa-calendar-plus'
      },
      {
        id: 2,
        text: 'John Doe status updated to Screening',
        time: new Date(Date.now() - 4 * 60 * 60 * 1000), // 4 hours ago
        icon: 'fas fa-user-edit'
      },
      {
        id: 3,
        text: 'New candidate Sarah Wilson added',
        time: new Date(Date.now() - 6 * 60 * 60 * 1000), // 6 hours ago
        icon: 'fas fa-user-plus'
      },
      {
        id: 4,
        text: 'Interview feedback submitted for Mike Johnson',
        time: new Date(Date.now() - 8 * 60 * 60 * 1000), // 8 hours ago
        icon: 'fas fa-comment-dots'
      },
      {
        id: 5,
        text: 'Bulk upload completed - 5 candidates added',
        time: new Date(Date.now() - 24 * 60 * 60 * 1000), // 1 day ago
        icon: 'fas fa-upload'
      }
    ];
  }

  // ---------- Helper Methods for Template ----------
  getInitials(name: string): string {
    return name
      .split(' ')
      .map(word => word.charAt(0))
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }

  getStatusClass(status: string): string {
    const statusMap: { [key: string]: string } = {
      'Screening': 'screening',
      'T1': 'interview',
      'T2': 'interview',
      'On Hold': 'warning',
      'Rejected': 'rejected',
      'Selected': 'hired'
    };
    return statusMap[status] || 'screening';
  }

  // ---------- Statistics Methods ----------
  getTotalCandidates(): number {
    return this.candidates.length;
  }

  getPendingInterviews(): number {
    return this.candidates.filter(c => ['T1', 'T2', 'Screening'].includes(c.status)).length;
  }

  getCompletedInterviews(): number {
    return this.candidates.filter(c => ['Selected', 'Rejected'].includes(c.status)).length;
  }

  getHiredCandidates(): number {
    return this.candidates.filter(c => c.status === 'Selected').length;
  }

  // ---------- Card Interactions ----------
  selectCard(card: CandidateCard) {
    this.candidates.forEach((c) => (c.active = c.id === card.id));
    console.log('Selected candidate:', card.name);
    
    // Add activity
    this.addActivity(`Selected candidate ${card.name} for review`, 'fas fa-mouse-pointer');
  }
  
  onStatusChange(card: CandidateCard, event: Event) {
    const select = event.target as HTMLSelectElement;
    const oldStatus = card.status;
    card.status = select.value;
    console.log(`Status changed for ${card.name}: ${oldStatus} -> ${card.status}`);
    
    // Add activity
    this.addActivity(`${card.name} status updated from ${oldStatus} to ${card.status}`, 'fas fa-user-edit');
  }
  
  viewDetails(card: CandidateCard) {
    console.log('View details for:', card.name);
    this.addActivity(`Viewed details for ${card.name}`, 'fas fa-eye');
  }
  
  editDetails(card: CandidateCard) {
    console.log('Edit details for:', card.name);
    // You can add confirmation dialog here if needed
    this.showConfirmationDialog(
      `Are you sure you want to schedule an interview for ${card.name}?`,
      () => {
        this.performScheduleAction(card);
      }
    );
  }

  private performScheduleAction(card: CandidateCard) {
    this.isProcessing = true;
    // Simulate API call
    setTimeout(() => {
      this.isProcessing = false;
      this.addActivity(`Interview scheduled for ${card.name}`, 'fas fa-calendar-plus');
      console.log('Interview scheduled for:', card.name);
    }, 2000);
  }

  openFeedback(card: CandidateCard) {
    console.log('Open interview feedback for:', card.name);
    this.addActivity(`Opened feedback for ${card.name}`, 'fas fa-comment-dots');
  }

  // ---------- Quick Actions ----------
  scheduleInterview() {
    const activeCandidate = this.getActiveCandidate();
    if (!activeCandidate) {
      this.showConfirmationDialog(
        'Please select a candidate first before scheduling an interview.',
        () => {}
      );
      return;
    }
    this.goToQuestionBank(activeCandidate);
  }

  // ---------- Schedule Interview navigation ----------
  getActiveCandidate(): CandidateCard | undefined {
    return this.candidates.find((c) => c.active);
  }

  goToQuestionBank(card?: CandidateCard) {
    const chosen = card ?? this.getActiveCandidate();
    if (!chosen) {
      this.showConfirmationDialog(
        'Please select a candidate first before scheduling an interview.',
        () => {}
      );
      return;
    }

    // Create the candidate info payload
    const candidateInfo: CandidateNavInfo = {
      fullName: chosen.name,
      appliedRole: chosen.jobTitle,
      requisitionId: chosen.requisitionId ?? 'N/A',
    };

    console.log('Navigating to question bank with candidate:', candidateInfo);

    // Add activity
    this.addActivity(`Navigating to question bank for ${chosen.name}`, 'fas fa-arrow-right');

    // Store in sessionStorage for persistence across refreshes
    sessionStorage.setItem('selectedCandidate', JSON.stringify(candidateInfo));

    // Navigate with state for immediate access
    this.router.navigate(['/question-bank'], {
      state: { candidate: candidateInfo }
    });
  }

  // ---------- Right Panel Actions ----------
  quickAction(action: string) {
    console.log('Quick action:', action);
    
    if (action === 'bulk-upload') {
      this.isProcessing = true;
      // Simulate bulk upload process
      setTimeout(() => {
        this.isProcessing = false;
        this.addActivity('Bulk upload initiated', 'fas fa-upload');
      }, 1500);
    }
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

  // Method to filter candidates (useful for search functionality)
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
    const dataUri = 'data:application/json;charset=utf-8,'+ encodeURIComponent(dataStr);
    
    const exportFileDefaultName = `candidates-${new Date().toISOString().split('T')[0]}.json`;
    
    const linkElement = document.createElement('a');
    linkElement.setAttribute('href', dataUri);
    linkElement.setAttribute('download', exportFileDefaultName);
    linkElement.click();
    
    this.addActivity('Exported candidates data', 'fas fa-download');
  }
}