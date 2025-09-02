import { Component, EventEmitter, Input, Output, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, FormGroup } from '@angular/forms';
import { INTERVIEW_ROUNDS, CANDIDATE_STATUS, STATUS_LABELS } from '../../constants/candidate.constants';

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
      candidatePhone: ['', [Validators.required, Validators.pattern(/^[+]?[1-9]\d{1,14}$|^\d{10}$/)]],
      appliedRole: ['', Validators.required],
      applicationDate: ['', Validators.required],
      totalExperience: [null, [Validators.required, Validators.min(0)]],
      relevantExperience: [null, [Validators.required, Validators.min(0)]],
      interviewRound: ['', Validators.required],
      status: ['APPLIED'], // Default status for new candidates (changed from PENDING)
      jobDescription: ['', Validators.required],
      keyResponsibilities: [''], // Optional field
      uploadPicture: [null], // Optional file
      uploadResume: [null] // CHANGED: Made resume optional in form validation
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
    // Only proceed if form is initialized
    if (!this.form) {
      return;
    }
    
    // Pre-fill data when switching to edit mode OR when candidateData changes
    if ((changes['candidateData'] || changes['mode']) && this.mode === 'edit' && this.candidateData) {
      this.initializeEditForm();
    }

    // Reset form when switching back to add mode
    if (changes['mode'] && this.mode === 'add' && !this.candidateData) {
      this.form.reset({ 
        status: 'APPLIED' // Use APPLIED as default instead of PENDING
      });
      // Resume is optional in form validation, but we'll still show it as recommended
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
      console.log('Form control errors:');
      Object.keys(this.form.controls).forEach(key => {
        const control = this.form.get(key);
        if (control && control.errors) {
          console.log(`- ${key}:`, control.errors);
        }
      });
      
      // Show user-friendly error message
      alert('⚠️ Please fill in all required fields before saving.');
      return;
    }
  
    const raw = this.form.getRawValue();
    console.log('Form raw values:', raw);
  
    // Validate and format phone number to match backend pattern ^[+]?[0-9]{10,15}$
    let formattedPhone = raw.candidatePhone;
    if (formattedPhone) {
      // Remove all non-numeric characters except +
      formattedPhone = formattedPhone.replace(/[^+0-9]/g, '');
      
      if (!formattedPhone.startsWith('+')) {
        // Remove leading zeros
        formattedPhone = formattedPhone.replace(/^0+/, '');
        
        // If it's exactly 10 digits, assume Indian number
        if (formattedPhone.length === 10) {
          formattedPhone = '+91' + formattedPhone;
        } else if (formattedPhone.length > 0) {
          formattedPhone = '+' + formattedPhone;
        }
      }
      
      // Ensure it matches the backend pattern: +[0-9]{10,15}
      if (!/^\+[0-9]{10,15}$/.test(formattedPhone)) {
        console.warn('⚠️ Phone number does not match backend pattern:', formattedPhone);
        // Try to fix it by keeping only + and digits
        formattedPhone = formattedPhone.replace(/[^+0-9]/g, '');
      }
    }
    
    // Validate application date format (should be YYYY-MM-DD and not in future)
    let applicationDate = raw.applicationDate;
    if (applicationDate) {
      const date = new Date(applicationDate);
      const today = new Date();
      
      if (!isNaN(date.getTime())) {
        // Check if date is in the future
        if (date > today) {
          console.warn('⚠️ Application date is in the future, setting to today:', applicationDate);
          applicationDate = today.toISOString().split('T')[0];
        } else {
          // Ensure date is in YYYY-MM-DD format
          applicationDate = date.toISOString().split('T')[0];
        }
      }
    }
    
    console.log('🔍 Data validation and formatting:');
    console.log('  - Original phone:', raw.candidatePhone, '→ Formatted:', formattedPhone);
    console.log('  - Application date:', applicationDate);
    console.log('  - Status value:', raw.status);
    
    const payload = {
      requisitionId: raw.requisitionId?.trim(),
      name: raw.candidateName?.trim(),
      email: raw.candidateEmail?.trim()?.toLowerCase(),
      phone: formattedPhone,
      appliedRole: raw.appliedRole?.trim(),
      applicationDate: applicationDate,
      totalExperience: parseFloat(raw.totalExperience) || 0.0,
      relevantExperience: parseFloat(raw.relevantExperience) || 0.0,
      interviewRound: raw.interviewRound?.trim(),
      status: raw.status || 'PENDING', // Ensure valid status
      jobDescription: raw.jobDescription?.trim(),
      keyResponsibilities: raw.keyResponsibilities?.trim() || '',
      skills: [],
      source: 'Manual Entry',
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

  // Helper method to get user-friendly status labels
  getStatusLabel(status: string): string {
    return STATUS_LABELS[status] || status;
  }

  // Helper method to get today's date in YYYY-MM-DD format for date input max attribute
  getTodayDate(): string {
    return new Date().toISOString().split('T')[0];
  }
}
