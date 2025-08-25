import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, of } from 'rxjs';
import { catchError, retry } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { VALIDATION_MESSAGES } from '../constants/candidate.constants';

export interface Candidate {
  id?: number;
  requisitionId: string;
  name: string;
  email: string;
  phone: string;  // Fixed: Changed from phoneNumber to phone to match backend
  appliedRole: string;
  applicationDate: string;
  totalExperience: number;
  relevantExperience: number;
  interviewRound: string;
  status?: string;
  jobDescription: string;
  keyResponsibilities?: string;
  skills?: string[];
  profilePicUrl?: string; // Fixed: Match backend field name (removed 'ture' typo)
  resumeUrl?: string;
}

@Injectable({
  providedIn: 'root'
})
export class CandidateService {
  private baseUrl = `${environment.apiBaseUrl}/candidates`; // Fixed: Removed duplicate /api

  constructor(private http: HttpClient, @Inject(PLATFORM_ID) private platformId: Object) {}

  // Comprehensive error handling method
  private handleError = (error: HttpErrorResponse) => {
    let errorMessage = 'An unknown error occurred';
    let userMessage = 'Something went wrong. Please try again.';

    if (error.error instanceof ErrorEvent) {
      // Client-side or network error
      errorMessage = `Client Error: ${error.error.message}`;
      userMessage = 'Network error. Please check your connection.';
    } else {
      // Backend error
      switch (error.status) {
        case 0:
          errorMessage = 'Network unavailable';
          userMessage = 'Unable to connect to server. Please check your internet connection.';
          break;
        case 400:
          errorMessage = `Bad Request: ${error.error?.message || 'Invalid data provided'}`;
          userMessage = error.error?.message || 'Please check the information provided and try again.';
          break;
        case 401:
          errorMessage = 'Unauthorized access';
          userMessage = 'Your session has expired. Please log in again.';
          break;
        case 403:
          errorMessage = 'Access forbidden';
          userMessage = 'You don\'t have permission to perform this action.';
          break;
        case 404:
          errorMessage = 'Resource not found';
          userMessage = 'The requested candidate was not found.';
          break;
        case 409:
          errorMessage = 'Conflict - resource already exists';
          userMessage = 'A candidate with this information already exists.';
          break;
        case 413:
          errorMessage = 'File too large';
          userMessage = 'The uploaded file is too large. Please choose a smaller file.';
          break;
        case 422:
          errorMessage = 'Validation failed';
          userMessage = error.error?.message || 'Please check the information provided.';
          break;
        case 500:
          errorMessage = 'Internal server error';
          userMessage = 'Server error occurred. Please try again later.';
          break;
        case 503:
          errorMessage = 'Service unavailable';
          userMessage = 'Service is temporarily unavailable. Please try again later.';
          break;
        default:
          errorMessage = `Server Error Code: ${error.status}\\nMessage: ${error.error?.message || error.message}`;
          userMessage = 'An unexpected error occurred. Please try again.';
      }
    }

    console.error('CandidateService Error:', errorMessage);
    
    // Return error with both technical details and user-friendly message
    return throwError(() => ({
      technical: errorMessage,
      user: userMessage,
      status: error.status,
      originalError: error
    }));
  };

  // Helper method to get authentication headers
  private getAuthHeaders(): any {
    if (isPlatformBrowser(this.platformId)) {
      const token = localStorage.getItem('auth_token');
      if (token) {
        return {
          Authorization: `Bearer ${token}`
        };
      }
    }
    return {};
  }

  addCandidate(candidate: Candidate, resumeFile: File, profilePicture?: File): Observable<any> {
    try {
      const formData = new FormData();
      formData.append('data', new Blob([JSON.stringify(candidate)], { type: 'application/json' }));
      formData.append('resume', resumeFile);
      if (profilePicture) {
        formData.append('profilePic', profilePicture);
      }
      
      console.log('Adding candidate:', candidate.name);
      
      return this.http.post(`${this.baseUrl}`, formData, {
        headers: this.getAuthHeaders()
      }).pipe(
        retry(1), // Retry once on failure
        catchError(this.handleError)
      );
    } catch (error) {
      console.error('Error preparing candidate data:', error);
      return throwError(() => ({
        technical: 'Failed to prepare candidate data',
        user: 'Please check the form data and try again.',
        status: 0
      }));
    }
  }

  uploadResume(resumeFile: File): Observable<any> {
    const formData = new FormData();
    formData.append('resume', resumeFile);
    
    console.log('Uploading resume file:', resumeFile.name);
    
    return this.http.post(`${this.baseUrl}/upload-resume`, formData, {
      headers: this.getAuthHeaders()
    }).pipe(
      retry(1),
      catchError(this.handleError)
    );
  }

