# Contributing to ARIA

We love your input! We want to make contributing to ARIA as easy and transparent as possible, whether it's:

- Reporting a bug
- Discussing the current state of the code
- Submitting a fix
- Proposing new features
- Becoming a maintainer

## 🚀 Development Environment Setup

### Prerequisites
- Docker 20.10+ and Docker Compose 2.0+
- Node.js 18+ and npm 9+
- Java 17+ and Maven 3.8+
- Python 3.11+ and pip
- Git 2.30+

### Quick Setup
```bash
# Clone your fork
git clone https://github.com/your-username/aria-platform.git
cd aria-platform

# Set up development environment
./start-aria.sh development

# Verify setup
./start-aria.sh status
```

## 🏗️ Project Structure

```
aria-platform/
├── frontend/                 # Angular 19 frontend
│   ├── src/app/
│   ├── src/environments/
│   └── package.json
├── backend/
│   ├── user-management/      # Spring Boot user service
│   ├── interview-orchestrator/ # Spring Boot interview service
│   └── pom.xml
├── ai-services/
│   ├── adaptive-engine/      # FastAPI adaptive questioning
│   ├── speech-service/       # FastAPI speech-to-text
│   ├── analytics-service/    # FastAPI AI analytics
│   └── requirements.txt
├── infrastructure/
│   ├── nginx/               # Reverse proxy config
│   ├── monitoring/          # Prometheus, Grafana config
│   └── scripts/
├── docker-compose.yml
└── start-aria.sh
```

## 🔄 Development Workflow

### 1. Fork and Clone
```bash
# Fork the repo on GitHub, then:
git clone https://github.com/your-username/aria-platform.git
cd aria-platform
git remote add upstream https://github.com/your-org/aria-platform.git
```

### 2. Create a Feature Branch
```bash
git checkout -b feature/amazing-feature
# or
git checkout -b fix/bug-description
# or
git checkout -b docs/update-readme
```

### 3. Make Your Changes

#### Frontend Development (Angular)
```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Start development server
ng serve

# Run tests
npm test
npm run e2e

# Lint and format
npm run lint
npm run format
```

#### Backend Development (Spring Boot)
```bash
# Navigate to service directory
cd backend/interview-orchestrator

# Run tests
mvn test

# Start service locally
mvn spring-boot:run

# Check code style
mvn checkstyle:check
```

#### AI Services Development (FastAPI)
```bash
# Navigate to AI service directory
cd ai-services/adaptive-engine

# Create virtual environment
python -m venv venv
source venv/bin/activate  # or `venv\Scripts\activate` on Windows

# Install dependencies
pip install -r requirements.txt

# Start service
uvicorn main:app --reload --port 8001

# Run tests
pytest

# Format code
black .
isort .
```

### 4. Test Your Changes

#### Unit Tests
```bash
# Frontend
cd frontend && npm test

# Backend
cd backend/interview-orchestrator && mvn test

# AI Services
cd ai-services/adaptive-engine && pytest
```

#### Integration Tests
```bash
# Start all services
./start-aria.sh development

# Run integration tests
cd tests && python -m pytest integration/

# Test specific interview flow
curl -X POST http://localhost:8081/api/interview/schedule \
  -H "Content-Type: application/json" \
  -d @test-data/sample-interview.json
```

### 5. Commit Your Changes

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```bash
# Examples of good commit messages:
git commit -m "feat: add real-time emotion detection to video analysis"
git commit -m "fix: resolve WebSocket connection timeout issue"
git commit -m "docs: update API documentation for adaptive engine"
git commit -m "test: add unit tests for IRT question selection"
git commit -m "refactor: optimize database queries in interview service"
```

### 6. Push and Create Pull Request
```bash
git push origin feature/amazing-feature
# Then create a PR on GitHub
```

## 📝 Coding Standards

