# 🔒 SECURITY FIX IMPLEMENTATION PLAN
## Candidate Management API - Critical Vulnerability Remediation

**Date:** August 30, 2025  
**Priority:** 🔴 **IMMEDIATE ACTION REQUIRED**  
**Target Completion:** 3 weeks  

---

## 🚨 **CRITICAL FIXES - WEEK 1 (IMMEDIATE)**

### **1. Fix CVE-2024-001: BOLA (Broken Object Level Authorization)**

#### **Step 1: Add Tenant Extraction Utility**
```java
// Create: src/main/java/com/company/user/util/TenantContextUtil.java
@Component
@Slf4j
public class TenantContextUtil {
    
    public static String extractTenantFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new SecurityException("No authentication context found");
        }
        
        // Extract tenant from JWT claims or user details
        if (authentication.getPrincipal() instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication.getPrincipal();
            return jwtToken.getToken().getClaim("tenant_id");
        }
        
        // Fallback: extract from user details
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        // Assume tenant is embedded in username or available via custom UserDetails
        return extractTenantFromUserDetails(userDetails);
    }
    
    public static String extractRecruiterIdFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new SecurityException("No authentication context found");
        }
        
        return authentication.getName(); // or extract from JWT claims
    }
}
```

#### **Step 2: Update Controller with Authorization**
```java
// Update: CandidateController.java
@RestController
@RequestMapping("/candidates")
@RequiredArgsConstructor
@Validated
public class CandidateController {
    
    private final CandidateService candidateService;
    
    /**
     * ✅ FIXED: Get candidate by ID with tenant isolation
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CandidateResponse> getCandidateById(
            @PathVariable Long id, 
            Authentication authentication) {
        
        String tenantId = TenantContextUtil.extractTenantFromAuth(authentication);
        String recruiterId = TenantContextUtil.extractRecruiterIdFromAuth(authentication);
        
        return candidateService.getCandidateById(id, tenantId, recruiterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    /**
     * ✅ FIXED: Update candidate with tenant isolation
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    public CandidateResponse updateCandidate(
            @PathVariable Long id,
            @RequestPart(value = "data", required = false) @Valid CandidateUpdateRequest dataFromPart,
            @RequestBody(required = false) @Valid CandidateUpdateRequest dataFromJson,
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic,
            HttpServletRequest request,
            Authentication authentication) {
        
        // Validate ID from path
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + id);
        }
        
        // Extract tenant and recruiter context
        String tenantId = TenantContextUtil.extractTenantFromAuth(authentication);
        String recruiterId = TenantContextUtil.extractRecruiterIdFromAuth(authentication);
        
        // Determine data source
        CandidateUpdateRequest data = determineDataSource(dataFromPart, dataFromJson, request);
        data.setId(id);
        
        return candidateService.updateCandidate(data, resume, profilePic, tenantId, recruiterId);
    }
    
    /**
     * ✅ FIXED: Get all candidates with tenant filtering
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> getAllCandidates(Authentication authentication) {
        String tenantId = TenantContextUtil.extractTenantFromAuth(authentication);
        String recruiterId = TenantContextUtil.extractRecruiterIdFromAuth(authentication);
        
        return candidateService.getAllCandidates(tenantId, recruiterId);
    }
    
    /**
     * ✅ FIXED: Search with tenant isolation
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> searchCandidatesByName(
            @RequestParam(required = false) @Length(max = 100) String name,
            Authentication authentication) {
        
        String tenantId = TenantContextUtil.extractTenantFromAuth(authentication);
        String recruiterId = TenantContextUtil.extractRecruiterIdFromAuth(authentication);
        
        return candidateService.searchCandidatesByName(name, tenantId, recruiterId);
    }
    
    /**
     * ✅ FIXED: Filter by status with tenant isolation
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @GetMapping(value = "/by-status/{status}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> getCandidatesByStatus(
            @PathVariable CandidateStatus status,
            Authentication authentication) {
        
        String tenantId = TenantContextUtil.extractTenantFromAuth(authentication);
        String recruiterId = TenantContextUtil.extractRecruiterIdFromAuth(authentication);
        
        return candidateService.getCandidatesByStatus(status, tenantId, recruiterId);
    }
    
    /**
     * ✅ FIXED: Filter by requisition with tenant isolation
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @GetMapping(value = "/by-requisition/{reqId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> getCandidatesByRequisitionId(
            @PathVariable @NotBlank @Length(max = 255) String reqId,
            Authentication authentication) {
        
        String tenantId = TenantContextUtil.extractTenantFromAuth(authentication);
        String recruiterId = TenantContextUtil.extractRecruiterIdFromAuth(authentication);
        
        return candidateService.getCandidatesByRequisitionId(reqId, tenantId, recruiterId);
    }
}
```

