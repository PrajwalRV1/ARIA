import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, of, BehaviorSubject } from 'rxjs';
import { catchError, retry, tap, debounceTime, distinctUntilChanged, switchMap, shareReplay } from 'rxjs/operators';
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
  
  // Performance optimization: Cache observables for frequent requests
  private candidatesCache$ = new BehaviorSubject<Candidate[] | null>(null);
  private cacheTimestamp = 0;
  private readonly CACHE_DURATION = 2 * 60 * 1000; // 2 minutes

  constructor(
    private http: HttpClient, 
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

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
          console.error('🔍 400 Bad Request Details:', {
            status: error.status,
            statusText: error.statusText,
            url: error.url,
            errorBody: error.error,
            headers: error.headers
          });
          
          let detailedMessage = 'Invalid data provided';
          if (error.error) {
            if (typeof error.error === 'string') {
              detailedMessage = error.error;
            } else if (error.error.message) {
              detailedMessage = error.error.message;
            } else if (error.error.error) {
              detailedMessage = error.error.error;
            } else {
              detailedMessage = JSON.stringify(error.error);
            }
          }
          
          errorMessage = `Bad Request: ${detailedMessage}`;
          userMessage = detailedMessage || 'Please check the information provided and try again.';
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
        console.log('Using auth token for candidate request:', token.substring(0, 20) + '...');
        return {
          Authorization: `Bearer ${token}`
        };
      } else {
        console.warn('No auth token found in localStorage for candidate request');
      }
    }
    return {};
  }

  addCandidate(candidate: Candidate, resumeFile?: File, profilePicture?: File): Observable<any> {
    console.log('🚀 CandidateService.addCandidate() called');
    console.log('📋 Input parameters:');
    console.log('  - Candidate:', candidate);
    console.log('  - Resume file:', resumeFile ? `${resumeFile.name} (${resumeFile.size} bytes, ${resumeFile.type})` : 'None');
    console.log('  - Profile pic:', profilePicture ? `${profilePicture.name} (${profilePicture.size} bytes, ${profilePicture.type})` : 'None');
    
    try {
      const formData = new FormData();
      
      // Append JSON data as a proper JSON part (backend expects @RequestPart("data"))
      const jsonBlob = new Blob([JSON.stringify(candidate)], { type: 'application/json' });
      formData.append('data', jsonBlob);
      console.log('✅ JSON data blob added to FormData');
      
      // Add required files with correct parameter names matching backend
      if (resumeFile) {
        formData.append('resume', resumeFile);
        console.log('✅ Resume file added to FormData:', resumeFile.name, 'Size:', resumeFile.size);
      } else {
        console.warn('⚠️ No resume file provided - this will likely cause validation error');
      }
      
      if (profilePicture) {
        formData.append('profilePic', profilePicture);
        console.log('✅ Profile picture added to FormData:', profilePicture.name, 'Size:', profilePicture.size);
      }
      
      // Log FormData contents for debugging
      console.log('📦 FormData prepared with parts:');
      for (let pair of formData.entries()) {
        console.log(`  - ${pair[0]}: ${pair[1] instanceof File ? `File(${pair[1].name})` : 'Blob/Data'}`); 
      }
      
      const headers = this.getAuthHeaders();
      console.log('🔑 Request headers prepared:', headers);
      console.log('🎯 Making POST request to:', this.baseUrl);
      
      const request$ = this.http.post(`${this.baseUrl}`, formData, {
        headers: headers
      }).pipe(
        retry(1), // Retry once on failure
        catchError(this.handleError)
      );
      
      // Add tap operators to log request progress and handle cache invalidation
      return request$.pipe(
        // Log when request starts
        tap({
          next: (response) => {
            console.log('✅ HTTP POST successful! Response:', response);
            console.log('💾 Response indicates candidate created with ID:', (response as any)?.id);
            // Clear cache after successful creation to ensure fresh data
            this.invalidateCache();
            console.log('📋 Cache cleared after successful candidate creation');
          },
          error: (error) => {
            console.error('❌ HTTP POST failed! Error:', error);
          }
        })
      );
      
    } catch (error) {
      console.error('❌ Error in addCandidate preparation:', error);
      return throwError(() => ({
        technical: 'Failed to prepare candidate data: ' + (error as Error).message,
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
      console.log('Fetching all candidates with performance optimization');
      
      // Check if cache is still valid
      const now = Date.now();
      if (this.candidatesCache$.value && (now - this.cacheTimestamp) < this.CACHE_DURATION) {
        console.log('[PERFORMANCE] Using cached candidates data');
        return this.candidatesCache$.asObservable().pipe(
          switchMap(cached => cached ? of(cached) : this.fetchCandidates())
        );
      }
      
      return this.fetchCandidates();
    } else {
      return of([]);
    }
  }
  
  private fetchCandidates(): Observable<Candidate[]> {
    return this.http.get<Candidate[]>(`${this.baseUrl}`, {
      headers: this.getAuthHeaders()
    }).pipe(
      tap(candidates => {
        // Update cache
        this.candidatesCache$.next(candidates);
        this.cacheTimestamp = Date.now();
        console.log('[PERFORMANCE] Updated candidates cache with', candidates.length, 'items');
      }),
      retry(2), // Retry twice for GET requests
      shareReplay(1), // Share response among multiple subscribers
      catchError(this.handleError)
    );
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
        tap(response => {
          // Clear cache after successful update
          this.invalidateCache();
          console.log('[PERFORMANCE] Cache cleared after candidate update');
        }),
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

  // Backend filtering endpoints for better performance
  searchCandidatesByName(name: string): Observable<Candidate[]> {
    if (!name || name.trim().length === 0) {
      return this.getAllCandidates(); // Return all if no search term
    }
    
    console.log('Searching candidates by name:', name);
    
    return this.http.get<Candidate[]>(`${this.baseUrl}/search`, {
      headers: this.getAuthHeaders(),
      params: { name: name.trim() }
    }).pipe(
      retry(1),
      shareReplay(1),
      catchError(this.handleError)
    );
  }
  
  getCandidatesByStatus(status: string): Observable<Candidate[]> {
    if (!status || status.trim().length === 0) {
      return this.getAllCandidates(); // Return all if no status filter
    }
    
    console.log('Fetching candidates by status:', status);
    
    return this.http.get<Candidate[]>(`${this.baseUrl}/by-status/${encodeURIComponent(status)}`, {
      headers: this.getAuthHeaders()
    }).pipe(
      retry(1),
      shareReplay(1),
      catchError(this.handleError)
    );
  }
  
  getCandidatesByRequisitionId(requisitionId: string): Observable<Candidate[]> {
    if (!requisitionId || requisitionId.trim().length === 0) {
      return this.getAllCandidates(); // Return all if no requisition filter
    }
    
    console.log('Fetching candidates by requisition ID:', requisitionId);
    
    return this.http.get<Candidate[]>(`${this.baseUrl}/by-requisition/${encodeURIComponent(requisitionId)}`, {
      headers: this.getAuthHeaders()
    }).pipe(
      retry(1),
      shareReplay(1),
      catchError(this.handleError)
    );
  }
  
  // Combined filtering method for advanced search
  filterCandidates(filters: {
    name?: string;
    status?: string;
    requisitionId?: string;
  }): Observable<Candidate[]> {
    // If multiple filters are provided, prioritize in this order: name > status > requisition
    // For complex filtering, we could create a new backend endpoint, but for now use the most specific filter
    
    if (filters.name && filters.name.trim().length > 0) {
      return this.searchCandidatesByName(filters.name);
    } else if (filters.status && filters.status.trim().length > 0) {
      return this.getCandidatesByStatus(filters.status);
    } else if (filters.requisitionId && filters.requisitionId.trim().length > 0) {
      return this.getCandidatesByRequisitionId(filters.requisitionId);
    } else {
      return this.getAllCandidates();
    }
  }

  // Performance optimization methods
  private invalidateCache(): void {
    this.candidatesCache$.next(null);
    this.cacheTimestamp = 0;
    console.log('[PERFORMANCE] Local cache invalidated');
  }

  public clearAllCache(): void {
    this.invalidateCache();
    console.log('[PERFORMANCE] All caches cleared');
  }
}
