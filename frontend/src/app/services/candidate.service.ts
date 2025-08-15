import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Candidate {
  id?: number;
  requisitionId: string;
  name: string;
  email: string;
  phoneNumber: string;
  appliedRole: string;
  applicationDate: string;
  totalExperience: number;
  relevantExperience: number;
  interviewRound: string;
  status?: string;
  jobDescription: string;
  keyResponsibilities?: string;
  skills?: string[];
  profilePictureUrl?: string; // backend field
  resumeUrl?: string;         // backend field
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
    formData.append('resumeFile', resumeFile);
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

  updateCandidate(id: number, updatePayload: Partial<Candidate>): Observable<any> {
    return this.http.put(`${this.baseUrl}/${id}`, updatePayload);
  }

  deleteCandidate(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/${id}`);
  }
}