#### **Step 3: Update Service Layer**
```java
// Update: CandidateServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateServiceImpl implements CandidateService {
    
    /**
     * ✅ FIXED: Get candidate with tenant and ownership validation
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<CandidateResponse> getCandidateById(Long id, String tenantId, String recruiterId) {
        log.debug("Fetching candidate by ID: {} for tenant: {} recruiter: {}", id, tenantId, recruiterId);
        
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + id);
        }
        
        return candidateRepository.findByIdAndTenantAndRecruiter(id, tenantId, recruiterId)
                .map(CandidateResponse::from);
    }
    
    /**
     * ✅ FIXED: Get all candidates with tenant filtering
     */
    @Override
    @Transactional(readOnly = true)  
    public List<CandidateResponse> getAllCandidates(String tenantId, String recruiterId) {
        log.debug("Fetching all candidates for tenant: {} recruiter: {}", tenantId, recruiterId);
        
        List<Candidate> candidates = candidateRepository.findAllByTenantAndRecruiter(tenantId, recruiterId);
        return candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
    }
    
    /**
     * ✅ FIXED: Search with tenant isolation
     */
    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> searchCandidatesByName(String name, String tenantId, String recruiterId) {
        log.debug("Searching candidates by name: {} for tenant: {} recruiter: {}", name, tenantId, recruiterId);
        
        if (!StringUtils.hasText(name)) {
            return getAllCandidates(tenantId, recruiterId);
        }
        
        // Sanitize search input
        String sanitizedName = sanitizeSearchInput(name);
        
        List<Candidate> candidates = candidateRepository.findByNameContainingIgnoreCaseAndTenantAndRecruiter(
                sanitizedName.trim(), tenantId, recruiterId);
        return candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
    }
    
    /**
     * Sanitize search input to prevent injection attacks
     */
    private String sanitizeSearchInput(String input) {
        if (input == null) return "";
        
        return input.trim()
                .replaceAll("[%_\\\\]", "\\\\$0")  // Escape SQL wildcards
                .replaceAll("[<>\"']", "")        // Remove potentially dangerous chars
                .substring(0, Math.min(input.length(), 100)); // Limit length
    }
}
```

