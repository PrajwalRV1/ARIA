import { Component, EventEmitter, Input, Output, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, FormGroup } from '@angular/forms';
import { INTERVIEW_ROUNDS, CANDIDATE_STATUS } from '../../constants/candidate.constants';

@Component({
  selector: 'app-add-candidate-popup',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './add-candidate-popup.component.html',
  styleUrls: ['./add-candidate-popup.component.scss']
})
export class AddCandidatePopupComponent implements OnInit, OnChanges {
  @Input() visible = false;               
  @Input() mode: 'add' | 'edit' = 'add';  
  @Input() candidateData: any = null;     
  @Output() close = new EventEmitter<void>();
  @Output() save = new EventEmitter<any>();  // New output for save event

  form!: FormGroup; // Definite assignment assertion - initialized in ngOnInit

  pictureName = '';
  resumeName = '';

  
  ngOnInit() {
    // Initialize form with proper validation and default values
    this.form = this.fb.group({
      requisitionId: ['', Validators.required],
      candidateName: ['', Validators.required],
      candidateEmail: ['', [Validators.required, Validators.email]],
      candidatePhone: ['', Validators.required],
      appliedRole: ['', Validators.required],
      applicationDate: ['', Validators.required],
      totalExperience: [null, [Validators.required, Validators.min(0)]],
      relevantExperience: [null, [Validators.required, Validators.min(0)]],
      interviewRound: ['', Validators.required],
      status: ['PENDING'], // Default status for new candidates
      jobDescription: ['', Validators.required],
      keyResponsibilities: [''], // Optional field
      uploadPicture: [null], // Optional file
      uploadResume: [null, Validators.required] // Required file
    });

    // Ensure mode is set
    if (!this.mode) {
      this.mode = 'add';
    }
    
    console.log('AddCandidatePopup initialized in mode:', this.mode);
  }

  // Use shared constants for consistency across the application
  interviewRounds = INTERVIEW_ROUNDS;
  statusOptions = CANDIDATE_STATUS;

  constructor(private fb: FormBuilder) {
    // Form will be initialized in ngOnInit to avoid duplication
  }

  ngOnChanges(changes: SimpleChanges) {
    // Pre-fill data when switching to edit mode OR when candidateData changes
    if ((changes['candidateData'] || changes['mode']) && this.mode === 'edit' && this.candidateData) {
      this.initializeEditForm();
    }

    // Reset form when switching back to add mode
    if (changes['mode'] && this.mode === 'add' && !this.candidateData) {
      this.form.reset({ status: 'PENDING' });
      // Re-add resume validation for add mode
      this.form.get('uploadResume')?.setValidators([Validators.required]);
      this.form.get('uploadResume')?.updateValueAndValidity();
      this.resetFiles();
    }
  }

  private initializeEditForm() {
    if (!this.candidateData) return;
    
    console.log('Initializing edit form with data:', this.candidateData);
    
    // Format application date for HTML date input (YYYY-MM-DD)
    let formattedDate = '';
    if (this.candidateData.applicationDate) {
      const date = new Date(this.candidateData.applicationDate);
      formattedDate = date.toISOString().split('T')[0];
    }
    
    this.form.patchValue({
      requisitionId: this.candidateData.requisitionId || '',
      candidateName: this.candidateData.name || '', // Fixed: Use 'name' from backend
      candidateEmail: this.candidateData.email || '', // Fixed: Map email field
      candidatePhone: this.candidateData.phone || '', // Fixed: Map phone field
      appliedRole: this.candidateData.appliedRole || this.candidateData.jobTitle || '', // Fixed: Map applied role
      applicationDate: formattedDate,
      totalExperience: this.candidateData.totalExperience || 0,
      relevantExperience: this.candidateData.relevantExperience || 0,
      interviewRound: this.candidateData.interviewRound || '',
      status: this.candidateData.status || 'PENDING',
      jobDescription: this.candidateData.jobDescription || '',
      keyResponsibilities: this.candidateData.keyResponsibilities || ''
    });

    // Set file names if available
    this.pictureName = this.candidateData.profilePicFileName || '';
    this.resumeName = this.candidateData.resumeFileName || '';
    
    // Remove resume required validation in edit mode since candidate already has one
    this.form.get('uploadResume')?.clearValidators();
    this.form.get('uploadResume')?.updateValueAndValidity();
    
    // Mark form as pristine after initialization
    this.form.markAsPristine();
  }

  get f() { return this.form.controls; }

  onClose() {
    this.resetFiles();
    this.close.emit();
  }

  onOverlayClick(e: MouseEvent) {
    if ((e.target as HTMLElement).classList.contains('overlay')) {
      this.onClose();
    }
  }

  onPictureSelected(ev: Event) {
    const input = ev.target as HTMLInputElement;
    if (input.files && input.files.length) {
      const file = input.files[0];
      this.pictureName = file.name;
      this.form.patchValue({ uploadPicture: file });
    } else {
      this.resetPicture();
    }
  }

  onResumeSelected(ev: Event) {
    const input = ev.target as HTMLInputElement;
    if (input.files && input.files.length) {
      const file = input.files[0];
      this.resumeName = file.name;
      this.form.patchValue({ uploadResume: file });
      this.form.get('uploadResume')?.markAsTouched();
    } else {
      this.resetResume();
    }
  }

  resetPicture() {
    this.pictureName = '';
    this.form.patchValue({ uploadPicture: null });
  }

  resetResume() {
    this.resumeName = '';
    this.form.patchValue({ uploadResume: null });
  }

  resetFiles() {
    this.resetPicture();
    this.resetResume();
  }

  saveCandidate() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      console.log('Form is invalid:', this.form.errors);
      return;
    }
  
    const raw = this.form.getRawValue();
    console.log('Form raw values:', raw);
  
    const payload = {
      requisitionId: raw.requisitionId,
      name: raw.candidateName,       // map to DTO name
      email: raw.candidateEmail,
      phone: raw.candidatePhone,
      appliedRole: raw.appliedRole,
      applicationDate: raw.applicationDate,
      totalExperience: raw.totalExperience,
      relevantExperience: raw.relevantExperience,
      interviewRound: raw.interviewRound,
      status: raw.status,
      jobDescription: raw.jobDescription,
      keyResponsibilities: raw.keyResponsibilities,
      skills: [],                    // optional, add from another control
      source: '',
      notes: '',
      tags: '',
      recruiterId: '',
      // Include file objects for form data
      uploadResume: raw.uploadResume,
      uploadPicture: raw.uploadPicture
    };
    
    console.log('Emitting payload for save:', payload);
    this.save.emit(payload);
  }
  

  cancel() {
    this.form.markAsPristine();
    this.resetFiles();
    this.close.emit();
  }
}