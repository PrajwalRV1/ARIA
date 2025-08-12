import { Component } from '@angular/core';
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
  avatar?: string; // path in /assets or SVG data
  active?: boolean;
}

@Component({
  selector: 'app-recruiter-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, AddCandidatePopupComponent],
  templateUrl: './recruiter-dashboard.component.html',
  styleUrls: ['./recruiter-dashboard.component.scss'],
})
export class RecruiterDashboardComponent {
  constructor(private router: Router) { }

  // Navigate to question bank dashboard with proceeding candidate object
  getActiveCandidate() {
    return this.candidates.find(c => c.active);
  }  
  
  goToQuestionBank(card: any) {
    if (!card) {
      console.warn('No candidate selected');
      return;
    }
    // Store candidate in sessionStorage so it survives refresh
    sessionStorage.setItem('selectedCandidate', JSON.stringify(card));
    this.router.navigate(['/question-bank']);
  }
  
  
  
  // add candidate pop up open & close
  showAddCandidatePopup = false;

  openAddCandidatePopup() {
    this.showAddCandidatePopup = true;
  }

  closeAddCandidatePopup() {
    this.showAddCandidatePopup = false;
  }

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

  // Tabs
  tabs = [
    { key: 'candidates', label: 'Candidates' },
    { key: 'upload', label: 'Upload Resume to add candidate' },
    { key: 'schedule', label: 'Schedule Interview' },
  ];
  activeTab = 'candidates';

  // Status options (for the dropdown shown on each card)
  statusOptions = ['Screening', 'T1', 'T2', 'On Hold', 'Rejected', 'Selected'];

  // Mock candidate data
  candidates: CandidateCard[] = [
    {
      id: 0,
      name: 'Ashoka G S',
      jobTitle: 'Senior Software Engineer',
      experience: 'Total Experience : 5 years of experience Relevant Experience : Experience',
      requisitionId: 'T1FSD101',
      status: 'Screening',
      avatar: '/assets/sample-avatar-1.svg',
      active: true,
    },
    {
      id: 1,
      name: 'Ashoka G S',
      jobTitle: 'Senior Software Engineer',
      experience: 'Total Experience : 5 years of experience Relevant Experience : Experience',
      requisitionId: 'T1FSD101',
      status: 'T1',
      avatar: '/assets/sample-avatar-2.svg',
      active: false,
    },
    {
      id: 2,
      name: 'Ashoka G S',
      jobTitle: 'Senior Software Engineer',
      experience: 'Total Experience : 5 years of experience Relevant Experience : Experience',
      requisitionId: 'T1FSD101',
      status: 'Screening',
      avatar: '/assets/sample-avatar-3.svg',
      active: false,
    },
    {
      id: 3,
      name: 'Ashoka G S',
      jobTitle: 'Senior Software Engineer',
      experience: 'Total Experience : 5 years of experience Relevant Experience : Experience',
      requisitionId: 'T1FSD101',
      status: 'Screening',
      avatar: '/assets/sample-avatar-4.svg',
      active: false,
    },
    {
      id: 4,
      name: 'Ashoka G S',
      jobTitle: 'Senior Software Engineer',
      experience: 'Total Experience : 5 years of experience Relevant Experience : Experience',
      requisitionId: 'T1FSD101',
      status: 'Screening',
      avatar: '/assets/sample-avatar-5.svg',
      active: false,
    },
  ];

  // Methods for interactions (currently stubs that log to console)
  setActiveTab(tabKey: string) {
    this.activeTab = tabKey;
    console.log('Tab changed to:', tabKey);
  }

  selectCard(card: CandidateCard) {
    // toggle active state so only the clicked card is active
    this.candidates.forEach((c) => (c.active = c.id === card.id));
    console.log('Selected card id:', card.id);
  }

  onStatusChange(card: CandidateCard, event: Event) {
    const select = event.target as HTMLSelectElement;
    card.status = select.value;
    console.log(`Status changed for candidate ${card.id}:`, card.status);
  }

  viewDetails(card: CandidateCard) {
    console.log('View details clicked for', card);
  }

  editDetails(card: CandidateCard) {
    console.log('Edit clicked for', card);
  }

  openFeedback(card: CandidateCard) {
    console.log('Open interview feedback for', card);
  }

  // sample actions on right side
  quickAction(action: string) {
    console.log('Right-panel action:', action);
  }
}