#### **Step 4: Update Repository Layer**
```java
// Update: CandidateRepository.java
@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long>, CandidateRepositoryCustom {
    
    /**
     * ✅ SECURE: Find candidate with tenant and ownership validation
     */
    @Query("SELECT c FROM Candidate c LEFT JOIN FETCH c.skills WHERE c.id = :id AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId")
    Optional<Candidate> findByIdAndTenantAndRecruiter(@Param("id") Long id, 
                                                     @Param("tenantId") String tenantId, 
                                                     @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Find all candidates with tenant isolation
     */
    @Query("SELECT c FROM Candidate c WHERE c.tenantId = :tenantId AND c.recruiterId = :recruiterId ORDER BY c.createdAt DESC")
    List<Candidate> findAllByTenantAndRecruiter(@Param("tenantId") String tenantId, 
                                               @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Search by name with tenant isolation
     */
    @Query("SELECT c FROM Candidate c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')) AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId ORDER BY c.createdAt DESC")
    List<Candidate> findByNameContainingIgnoreCaseAndTenantAndRecruiter(@Param("name") String name, 
                                                                       @Param("tenantId") String tenantId, 
                                                                       @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Find by status with tenant isolation
     */
    @Query("SELECT c FROM Candidate c WHERE c.status = :status AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId ORDER BY c.createdAt DESC")
    List<Candidate> findByStatusAndTenantAndRecruiter(@Param("status") CandidateStatus status, 
                                                     @Param("tenantId") String tenantId, 
                                                     @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Find by requisition with tenant isolation
     */
    @Query("SELECT c FROM Candidate c WHERE c.requisitionId = :requisitionId AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId ORDER BY c.createdAt DESC")
    List<Candidate> findByRequisitionIdAndTenantAndRecruiter(@Param("requisitionId") String requisitionId, 
                                                            @Param("tenantId") String tenantId, 
                                                            @Param("recruiterId") String recruiterId);
}
```

#### **Step 5: Database Migration**
```sql
-- Create: V2_001__Add_Tenant_Isolation.sql
-- Add tenant isolation columns to candidates table

ALTER TABLE candidates 
ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default',
ADD COLUMN created_by VARCHAR(255),
ADD COLUMN updated_by VARCHAR(255);

-- Create indexes for performance
CREATE INDEX idx_candidates_tenant_recruiter ON candidates(tenant_id, recruiter_id);
CREATE INDEX idx_candidates_tenant_status ON candidates(tenant_id, status);
CREATE INDEX idx_candidates_tenant_requisition ON candidates(tenant_id, requisition_id);

-- Update existing records with default tenant
UPDATE candidates SET tenant_id = 'default' WHERE tenant_id IS NULL;

-- Add constraint to ensure tenant_id is not null
ALTER TABLE candidates ALTER COLUMN tenant_id SET NOT NULL;

-- Add foreign key constraint if tenant table exists
-- ALTER TABLE candidates ADD CONSTRAINT fk_candidates_tenant 
--   FOREIGN KEY (tenant_id) REFERENCES tenants(id);

COMMENT ON COLUMN candidates.tenant_id IS 'Tenant isolation - ensures data separation between organizations';
COMMENT ON COLUMN candidates.created_by IS 'User who created this candidate record';  
COMMENT ON COLUMN candidates.updated_by IS 'User who last updated this candidate record';
```

---

### **2. Fix CVE-2024-003: Information Disclosure Through Error Messages**

#### **Step 1: Enhanced Error Response DTO**
```java
// Update: src/main/java/com/company/user/dto/ApiError.java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiError {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String errorId;     // ✅ ADD: Unique error ID for support
    private List<String> details;
    
    // ✅ ADD: Sanitized error creation method
    public static ApiError createSanitized(HttpStatus status, String message, String path) {
        return ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(sanitizePath(path))
                .errorId(generateErrorId())
                .build();
    }
    
    private static String sanitizePath(String path) {
        if (path == null) return "";
        return path.replaceAll("uri=", "").replaceAll("[<>\"']", "");
    }
    
    private static String generateErrorId() {
        return "ERR-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString(ThreadLocalRandom.current().nextInt(1000, 9999));
    }
}
```