### TypeScript/Angular Standards
- Follow [Angular Style Guide](https://angular.io/guide/styleguide)
- Use TypeScript strict mode
- Prefer reactive programming with RxJS
- Use Prettier for formatting: `npm run format`

### Java/Spring Boot Standards
- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use Spring Boot best practices
- Write comprehensive JavaDoc comments
- Use Checkstyle: `mvn checkstyle:check`

### Python/FastAPI Standards
- Follow [PEP 8](https://peps.python.org/pep-0008/)
- Use type hints everywhere
- Use Black for formatting: `black .`
- Use isort for imports: `isort .`
- Write docstrings for all functions

### General Guidelines
- Write self-documenting code with clear variable names
- Add comments for complex business logic
- Include unit tests for new features
- Update documentation for API changes
- Keep functions small and focused (max 20-30 lines)

## 🧪 Testing Guidelines

### Unit Tests
- **Frontend**: Jest with Angular Testing Utilities
- **Backend**: JUnit 5 with Mockito
- **AI Services**: pytest with fixtures

### Integration Tests
- Test API endpoints with real HTTP calls
- Test WebSocket connections and data flow
- Test database operations with test containers

### End-to-End Tests
- Use Cypress for frontend E2E tests
- Test complete interview workflows
- Test across different browsers and devices

### Test Data
```bash
# Use provided test data
tests/data/
├── sample-users.json
├── sample-questions.json
├── sample-interviews.json
└── test-audio-files/
```

## 🐛 Bug Reports

### Before Submitting
1. Check existing issues to avoid duplicates
2. Test with the latest development version
3. Try to reproduce with minimal steps

### Bug Report Template
```markdown
**Bug Description**
A clear and concise description of the bug.

**Steps to Reproduce**
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected Behavior**
What you expected to happen.

**Screenshots/Logs**
Add screenshots or log outputs if applicable.

**Environment**
- OS: [e.g. macOS 12.6]
- Browser: [e.g. Chrome 108]
- Node.js: [e.g. 18.12.0]
- Docker: [e.g. 20.10.21]
```

## 💡 Feature Requests

### Feature Request Template
```markdown
**Feature Description**
A clear description of the feature you'd like to see.

**Problem Statement**
What problem does this solve? Who benefits?

**Proposed Solution**
How would you like this to work?

**Alternatives Considered**
What other solutions did you consider?

**Additional Context**
Add any other context, screenshots, or examples.
```

## 🚀 Deployment and Release

### Development Deployment
```bash
# Deploy to development environment
./start-aria.sh development

# View logs
docker-compose logs -f
```

### Production Deployment
```bash
# Deploy to production
./start-aria.sh production

# Scale services
docker-compose up --scale adaptive-engine=3
```

### Release Process
1. Create release branch: `git checkout -b release/v1.1.0`
2. Update version numbers in all services
3. Update CHANGELOG.md
4. Create release PR and get approval
5. Merge to main and tag: `git tag v1.1.0`
6. Deploy to production
7. Create GitHub release with notes

## 🤝 Code Review Process

### For Contributors
- Ensure all tests pass
- Update documentation if needed
- Keep PRs focused and small (< 500 lines)
- Respond to feedback promptly
- Be open to suggestions and changes

### For Reviewers
- Review code within 48 hours
- Provide constructive feedback
- Check for security vulnerabilities
- Verify tests cover new functionality
- Ensure code follows style guidelines

### PR Checklist
- [ ] Tests pass locally and in CI
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] No sensitive data exposed
- [ ] Performance implications considered
- [ ] Backward compatibility maintained

## 🏆 Recognition

Contributors will be recognized in:
- CONTRIBUTORS.md file
- GitHub contributors section
- Release notes for significant contributions
- Special mentions in project communications

## 📞 Getting Help

- **GitHub Discussions**: For general questions and discussions
- **GitHub Issues**: For bug reports and feature requests
- **Discord**: [ARIA Dev Community](https://discord.gg/aria-dev) for real-time chat
- **Email**: dev-team@aria-platform.com for private inquiries

## 📄 License

By contributing, you agree that your contributions will be licensed under the Apache 2.0 License.

---

**Thank you for contributing to ARIA!** 🎉

Your contributions help make AI-powered recruitment more accessible and fair for everyone.
