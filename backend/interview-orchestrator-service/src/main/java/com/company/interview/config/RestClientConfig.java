package com.company.interview.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;

/**
 * Configuration class for REST clients used to communicate with external services
 */
@Configuration
@EnableRetry
public class RestClientConfig {

    @Value("${app.services.user-management.url:https://localhost:8080}")
    private String userManagementUrl;

    @Value("${app.services.ai-analytics.url:https://localhost:8003}")
    private String aiAnalyticsUrl;

    @Value("${app.services.question-engine.url:https://localhost:8001}")
    private String questionEngineUrl;

    @Value("${app.services.transcript.url:https://localhost:8002}")
    private String transcriptServiceUrl;

    @Value("${app.services.job-analyzer.url:https://localhost:8005}")
    private String jobDescriptionAnalyzerUrl;

    @Value("${app.services.ai-avatar.url:https://localhost:8006}")
    private String aiAvatarUrl;

    @Value("${app.services.voice-synthesis.url:https://localhost:8007}")
    private String voiceSynthesisUrl;

    @Value("${app.services.voice-isolation.url:https://localhost:8008}")
    private String voiceIsolationUrl;

    @Value("${app.services.mozilla-tts.url:https://localhost:8004}")
    private String mozillaTtsUrl;

    /**
     * Primary REST template with standard configuration
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .interceptors(Collections.singletonList(createLoggingInterceptor()))
                .build();
    }

    /**
     * REST template for User Management Service communication
     */
    @Bean(name = "userManagementRestTemplate")
    public RestTemplate userManagementRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(userManagementUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .interceptors(Collections.singletonList(createAuthenticationInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for AI Analytics Service communication
     */
    @Bean(name = "aiAnalyticsRestTemplate")
    public RestTemplate aiAnalyticsRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(aiAnalyticsUrl)
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60)) // Longer timeout for analytics processing
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for Question Engine Service communication (Adaptive Engine)
     */
    @Bean(name = "questionEngineRestTemplate")
    public RestTemplate questionEngineRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(questionEngineUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for Adaptive Engine Service communication (alias for question engine)
     */
    @Bean(name = "adaptiveEngineRestTemplate")
    public RestTemplate adaptiveEngineRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(questionEngineUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for Transcript Service communication
     */
    @Bean(name = "transcriptServiceRestTemplate")
    public RestTemplate transcriptServiceRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(transcriptServiceUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for Job Description Analyzer Service communication
     */
    @Bean(name = "jobDescriptionAnalyzerRestTemplate")
    public RestTemplate jobDescriptionAnalyzerRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(jobDescriptionAnalyzerUrl)
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(45)) // Longer timeout for job analysis processing
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for AI Avatar Service communication
     */
    @Bean(name = "aiAvatarRestTemplate")
    public RestTemplate aiAvatarRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(aiAvatarUrl)
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for Voice Synthesis Service communication
     */
    @Bean(name = "voiceSynthesisRestTemplate")
    public RestTemplate voiceSynthesisRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(voiceSynthesisUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for Voice Isolation Service communication
     */
    @Bean(name = "voiceIsolationRestTemplate")
    public RestTemplate voiceIsolationRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(voiceIsolationUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for Mozilla TTS Service communication
     */
    @Bean(name = "mozillaTtsRestTemplate")
    public RestTemplate mozillaTtsRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(mozillaTtsUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * Creates an interceptor for standard HTTP headers
     */
    private ClientHttpRequestInterceptor createStandardHeadersInterceptor() {
        return (request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.add("User-Agent", "ARIA-InterviewOrchestratorService/1.0.0");
            headers.add("X-Service-Name", "interview-orchestrator-service");
            return execution.execute(request, body);
        };
    }

    /**
     * Creates an interceptor for authentication with other Spring Boot services
     */
    private ClientHttpRequestInterceptor createAuthenticationInterceptor() {
        return (request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.add("User-Agent", "ARIA-InterviewOrchestratorService/1.0.0");
            headers.add("X-Service-Name", "interview-orchestrator-service");
            
            // Add internal service authentication header
            // This should be replaced with proper service-to-service authentication
            headers.add("X-Internal-Service", "interview-orchestrator-service");
            
            return execution.execute(request, body);
        };
    }

    /**
     * Creates a logging interceptor for debugging purposes
     */
    private ClientHttpRequestInterceptor createLoggingInterceptor() {
        return (request, body, execution) -> {
            // Log the request (in debug mode only)
            System.out.println("REST Request: " + request.getMethod() + " " + request.getURI());
            
            var response = execution.execute(request, body);
            
            // Log the response status
            System.out.println("REST Response: " + response.getStatusCode());
            
            return response;
        };
    }
    
    /**
     * Creates an HTTP request factory that trusts all SSL certificates (for development only)
     * WARNING: This should NEVER be used in production!
     */
    private HttpComponentsClientHttpRequestFactory createSSLTrustingRequestFactory() {
        try {
            // Create SSL context that trusts all certificates
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                    .build();
            
            // Create connection manager with SSL configuration
            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(SSLConnectionSocketFactory.getSystemSocketFactory())
                    .build();
            
            // Create HTTP client with SSL configuration
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();
            
            return new HttpComponentsClientHttpRequestFactory(httpClient);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL trusting request factory", e);
        }
    }
}
