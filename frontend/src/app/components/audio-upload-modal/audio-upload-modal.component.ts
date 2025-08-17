import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  selector: 'app-audio-upload-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './audio-upload-modal.component.html',
  styleUrls: ['./audio-upload-modal.component.scss']
})
export class AudioUploadModalComponent {
  @Input() candidate: any = null;
  @Input() candidateName = '';
  @Input() candidateId: number | null = null;
  @Input() visible: boolean = false;
  @Output() close = new EventEmitter<void>();
  @Output() upload = new EventEmitter<{candidateId: number, audioFile: File}>();

  uploadForm: FormGroup;
  selectedFile: File | null = null;
  dragOver = false;
  uploading = false;
  validationErrors: string[] = [];

  // Audio validation constraints
  readonly MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
  readonly MIN_DURATION = 5; // seconds
  readonly MAX_DURATION = 10; // seconds
  readonly ALLOWED_TYPES = ['audio/mpeg', 'audio/mp3', 'audio/wav', 'audio/x-wav'];
  readonly ALLOWED_EXTENSIONS = ['.mp3', '.wav'];

  constructor(private fb: FormBuilder) {
    this.uploadForm = this.fb.group({
      audioFile: [null, Validators.required],
      description: ['Phone screening audio clip', Validators.maxLength(200)]
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.handleFileSelection(input.files[0]);
    }
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = false;

    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      this.handleFileSelection(event.dataTransfer.files[0]);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = false;
  }

  private handleFileSelection(file: File): void {
    this.validationErrors = [];
    
    // Reset previous selection
    this.selectedFile = null;
    this.uploadForm.get('audioFile')?.setValue(null);

    // Validate file
    if (this.validateFile(file)) {
      this.selectedFile = file;
      this.uploadForm.get('audioFile')?.setValue(file);
      
      // Optionally validate duration
      this.validateAudioDuration(file);
    }
  }

  private validateFile(file: File): boolean {
    const errors: string[] = [];

    // Check file type
    if (!this.ALLOWED_TYPES.includes(file.type) && !this.hasAllowedExtension(file.name)) {
      errors.push('Please select an MP3 or WAV audio file');
    }

    // Check file size
    if (file.size > this.MAX_FILE_SIZE) {
      errors.push(`File size must be less than ${this.MAX_FILE_SIZE / 1024 / 1024}MB`);
    }

    // Check if file is empty
    if (file.size === 0) {
      errors.push('File cannot be empty');
    }

    this.validationErrors = errors;
    return errors.length === 0;
  }

  private hasAllowedExtension(fileName: string): boolean {
    return this.ALLOWED_EXTENSIONS.some(ext => 
      fileName.toLowerCase().endsWith(ext)
    );
  }

  private validateAudioDuration(file: File): void {
    const audio = new Audio();
    const objectUrl = URL.createObjectURL(file);
    
    audio.addEventListener('loadedmetadata', () => {
      const duration = audio.duration;
      
      if (duration < this.MIN_DURATION) {
        this.validationErrors.push(`Audio must be at least ${this.MIN_DURATION} seconds long`);
      } else if (duration > this.MAX_DURATION) {
        this.validationErrors.push(`Audio must be no longer than ${this.MAX_DURATION} seconds`);
      }
      
      // Clean up
      URL.revokeObjectURL(objectUrl);
    });

    audio.addEventListener('error', () => {
      this.validationErrors.push('Could not validate audio duration. File may be corrupted.');
      URL.revokeObjectURL(objectUrl);
    });

    audio.src = objectUrl;
  }

  removeFile(): void {
    this.selectedFile = null;
    this.uploadForm.get('audioFile')?.setValue(null);
    this.validationErrors = [];
  }

  onSubmit(): void {
    if (this.uploadForm.invalid || !this.selectedFile || !this.candidateId) {
      return;
    }

    if (this.validationErrors.length > 0) {
      return;
    }

    this.uploading = true;
    
    // Emit upload event with candidate ID and file
    this.upload.emit({
      candidateId: this.candidateId,
      audioFile: this.selectedFile
    });
  }

  onCancel(): void {
    this.resetForm();
    this.close.emit();
  }

  private resetForm(): void {
    this.uploadForm.reset();
    this.selectedFile = null;
    this.validationErrors = [];
    this.uploading = false;
    this.dragOver = false;
    this.uploadForm.get('description')?.setValue('Phone screening audio clip');
  }

  // Public method for parent to reset upload state
  resetUploadState(): void {
    this.uploading = false;
  }

  // Helper methods for template
  get fileSize(): string {
    if (!this.selectedFile) return '';
    const size = this.selectedFile.size;
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }

  get fileName(): string {
    return this.selectedFile?.name || '';
  }
}
