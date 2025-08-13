// import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
// import { CommonModule, isPlatformBrowser } from '@angular/common';
// import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
// import { FormsModule } from '@angular/forms';
// import { Router } from '@angular/router';

// /* Data interfaces */
// export interface Category {
//   id: string;
//   name: string;
//   description?: string;
//   icon?: string;
//   questionCount?: number;
//   selected?: boolean;
// }

// export interface Question {
//   id: string;
//   categoryId: string;
//   heading: string;
//   difficulty: 'Easy' | 'Medium' | 'Hard';
//   estimatedSeconds: number;
//   details?: string;
// }

// export interface BookletItem {
//   question: Question;
//   addedAt: Date;
// }

// export interface CandidateNavInfo {
//   fullName: string;
//   appliedRole: string;
//   requisitionId: string;
// }

// @Component({
//   selector: 'app-question-bank-dashboard',
//   standalone: true,
//   imports: [CommonModule, ReactiveFormsModule, FormsModule],
//   templateUrl: './question-bank-dashboard.component.html',
//   styleUrls: ['./question-bank-dashboard.component.scss'],
// })
// export class QuestionBankDashboardComponent implements OnInit {
//   candidate: CandidateNavInfo | null = null; // unified candidate field

//   // UI state
//   categories: Category[] = [];
//   questions: Question[] = [];
//   allQuestions: Question[] = [];
//   selectedCategoryId: string | null = null;
//   searchQuery = '';
//   loadingQuestions = false;

//   // Booklet
//   booklet: BookletItem[] = [];

//   // Modal for create category
//   showCreateCategoryModal = false;
//   createCategoryForm!: FormGroup;
//   creatingCategory = false;

//   // Total interview duration
//   interviewDurationSeconds = 0;

//   // Difficulty tags
//   readonly difficulties = ['Easy', 'Medium', 'Hard'];

//   constructor(
//     private fb: FormBuilder,
//     private router: Router,
//     @Inject(PLATFORM_ID) private platformId: object
//   ) {}

//   ngOnInit(): void {
//     // 1) Try to get candidate from router state
//     // Only run this in browser
//     if (isPlatformBrowser(this.platformId)) {
//       const nav = this.router.getCurrentNavigation();
//       if (nav?.extras?.state?.['candidate']) {
//         this.candidate = nav.extras.state['candidate'];
//         sessionStorage.setItem('selectedCandidate', JSON.stringify(this.candidate));
//       } else {
//         const storedCandidate = sessionStorage.getItem('selectedCandidate');
//         if (storedCandidate) {
//           this.candidate = JSON.parse(storedCandidate);
//         }
//       }
//     }

//     this.initMockData();

//     this.createCategoryForm = this.fb.group({
//       name: ['', [Validators.required, Validators.maxLength(80)]],
//       round: ['', [Validators.required]],
//       role: ['', [Validators.required]],
//       description: [''],
//     });

//     if (this.categories.length) {
//       this.selectCategory(this.categories[0].id);
//     }
//   }

//   initMockData() {
//     this.categories = [
//       {
//         id: 'c-general',
//         name: 'General Introduction Questions',
//         description: 'Common candidate introduction questions',
//         icon: '/assets/images/avatar.png',
//         questionCount: 10,
//       },
//       {
//         id: 'c-communication',
//         name: 'Communication Skills',
//         description: 'Articulative with the listening skills',
//         icon: '/assets/images/coms.png',
//         questionCount: 6,
//       },
//       {
//         id: 'c-technical',
//         name: 'Technical & Role-based',
//         description: 'Role specific technical questions',
//         icon: '/assets/images/technical.png',
//         questionCount: 12,
//       }
//     ];

//     this.allQuestions = [
//       { id: 'q-1', categoryId: 'c-general', heading: 'Tell us a little bit about yourself?', difficulty: 'Easy', estimatedSeconds: 60 },
//       { id: 'q-2', categoryId: 'c-general', heading: 'Why are you interested in this role?', difficulty: 'Medium', estimatedSeconds: 90 },
//       { id: 'q-3', categoryId: 'c-general', heading: 'What are your strengths and weaknesses?', difficulty: 'Hard', estimatedSeconds: 120 },
//       { id: 'q-4', categoryId: 'c-communication', heading: 'Describe a time you handled a conflict in a team.', difficulty: 'Medium', estimatedSeconds: 90 },
//       { id: 'q-5', categoryId: 'c-communication', heading: 'How do you improve your verbal communication skills?', difficulty: 'Easy', estimatedSeconds: 60 },
//       { id: 'q-6', categoryId: 'c-technical', heading: 'Explain Dependency Injection in .NET.', difficulty: 'Hard', estimatedSeconds: 120 },
//     ];
//   }

