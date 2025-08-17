import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';

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
  private baseUrl = `${environment.apiBaseUrl}/candidates`;

  constructor(private http: HttpClient) {}

  addCandidate(candidate: Candidate, resumeFile: File, profilePicture?: File): Observable<any> {
    const formData = new FormData();
    formData.append('data', new Blob([JSON.stringify(candidate)], { type: 'application/json' }));  // Change to 'data'
    formData.append('resume', resumeFile);  // Change to 'resume'
    if (profilePicture) {
      formData.append('profilePic', profilePicture);  // Change to 'profilePic'
    }
    return this.http.post(`${this.baseUrl}`, formData);
  }

  uploadResume(resumeFile: File): Observable<any> {
    const formData = new FormData();
    formData.append('resume', resumeFile);  // Fixed: Change from 'resumeFile' to 'resume' to match backend
    return this.http.post(`${this.baseUrl}/upload-resume`, formData);
  }

  getAllCandidates(): Observable<Candidate[]> {
    const token = localStorage.getItem('token');
    return this.http.get<Candidate[]>(`${this.baseUrl}`, {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
  }
  

  getCandidateById(id: number): Observable<Candidate> {
    return this.http.get<Candidate>(`${this.baseUrl}/${id}`);
  }

  updateCandidate(id: number, candidate: Candidate, resumeFile?: File, profilePicture?: File): Observable<any> {
    console.log('CandidateService.updateCandidate called with:');
    console.log('ID:', id);
    console.log('Candidate data:', candidate);
    console.log('Resume file:', resumeFile);
    console.log('Profile picture:', profilePicture);
    
    const formData = new FormData();
    formData.append('data', new Blob([JSON.stringify(candidate)], { type: 'application/json' }));
    
    if (resumeFile) {
      formData.append('resume', resumeFile);
    }
    
    if (profilePicture) {
      formData.append('profilePic', profilePicture);
    }
    
    console.log('Sending PUT request to:', `${this.baseUrl}/${id}`);
    
    // Debug FormData contents
    console.log('FormData contents:');
    for (let [key, value] of formData.entries()) {
      if (value instanceof File) {
        console.log(key, ':', value.name, '(File,', value.size, 'bytes)');
      } else {
        console.log(key, ':', value);
      }
    }
    
    // Use the correct endpoint: PUT /api/candidates/{id}
    return this.http.put(`${this.baseUrl}/${id}`, formData);
  }

  deleteCandidate(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/${id}`);
  }
}