#### **Step 2: Sanitized Exception Handler**
```java
// Update: GlobalExceptionHandler.java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex, WebRequest req) {
        String errorId = ApiError.generateErrorId();
        log.warn("Validation error [{}]: {}", errorId, ex.getMessage());
        
        List<String> sanitizedErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> sanitizeFieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        
        ApiError err = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Request validation failed")
                .path(ApiError.sanitizePath(req.getDescription(false)))
                .errorId(errorId)
                .details(sanitizedErrors)
                .build();
        
        return ResponseEntity.badRequest().body(err);
    }
    
    @ExceptionHandler({IllegalArgumentException.class, BadRequestException.class})
    public ResponseEntity<ApiError> onBadArg(RuntimeException ex, WebRequest req) {
        String errorId = ApiError.generateErrorId();
        log.warn("Bad request error [{}]: {}", errorId, ex.getMessage());
        
        ApiError err = ApiError.createSanitized(
                HttpStatus.BAD_REQUEST,
                sanitizeUserMessage(ex.getMessage()),
                req.getDescription(false)
        );
        
        return ResponseEntity.badRequest().body(err);
    }
    
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiError> handleFileStorageException(FileStorageException ex, WebRequest req) {
        String errorId = ApiError.generateErrorId();
        log.error("File storage error [{}]: {}", errorId, ex.getMessage(), ex);
        
        ApiError err = ApiError.createSanitized(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "File processing failed. Contact support with Error ID: " + errorId,
                req.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, WebRequest req) {
        String errorId = ApiError.generateErrorId();
        log.warn("Access denied [{}]: {} for path: {}", errorId, ex.getMessage(), req.getDescription(false));
        
        ApiError err = ApiError.createSanitized(
                HttpStatus.FORBIDDEN,
                "Access denied. Insufficient permissions.",
                req.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
    }
    
    /**
     * ✅ CRITICAL: Sanitized generic exception handler
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onGeneric(Exception ex, WebRequest req) {
        String errorId = ApiError.generateErrorId();
        
        // ✅ SECURE: Log full details internally with error ID
        log.error("Unexpected error [{}]: {} | Path: {} | Exception: {}", 
                 errorId, ex.getMessage(), req.getDescription(false), ex.getClass().getSimpleName(), ex);
        
        // ✅ SECURE: Return sanitized response to client
        ApiError err = ApiError.createSanitized(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Contact support with Error ID: " + errorId,
                req.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
    
    /**
     * Sanitize field errors to prevent information disclosure
     */
    private String sanitizeFieldError(String field, String message) {
        // Remove potentially sensitive information from validation messages
        String sanitizedMessage = message
                .replaceAll("(?i)(database|sql|query|table|column)", "data")
                .replaceAll("(?i)(exception|error|stack)", "issue")
                .replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "[IP]") // IP addresses
                .replaceAll("\\b[A-Za-z]:\\\\[\\w\\\\]+", "[PATH]"); // Windows paths
        
        return field + ": " + sanitizedMessage;
    }
    
    /**
     * Sanitize user-facing messages
     */
    private String sanitizeUserMessage(String message) {
        if (message == null) return "Invalid request";
        
        return message
                .replaceAll("(?i)(database|sql|query|table|column|schema)", "data")
                .replaceAll("(?i)(exception|stack|trace)", "error")
                .replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "[IP]")
                .replaceAll("\\b[A-Za-z]:\\\\[\\w\\\\]+", "[PATH]")
                .substring(0, Math.min(message.length(), 200)); // Limit message length
    }
}
```

---

### **3. Fix CVE-2024-006: Implement Rate Limiting**

#### **Step 1: Rate Limiting Configuration**
```java
// Create: src/main/java/com/company/user/config/RateLimitConfig.java
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "app.rate-limit")
@Data
public class RateLimitConfig {
    
    private int requestsPerMinute = 60;
    private int burstCapacity = 100;
    private int replenishRate = 1;
    private long windowSizeMinutes = 1;
    
    @Bean
    public RedisTemplate<String, String> rateLimitRedisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
```