//   fetchQuestionsByCategory(categoryId: string) {
//     this.loadingQuestions = true;
//     this.questions = [];
//     setTimeout(() => {
//       const fetched = this.allQuestions
//         .filter((q) => q.categoryId === categoryId)
//         .sort((a, b) => (a.estimatedSeconds > b.estimatedSeconds ? -1 : 1));
//       this.questions = fetched;
//       this.loadingQuestions = false;
//     }, 500);
//   }

//   selectCategory(categoryId: string) {
//     this.categories = this.categories.map((c) => ({
//       ...c,
//       selected: c.id === categoryId,
//     }));
//     this.selectedCategoryId = categoryId;
//     this.fetchQuestionsByCategory(categoryId);
//   }

//   get filteredQuestions(): Question[] {
//     const q = this.searchQuery.trim().toLowerCase();
//     if (!q) return this.questions;
//     return this.questions.filter(
//       (item) =>
//         item.heading.toLowerCase().includes(q) ||
//         item.difficulty.toLowerCase().includes(q) ||
//         (item.details && item.details.toLowerCase().includes(q))
//     );
//   }

//   isQuestionInBooklet(questionId: string): boolean {
//     return this.booklet.some((b) => b.question.id === questionId);
//   }

//   addToBooklet(question: Question) {
//     if (this.isQuestionInBooklet(question.id)) return;
//     this.booklet.push({ question, addedAt: new Date() });
//     this.recalculateDuration();
//     console.log('Added to booklet:', question);
//   }

//   removeFromBooklet(questionId: string) {
//     this.booklet = this.booklet.filter((b) => b.question.id !== questionId);
//     this.recalculateDuration();
//     console.log('Removed from booklet:', questionId);
//   }

//   recalculateDuration() {
//     this.interviewDurationSeconds = this.booklet.reduce(
//       (sum, b) => sum + b.question.estimatedSeconds,
//       0
//     );
//   }

//   formatDuration(seconds: number) {
//     const hrs = Math.floor(seconds / 3600);
//     const mins = Math.floor((seconds % 3600) / 60);
//     const secs = seconds % 60;
//     const pad = (n: number) => String(n).padStart(2, '0');
//     return { hrs: pad(hrs), mins: pad(mins), secs: pad(secs) };
//   }

//   openCreateCategoryModal() {
//     this.showCreateCategoryModal = true;
//   }

//   cancelCreateCategory() {
//     this.showCreateCategoryModal = false;
//     this.createCategoryForm.reset();
//   }

//   submitCreateCategory() {
//     if (this.createCategoryForm.invalid) {
//       this.createCategoryForm.markAllAsTouched();
//       return;
//     }
//     this.creatingCategory = true;
//     const val = this.createCategoryForm.value;
//     setTimeout(() => {
//       const newCat: Category = {
//         id: 'c-' + Math.random().toString(36).substring(2, 9),
//         name: val.name,
//         description: `${val.round} - ${val.role}`,
//         icon: '/assets/icons/new-category.svg',
//         questionCount: 0,
//       };
//       this.categories.push(newCat);
//       console.log('Created category:', newCat);
//       this.creatingCategory = false;
//       this.cancelCreateCategory();
//       this.selectCategory(newCat.id);
//     }, 500);
//   }

//   saveQuestionBank() {
//     console.log('Save Question Bank clicked. Booklet:', this.booklet, 'Candidate:', this.candidate);
//   }

//   scheduleInterview() {
//     if (!this.booklet.length) return;
//     const payload = {
//       bookletQuestions: this.booklet.map((b) => b.question.id),
//       durationSeconds: this.interviewDurationSeconds,
//       candidate: this.candidate || { id: 'candidate-123' }
//     };
//     console.log('Schedule Interview payload (stub):', payload);
//     console.log('Shared links to: Recruiter, AI Avatar Agent, Candidate (STUB).');
//   }

//   difficultyClass(d: Question['difficulty']) {
//     return {
//       Easy: 'badge-easy',
//       Medium: 'badge-medium',
//       Hard: 'badge-hard',
//     }[d];
//   }

//   trackByQuestion(index: number, q: Question) {
//     return q.id;
//   }
// }


// version 2
import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

/* Data interfaces */
export interface Category {
  id: string;
  name: string;
  description?: string;
  icon?: string;
  questionCount?: number;
  selected?: boolean;
}

export interface Question {
  id: string;
  categoryId: string;
  heading: string;
  difficulty: 'Easy' | 'Medium' | 'Hard';
  estimatedSeconds: number;
  details?: string;
}

export interface BookletItem {
  question: Question;
  addedAt: Date;
}