  uploadAudio(candidateId: number, audioFile: File): Observable<any> {
    if (!candidateId || candidateId <= 0) {
      return throwError(() => ({
        technical: 'Invalid candidate ID for audio upload',
        user: 'Invalid candidate selected for audio upload.',
        status: 400
      }));
    }
    
    if (!audioFile) {
      return throwError(() => ({
        technical: 'No audio file provided',
        user: 'Please select an audio file to upload.',
        status: 400
      }));
    }
    
    try {
      const formData = new FormData();
      formData.append('audio', audioFile);
      
      console.log('Uploading audio file for candidate:', candidateId, 'File:', audioFile.name);
      
      return this.http.post(`${this.baseUrl}/${candidateId}/upload-audio`, formData, {
        headers: this.getAuthHeaders()
      }).pipe(
        retry(1),
        catchError(this.handleError)
      );
    } catch (error) {
      console.error('Error preparing audio upload data:', error);
      return throwError(() => ({
        technical: 'Failed to prepare audio upload data',
        user: 'Please check the audio file and try again.',
        status: 0
      }));
    }
  }

  getAllCandidates(): Observable<Candidate[]> {
    if (isPlatformBrowser(this.platformId)) {
      console.log('Fetching all candidates');
      
      return this.http.get<Candidate[]>(`${this.baseUrl}`, {
        headers: this.getAuthHeaders()
      }).pipe(
        retry(2), // Retry twice for GET requests
        catchError(this.handleError)
      );
    } else {
      return of([]);
    }
  }

  getCandidateById(id: number): Observable<Candidate> {
    if (!id || id <= 0) {
      return throwError(() => ({
        technical: 'Invalid candidate ID',
        user: 'Invalid candidate selected.',
        status: 400
      }));
    }
    
    console.log('Fetching candidate by ID:', id);
    
    return this.http.get<Candidate>(`${this.baseUrl}/${id}`, {
      headers: this.getAuthHeaders()
    }).pipe(
      retry(1),
      catchError(this.handleError)
    );
  }

  updateCandidate(id: number, candidate: Candidate, resumeFile?: File, profilePicture?: File): Observable<any> {
    if (!id || id <= 0) {
      return throwError(() => ({
        technical: 'Invalid candidate ID for update',
        user: 'Invalid candidate selected for update.',
        status: 400
      }));
    }
    
    try {
      console.log('CandidateService.updateCandidate called with ID:', id);
      console.log('Candidate data:', candidate);
      
      const formData = new FormData();
      formData.append('data', new Blob([JSON.stringify(candidate)], { type: 'application/json' }));
      
      if (resumeFile) {
        formData.append('resume', resumeFile);
        console.log('Resume file included:', resumeFile.name);
      }
      
      if (profilePicture) {
        formData.append('profilePic', profilePicture);
        console.log('Profile picture included:', profilePicture.name);
      }
      
      return this.http.put(`${this.baseUrl}/${id}`, formData, {
        headers: this.getAuthHeaders()
      }).pipe(
        retry(1),
        catchError(this.handleError)
      );
    } catch (error) {
      console.error('Error preparing update data:', error);
      return throwError(() => ({
        technical: 'Failed to prepare update data',
        user: 'Please check the form data and try again.',
        status: 0
      }));
    }
  }

  deleteCandidate(id: number): Observable<any> {
    if (!id || id <= 0) {
      return throwError(() => ({
        technical: 'Invalid candidate ID for deletion',
        user: 'Invalid candidate selected for deletion.',
        status: 400
      }));
    }
    
    console.log('Deleting candidate with ID:', id);
    
    return this.http.delete(`${this.baseUrl}/${id}`, {
      headers: this.getAuthHeaders()
    }).pipe(
      retry(1),
      catchError(this.handleError)
    );
  }

  // Utility method to validate file constraints
  validateFile(file: File, type: 'resume' | 'profilePic' | 'audio'): string | null {
    let constraints: { maxSize: number; types: string[] };
    
    switch (type) {
      case 'resume':
        constraints = { maxSize: 5 * 1024 * 1024, types: ['.pdf', '.doc', '.docx'] };
        break;
      case 'profilePic':
        constraints = { maxSize: 2 * 1024 * 1024, types: ['.jpg', '.jpeg', '.png', '.gif'] };
        break;
      case 'audio':
        constraints = { maxSize: 25 * 1024 * 1024, types: ['.mp3', '.wav', '.ogg', '.m4a', '.flac'] };
        break;
      default:
        return 'Unknown file type';
    }
    
    if (file.size > constraints.maxSize) {
      return `File size cannot exceed ${Math.round(constraints.maxSize / (1024 * 1024))}MB`;
    }
    
    const extension = '.' + file.name.split('.').pop()?.toLowerCase();
    if (!constraints.types.includes(extension)) {
      return `Invalid file type. Allowed types: ${constraints.types.join(', ')}`;
    }
    
    return null; // No validation errors
  }
}