#### **Step 2: Rate Limiting Filter**  
```java
// Create: src/main/java/com/company/user/security/RateLimitingFilter.java
@Component
@Slf4j
public class RateLimitingFilter implements Filter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitConfig rateLimitConfig;
    
    public RateLimitingFilter(RedisTemplate<String, String> redisTemplate, RateLimitConfig config) {
        this.redisTemplate = redisTemplate;
        this.rateLimitConfig = config;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Extract client identifier
        String clientId = extractClientId(httpRequest);
        String endpoint = httpRequest.getRequestURI();
        
        // Check rate limit
        if (!isWithinRateLimit(clientId, endpoint)) {
            log.warn("Rate limit exceeded for client: {} endpoint: {}", clientId, endpoint);
            
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(createRateLimitErrorResponse());
            return;
        }
        
        chain.doFilter(request, response);
    }
    
    private String extractClientId(HttpServletRequest request) {
        // Priority: JWT subject > IP address
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                // Extract user ID from JWT token
                return extractUserIdFromToken(authHeader.substring(7));
            } catch (Exception e) {
                log.debug("Could not extract user from token: {}", e.getMessage());
            }
        }
        
        // Fallback to IP address
        return getClientIpAddress(request);
    }
    
    private boolean isWithinRateLimit(String clientId, String endpoint) {
        String key = "rate_limit:" + clientId + ":" + endpoint;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (rateLimitConfig.getWindowSizeMinutes() * 60 * 1000);
        
        try {
            // Use Redis sorted set for sliding window
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
            
            Long currentCount = redisTemplate.opsForZSet().count(key, windowStart, currentTime);
            
            if (currentCount < rateLimitConfig.getRequestsPerMinute()) {
                // Add current request timestamp
                redisTemplate.opsForZSet().add(key, String.valueOf(currentTime), currentTime);
                redisTemplate.expire(key, Duration.ofMinutes(rateLimitConfig.getWindowSizeMinutes()));
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Rate limiting check failed: {}", e.getMessage());
            // Fail open - allow request if Redis is unavailable
            return true;
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private String createRateLimitErrorResponse() {
        return """
            {
                "timestamp": "%s",
                "status": 429,
                "error": "Too Many Requests",
                "message": "Rate limit exceeded. Please try again later.",
                "path": "rate-limited"
            }
            """.formatted(Instant.now().toString());
    }
}
```

---

## 🟡 **MEDIUM PRIORITY FIXES - WEEK 2**

### **4. Enhanced Input Validation**

#### **Step 1: Custom Validation Annotations**
```java
// Create: src/main/java/com/company/user/validation/SafeSearchInput.java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeSearchInputValidator.class)
public @interface SafeSearchInput {
    String message() default "Search input contains unsafe characters";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
public class SafeSearchInputValidator implements ConstraintValidator<SafeSearchInput, String> {
    
    private static final Pattern UNSAFE_PATTERNS = Pattern.compile(
        "(?i)(script|javascript|vbscript|onload|onerror|<|>|'|\"|;|--|/\\*|\\*/|xp_|sp_|exec|union|select|insert|update|delete|drop|create|alter)"
    );
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        
        return !UNSAFE_PATTERNS.matcher(value).find() && value.length() <= 100;
    }
}
```

#### **Step 2: Update Controller Validation**
```java
// Update search endpoints in CandidateController.java
@GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
public List<CandidateResponse> searchCandidatesByName(
        @RequestParam(required = false) 
        @SafeSearchInput 
        @Length(max = 100, message = "Search term too long") 
        String name,
        Authentication authentication) {
    // ... existing implementation
}
```

---

## 🟢 **LOW PRIORITY FIXES - WEEK 3**

### **5. Security Headers**

#### **Step 1: Security Headers Filter**
```java
// Create: src/main/java/com/company/user/security/SecurityHeadersFilter.java
@Component
public class SecurityHeadersFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Security headers
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        httpResponse.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        
        // HSTS for HTTPS
        if (isHttps((HttpServletRequest) request)) {
            httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        
        chain.doFilter(request, response);
    }
    
    private boolean isHttps(HttpServletRequest request) {
        return "https".equalsIgnoreCase(request.getScheme()) || 
               "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
```

---

## 📋 **TESTING & VALIDATION**

### **Security Test Cases**