export interface CandidateNavInfo {
  fullName: string;
  appliedRole: string;
  requisitionId: string;
}

@Component({
  selector: 'app-question-bank-dashboard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './question-bank-dashboard.component.html',
  styleUrls: ['./question-bank-dashboard.component.scss'],
})
export class QuestionBankDashboardComponent implements OnInit {
  candidate: CandidateNavInfo | null = null;

  // UI state
  categories: Category[] = [];
  questions: Question[] = [];
  allQuestions: Question[] = [];
  selectedCategoryId: string | null = null;
  searchQuery = '';
  loadingQuestions = false;

  // Booklet
  booklet: BookletItem[] = [];

  // Modal for create category
  showCreateCategoryModal = false;
  createCategoryForm!: FormGroup;
  creatingCategory = false;

  // Total interview duration
  interviewDurationSeconds = 0;

  // Difficulty tags
  readonly difficulties = ['Easy', 'Medium', 'Hard'];

  constructor(
    private fb: FormBuilder,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: object
  ) {}

  ngOnInit(): void {
    console.log('QuestionBankDashboardComponent initializing...');
    
    // Load candidate info - only run this in browser
    if (isPlatformBrowser(this.platformId)) {
      this.loadCandidateInfo();
    }

    this.initMockData();

    this.createCategoryForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(80)]],
      round: ['', [Validators.required]],
      role: ['', [Validators.required]],
      description: [''],
    });

    if (this.categories.length) {
      this.selectCategory(this.categories[0].id);
    }
  }

  private loadCandidateInfo(): void {
    // First try to get candidate from router navigation state
    const nav = this.router.getCurrentNavigation();
    if (nav?.extras?.state?.['candidate']) {
      console.log('Loading candidate from navigation state:', nav.extras.state['candidate']);
      this.candidate = nav.extras.state['candidate'];
      // Store in sessionStorage for persistence
      sessionStorage.setItem('selectedCandidate', JSON.stringify(this.candidate));
      return;
    }

    // If not in navigation state, try sessionStorage
    try {
      const storedCandidate = sessionStorage.getItem('selectedCandidate');
      if (storedCandidate) {
        console.log('Loading candidate from sessionStorage:', storedCandidate);
        this.candidate = JSON.parse(storedCandidate);
      } else {
        console.warn('No candidate found in navigation state or sessionStorage');
        // Optionally redirect back to recruiter dashboard
        // this.router.navigate(['/recruiter-dashboard']);
      }
    } catch (error) {
      console.error('Error parsing candidate from sessionStorage:', error);
      sessionStorage.removeItem('selectedCandidate');
    }
  }

  initMockData() {
    this.categories = [
      {
        id: 'c-general',
        name: 'General Introduction Questions',
        description: 'Common candidate introduction questions',
        icon: '/assets/images/avatar.png',
        questionCount: 10,
      },
      {
        id: 'c-communication',
        name: 'Communication Skills',
        description: 'Articulative with the listening skills',
        icon: '/assets/images/coms.png',
        questionCount: 6,
      },
      {
        id: 'c-technical',
        name: 'Technical & Role-based',
        description: 'Role specific technical questions',
        icon: '/assets/images/technical.png',
        questionCount: 12,
      }
    ];

    this.allQuestions = [
      { id: 'q-1', categoryId: 'c-general', heading: 'Tell us a little bit about yourself?', difficulty: 'Easy', estimatedSeconds: 60 },
      { id: 'q-2', categoryId: 'c-general', heading: 'Why are you interested in this role?', difficulty: 'Medium', estimatedSeconds: 90 },
      { id: 'q-3', categoryId: 'c-general', heading: 'What are your strengths and weaknesses?', difficulty: 'Hard', estimatedSeconds: 120 },
      { id: 'q-4', categoryId: 'c-general', heading: 'Where do you see yourself in 5 years?', difficulty: 'Medium', estimatedSeconds: 90 },
      { id: 'q-5', categoryId: 'c-general', heading: 'Why should we hire you?', difficulty: 'Hard', estimatedSeconds: 120 },
      { id: 'q-6', categoryId: 'c-general', heading: 'What motivates you at work?', difficulty: 'Easy', estimatedSeconds: 60 },
      { id: 'q-7', categoryId: 'c-communication', heading: 'Describe a time you handled a conflict in a team.', difficulty: 'Medium', estimatedSeconds: 90 },
      { id: 'q-8', categoryId: 'c-communication', heading: 'How do you improve your verbal communication skills?', difficulty: 'Easy', estimatedSeconds: 60 },
      { id: 'q-9', categoryId: 'c-communication', heading: 'Give an example of when you had to explain a complex concept to someone.', difficulty: 'Hard', estimatedSeconds: 120 },
      { id: 'q-10', categoryId: 'c-technical', heading: 'Explain Dependency Injection in .NET.', difficulty: 'Hard', estimatedSeconds: 120 },
      { id: 'q-11', categoryId: 'c-technical', heading: 'What is the difference between Abstract class and Interface?', difficulty: 'Medium', estimatedSeconds: 90 },
      { id: 'q-12', categoryId: 'c-technical', heading: 'How does garbage collection work in .NET?', difficulty: 'Hard', estimatedSeconds: 120 },
    ];
  }

  fetchQuestionsByCategory(categoryId: string) {
    this.loadingQuestions = true;
    this.questions = [];
    setTimeout(() => {
      const fetched = this.allQuestions
        .filter((q) => q.categoryId === categoryId)
        .sort((a, b) => (a.estimatedSeconds > b.estimatedSeconds ? -1 : 1));
      this.questions = fetched;
      this.loadingQuestions = false;
      console.log(`Loaded ${fetched.length} questions for category: ${categoryId}`);
    }, 500);
  }

  selectCategory(categoryId: string) {
    this.categories = this.categories.map((c) => ({
      ...c,
      selected: c.id === categoryId,
    }));
    this.selectedCategoryId = categoryId;
    this.fetchQuestionsByCategory(categoryId);
  }

  get filteredQuestions(): Question[] {
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) return this.questions;
    return this.questions.filter(
      (item) =>
        item.heading.toLowerCase().includes(q) ||
        item.difficulty.toLowerCase().includes(q) ||
        (item.details && item.details.toLowerCase().includes(q))
    );
  }

  isQuestionInBooklet(questionId: string): boolean {
    return this.booklet.some((b) => b.question.id === questionId);
  }

  addToBooklet(question: Question) {
    if (this.isQuestionInBooklet(question.id)) return;
    this.booklet.push({ question, addedAt: new Date() });
    this.recalculateDuration();
    console.log('Added to booklet:', question.heading);
  }

  removeFromBooklet(questionId: string) {
    const question = this.booklet.find(b => b.question.id === questionId)?.question;
    this.booklet = this.booklet.filter((b) => b.question.id !== questionId);
    this.recalculateDuration();
    console.log('Removed from booklet:', question?.heading);
  }

  recalculateDuration() {
    this.interviewDurationSeconds = this.booklet.reduce(
      (sum, b) => sum + b.question.estimatedSeconds,
      0
    );
  }

  formatDuration(seconds: number) {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    const pad = (n: number) => String(n).padStart(2, '0');
    return { hrs: pad(hrs), mins: pad(mins), secs: pad(secs) };
  }

  openCreateCategoryModal() {
    this.showCreateCategoryModal = true;
  }

  cancelCreateCategory() {
    this.showCreateCategoryModal = false;
    this.createCategoryForm.reset();
  }

  submitCreateCategory() {
    if (this.createCategoryForm.invalid) {
      this.createCategoryForm.markAllAsTouched();
      return;
    }
    this.creatingCategory = true;
    const val = this.createCategoryForm.value;
    setTimeout(() => {
      const newCat: Category = {
        id: 'c-' + Math.random().toString(36).substring(2, 9),
        name: val.name,
        description: `${val.round} - ${val.role}`,
        icon: '/assets/icons/new-category.svg',
        questionCount: 0,
      };
      this.categories.push(newCat);
      console.log('Created category:', newCat);
      this.creatingCategory = false;
      this.cancelCreateCategory();
      this.selectCategory(newCat.id);
    }, 500);
  }

  saveQuestionBank() {
    console.log('Save Question Bank clicked. Booklet:', this.booklet, 'Candidate:', this.candidate);
    alert('Question bank saved successfully!');
  }

  scheduleInterview() {
    if (!this.booklet.length) {
      alert('Please add at least one question to the booklet before scheduling.');
      return;
    }
    
    const payload = {
      bookletQuestions: this.booklet.map((b) => b.question.id),
      durationSeconds: this.interviewDurationSeconds,
      candidate: this.candidate
    };
    
    console.log('Schedule Interview payload:', payload);
    alert(`Interview scheduled for ${this.candidate?.fullName || 'Selected Candidate'} with ${this.booklet.length} questions (${this.formatDuration(this.interviewDurationSeconds).hrs}:${this.formatDuration(this.interviewDurationSeconds).mins}:${this.formatDuration(this.interviewDurationSeconds).secs})`);
  }

  difficultyClass(d: Question['difficulty']) {
    return {
      Easy: 'badge-easy',
      Medium: 'badge-medium',
      Hard: 'badge-hard',
    }[d];
  }

  trackByQuestion(index: number, q: Question) {
    return q.id;
  }
}