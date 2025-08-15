import { Component, EventEmitter, Input, Output, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, FormGroup } from '@angular/forms';

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

  form: FormGroup;

  pictureName = '';
  resumeName = '';

  
  ngOnInit() {
    this.form = this.fb.group({
      requisitionId: ['', Validators.required],
      candidateName: ['', Validators.required],
      candidateEmail: ['', [Validators.required, Validators.email]],
      candidatePhone: ['', Validators.required],
      appliedRole: ['', Validators.required],
      applicationDate: ['', Validators.required],
      totalExperience: ['', Validators.required],
      relevantExperience: ['', Validators.required],
      interviewRound: ['', Validators.required],
      status: [''],
      jobDescription: ['', Validators.required],
      keyResponsibilities: [''],
      uploadResume: ['', Validators.required]
    });

    if (!this.mode) {
      this.mode = 'add';
    }
  }

  interviewRounds = [
    'Technical - T1',
    'Technical - T2',
    'HR Round',
    'Managerial Round',
    'Screening'
  ];

  statusOptions = [
    'Yet to schedule T1 interview',
    'Scheduled',
    'On Hold',
    'Rejected',
    'Selected'
  ];

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      requisitionId: ['', Validators.required],
      candidateName: ['', Validators.required],
      appliedRole: ['', Validators.required],
      totalExperience: [null, [Validators.required, Validators.min(0)]],
      relevantExperience: [null, [Validators.required, Validators.min(0)]],
      interviewRound: ['', Validators.required],
      applicationDate: ['', Validators.required],
      status: [{ value: 'Yet to schedule T1 interview', disabled: false }],
      uploadPicture: [null],
      uploadResume: [null, Validators.required],
      jobDescription: ['', Validators.required],
      keyResponsibilities: ['']
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    // Pre-fill data when switching to edit mode
    if (changes['candidateData'] && this.mode === 'edit' && this.candidateData) {
      this.form.patchValue({
        requisitionId: this.candidateData.requisitionId || '',
        candidateName: this.candidateData.candidateName || '',
        appliedRole: this.candidateData.appliedRole || '',
        totalExperience: this.candidateData.totalExperience || 0,
        relevantExperience: this.candidateData.relevantExperience || 0,
        interviewRound: this.candidateData.interviewRound || '',
        applicationDate: this.candidateData.applicationDate || '',
        status: this.candidateData.status || 'Yet to schedule T1 interview',
        jobDescription: this.candidateData.jobDescription || '',
        keyResponsibilities: this.candidateData.keyResponsibilities || ''
      });

      // Set file names if available
      this.pictureName = this.candidateData.uploadPicture || '';
      this.resumeName = this.candidateData.uploadResume || '';
    }

    // Reset form when switching back to add mode
    if (changes['mode'] && this.mode === 'add' && !this.candidateData) {
      this.form.reset({ status: 'Yet to schedule T1 interview' });
      this.resetFiles();
    }
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
      return;
    }
  
    const raw = this.form.getRawValue();
  
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
      recruiterId: ''
    };
  
    this.save.emit(payload);
  }
  

  cancel() {
    this.form.markAsPristine();
    this.resetFiles();
    this.close.emit();
  }
}