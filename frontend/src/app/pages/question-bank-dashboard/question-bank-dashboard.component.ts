import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
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

@Component({
  selector: 'app-question-bank-dashboard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './question-bank-dashboard.component.html',
  styleUrls: ['./question-bank-dashboard.component.scss'],
})
export class QuestionBankDashboardComponent implements OnInit {
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

  // Candidate details passed from recruiter dashboard
  candidateData: any = null;

  constructor(
    private fb: FormBuilder,
    private router: Router
  ) {}

  ngOnInit(): void {
    const nav = this.router.getCurrentNavigation();
    if (nav?.extras?.state?.['candidate']) {
      this.candidateData = nav.extras.state['candidate'];
      sessionStorage.setItem('selectedCandidate', JSON.stringify(this.candidateData));
    } else {
      // Fallback to session storage
      const storedCandidate = sessionStorage.getItem('selectedCandidate');
      if (storedCandidate) {
        this.candidateData = JSON.parse(storedCandidate);
      }
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
      { id: 'q-4', categoryId: 'c-communication', heading: 'Describe a time you handled a conflict in a team.', difficulty: 'Medium', estimatedSeconds: 90 },
      { id: 'q-5', categoryId: 'c-communication', heading: 'How do you improve your verbal communication skills?', difficulty: 'Easy', estimatedSeconds: 60 },
      { id: 'q-6', categoryId: 'c-technical', heading: 'Explain Dependency Injection in .NET.', difficulty: 'Hard', estimatedSeconds: 120 },
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
    console.log('Added to booklet:', question);
  }

  removeFromBooklet(questionId: string) {
    this.booklet = this.booklet.filter((b) => b.question.id !== questionId);
    this.recalculateDuration();
    console.log('Removed from booklet:', questionId);
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
    console.log('Save Question Bank clicked. Booklet:', this.booklet, 'Candidate:', this.candidateData);
  }

  scheduleInterview() {
    if (!this.booklet.length) return;
    const payload = {
      bookletQuestions: this.booklet.map((b) => b.question.id),
      durationSeconds: this.interviewDurationSeconds,
      candidate: this.candidateData || { id: 'candidate-123' }
    };
    console.log('Schedule Interview payload (stub):', payload);
    console.log('Shared links to: Recruiter, AI Avatar Agent, Candidate (STUB).');
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
