# ARIA Project Architecture Flow

## System Overview
ARIA (Advanced Recruitment Intelligence Assistant) is an AI-powered recruitment platform with a microservices architecture designed for scalable, bias-free recruitment processes.

## Architecture Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                  CLIENT LAYER                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────────┐ │
│  │                          Angular Frontend (19.x)                           │ │
│  │                         Port: 4200 (Development)                           │ │
│  ├─────────────────────────────────────────────────────────────────────────────┤ │
│  │ Components:                                                                 │ │
│  │ • Recruiter Dashboard        • Authentication Guards                        │ │
│  │ • Candidate Management       • Auth Service (JWT + Token Management)       │ │
│  │ • Interview Interface        • Candidate Service (CRUD Operations)         │ │
│  │ • File Upload (Resume/Audio) • Environment Configuration                   │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
│                                       │                                         │
└───────────────────────────────────────┼─────────────────────────────────────────┘
                                        │ HTTP/HTTPS Requests
                                        │ REST API Calls
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                   API GATEWAY                                  │
│                         (http://localhost:8080/api)                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                       │                                         │
│  ┌───────────────────────────────┬────┴──────┬──────────────────────────────────┐ │
│  │                               │           │                                  │ │
│  ▼                               ▼           ▼                                  │ │
│                                                                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                               MICROSERVICES LAYER                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│ ┌─────────────────────────────┐  ┌─────────────────────────────┐                │
│ │    User Management Service  │  │    AI Analysis Service      │                │
│ │      (Spring Boot 3.2.3)   │  │        (Python/FastAPI)     │                │
│ │         Port: 8080          │  │         Port: 8001          │                │
│ ├─────────────────────────────┤  ├─────────────────────────────┤                │
│ │ Endpoints:                  │  │ AI Analysis APIs:           │                │
│ │ • /api/auth/*               │  │ • /facial-analysis          │                │
│ │   - login, register         │  │ • /speech-analysis          │                │
│ │   - refresh-token           │  │ • /bias-detection           │                │
│ │   - forgot/reset-password   │  │                             │                │
│ │   - OTP send/verify         │  │ Features:                   │                │
│ │                             │  │ • Facial Expression Analysis│                │
│ │ • /api/candidates/*         │  │ • Speech Emotion Recognition│                │
│ │   - CRUD operations         │  │ • AI Bias Detection         │                │
│ │   - file uploads            │  │ • Real-time Processing      │                │
│ │   - search & filtering      │  │                             │                │
│ │                             │  │                             │                │
│ │ Features:                   │  │                             │                │
│ │ • JWT Authentication        │  │                             │                │
│ │ • File Storage (Resume/Audio)│  │                             │                │
│ │ • Email Integration         │  │                             │                │
│ │ • Refresh Token Management  │  │                             │                │
│ └─────────────────────────────┘  └─────────────────────────────┘                │
│              │                                   │                              │
│              ▼                                   ▼                              │
└──────────────┼───────────────────────────────────┼──────────────────────────────┘
               │                                   │
               ▼                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                               DATA LAYER                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│ ┌─────────────────────────────┐  ┌─────────────────────────────┐                │
│ │       MySQL Database        │  │       File Storage          │                │
│ │                             │  │                             │                │
│ │ Tables (Schema):            │  │ Local Storage Paths:        │                │
│ │ • candidate_tables.sql      │  │ • /uploads/resumes/         │                │
│ │ • interview_tables.sql      │  │ • /uploads/audio/           │                │
│ │ • analytics_tables.sql      │  │ • /uploads/profiles/        │                │
│ │ • question_bank_tables.sql  │  │                             │                │
│ │                             │  │ File Types Supported:       │                │
│ │ Entities:                   │  │ • Resume: PDF, DOC, DOCX    │                │
│ │ • Recruiters                │  │ • Audio: MP3, WAV, OGG      │                │
│ │ • Candidates                │  │ • Images: JPG, PNG, GIF     │                │
│ │ • Interviews                │  │                             │                │
│ │ • Refresh Tokens            │  │                             │                │
│ │ • Password Reset Tokens     │  │                             │                │
│ └─────────────────────────────┘  └─────────────────────────────┘                │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

```

## Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              USER INTERACTION FLOW                             │
└─────────────────────────────────────────────────────────────────────────────────┘

1. AUTHENTICATION FLOW:
   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
   │   Frontend  │───▶│    API      │───▶│    Auth     │───▶│   Database  │
   │  (Angular)  │◄───│  Gateway    │◄───│  Service    │◄───│   (MySQL)   │
   └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
         │                                        │
         ▼                                        ▼
   [JWT Storage]                           [Token Management]
   [Session Mgmt]                          [OTP Verification]

2. CANDIDATE MANAGEMENT FLOW:
   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
   │   Frontend  │───▶│  Candidate  │───▶│    File     │───▶│  Database + │
   │   Upload    │    │  Controller │    │   Storage   │    │ File System │
   └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
         │                   │
         ▼                   ▼
   [Form Data]         [Multipart Processing]
   [File Validation]   [Resume Parsing]

3. AI ANALYSIS FLOW:
   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
   │  Interview  │───▶│     AI      │───▶│  Analysis   │───▶│  Results    │
   │   Media     │    │  Analysis   │    │ Algorithms  │    │  Storage    │
   │(Video/Audio)│    │  Service    │    │             │    │             │
   └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
         │                   │                   │                │
         ▼                   ▼                   ▼                ▼
   [Media Capture]    [API Processing]    [ML Models]    [Analytics DB]
                      [Facial Analysis]   [Bias Detection]
                      [Speech Analysis]   [Emotion Recognition]

```

## Technology Stack

### Frontend (Angular 19.x)
- **Framework**: Angular with SSR support
- **Language**: TypeScript
- **Styling**: SCSS
- **Build Tools**: Vite, Webpack
- **Testing**: Karma, Jasmine
- **Server**: Express.js for SSR

### Backend Services
- **User Management Service**: Spring Boot 3.2.3 (Java 17)
- **AI Analysis Service**: Python (FastAPI framework expected)
- **Database**: MySQL with JPA/Hibernate
- **Authentication**: JWT with refresh token rotation
- **Email**: Spring Mail integration

### Infrastructure
- **Containerization**: Docker support
- **Build Tools**: Maven (Backend), Angular CLI (Frontend)
- **Security**: Spring Security, CORS, GDPR compliance
- **File Storage**: Local file system with structured uploads

## Key Features Flow

### 1. User Authentication
```
Login Request → JWT Generation → Token Storage → Session Management
     ↓              ↓              ↓              ↓
OTP Verification → Refresh Token → Auto Logout → Security Guards
```

### 2. Candidate Lifecycle
```
Candidate Creation → File Upload → Resume Parsing → Database Storage
       ↓               ↓             ↓              ↓
   Validation → Profile Pictures → Audio Files → Search/Filter
```

### 3. AI-Powered Analysis
```
Interview Media → AI Processing → Bias Detection → Analytics
      ↓              ↓              ↓              ↓
  Video/Audio → Facial Analysis → Speech Analysis → Reports
```

## Service Communication
- **Frontend ↔ Backend**: REST API calls via HTTP
- **Authentication**: JWT tokens with Bearer authentication
- **File Uploads**: Multipart form data
- **Real-time Features**: WebSocket connections (planned)
- **AI Processing**: RESTful service integration

## Security Architecture
- JWT-based authentication with refresh token rotation
- CORS configuration for cross-origin requests
- File upload validation and size limits
- Password reset with secure token generation
- GDPR-compliant data handling

## Scalability Design
- Microservices architecture for independent scaling
- Stateless service design
- Database connection pooling
- File storage abstraction for cloud migration
- API versioning support

This architecture supports ARIA's vision of transforming IT recruitment through AI-powered, bias-free assessment while maintaining security, scalability, and compliance standards.