#### **Test 1: BOLA Prevention**
```bash
#!/bin/bash
# Test tenant isolation

# Setup test users
USER_A="tenant_a_user"
USER_B="tenant_b_user"
TOKEN_A=$(get_jwt_token $USER_A)
TOKEN_B=$(get_jwt_token $USER_B)

# Create candidate for Tenant A
CANDIDATE_ID=$(curl -s -X POST \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Candidate","email":"test@example.com","requisitionId":"REQ001","phone":"1234567890","status":"PENDING"}' \
  https://api/candidates | jq -r '.id')

# Test: Tenant B should NOT access Tenant A's candidate
RESPONSE=$(curl -s -w "%{http_code}" -o /dev/null \
  -H "Authorization: Bearer $TOKEN_B" \
  https://api/candidates/$CANDIDATE_ID)

if [ "$RESPONSE" = "404" ]; then
    echo "✅ BOLA Prevention: PASSED"
else
    echo "❌ BOLA Prevention: FAILED (HTTP $RESPONSE)"
fi
```

#### **Test 2: Rate Limiting**
```bash
#!/bin/bash
# Test rate limiting

TOKEN=$(get_jwt_token "test_user")

# Send requests rapidly
for i in {1..100}; do
    RESPONSE=$(curl -s -w "%{http_code}" -o /dev/null \
      -H "Authorization: Bearer $TOKEN" \
      https://api/candidates)
    
    if [ "$RESPONSE" = "429" ]; then
        echo "✅ Rate Limiting: PASSED (blocked at request $i)"
        break
    fi
    
    if [ "$i" = "100" ]; then
        echo "❌ Rate Limiting: FAILED (no blocking after 100 requests)"
    fi
done
```

#### **Test 3: Error Message Sanitization**
```bash
#!/bin/bash
# Test error sanitization

TOKEN=$(get_jwt_token "test_user")

# Send malformed request
RESPONSE=$(curl -s \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"invalid": "data"}' \
  https://api/candidates/invalid)

# Check if response contains sensitive information
if echo "$RESPONSE" | grep -i "database\|sql\|stack\|exception"; then
    echo "❌ Error Sanitization: FAILED (sensitive info leaked)"
else
    echo "✅ Error Sanitization: PASSED"
fi
```

---

## 🚀 **DEPLOYMENT CHECKLIST**

### **Pre-deployment**
- [ ] Code review completed
- [ ] Security tests passed
- [ ] Database migration tested
- [ ] Performance impact assessed
- [ ] Rollback plan prepared

### **Deployment Steps**
1. [ ] Deploy database migration (off-peak hours)
2. [ ] Deploy application with feature flags disabled
3. [ ] Run smoke tests
4. [ ] Gradually enable security features
5. [ ] Monitor error rates and performance

### **Post-deployment**
- [ ] Verify all endpoints require proper authorization
- [ ] Test rate limiting functionality
- [ ] Validate error message sanitization
- [ ] Monitor security logs for suspicious activity
- [ ] Performance metrics within acceptable range

---

## 📊 **MONITORING & ALERTING**

### **Security Metrics to Track**
1. **Authorization Failures**: Failed attempts to access unauthorized resources
2. **Rate Limit Hits**: Requests blocked due to rate limiting
3. **Authentication Errors**: Invalid or expired tokens
4. **Suspicious Search Patterns**: Potential injection attempts

### **Alert Conditions**
```yaml
# Example Prometheus/Grafana alerts
- alert: HighAuthorizationFailures
  expr: rate(authorization_failures_total[5m]) > 10
  labels:
    severity: critical
  annotations:
    summary: "High number of authorization failures detected"

- alert: RateLimitExceeded
  expr: rate(rate_limit_exceeded_total[5m]) > 50
  labels:
    severity: warning
  annotations:
    summary: "High rate limit violations detected"
```

---

**Implementation Status:** 📋 **READY FOR EXECUTION**  
**Estimated Completion:** 3 weeks  
**Resource Requirement:** 2 developers + 1 security reviewer  

---

*This implementation plan should be executed in the specified order to ensure critical vulnerabilities are addressed first while maintaining system stability.*
