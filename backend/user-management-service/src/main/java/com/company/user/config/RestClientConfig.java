package com.company.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Value("${app.services.interview-orchestrator.url:http://localhost:8081}")
    private String interviewOrchestratorUrl;

    @Value("${app.services.adaptive-engine.url:http://localhost:8001}")
    private String adaptiveEngineUrl;

    @Value("${app.services.speech-service.url:http://localhost:8002}")
    private String speechServiceUrl;

    @Value("${app.services.ai-analytics.url:http://localhost:8003}")
    private String aiAnalyticsUrl;

    /**
     * Primary REST template with standard configuration
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .interceptors(Collections.singletonList(createLoggingInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for Interview Orchestrator Service communication
     */
    @Bean(name = "interviewOrchestratorRestTemplate")
    public RestTemplate interviewOrchestratorRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .rootUri(interviewOrchestratorUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .interceptors(Collections.singletonList(createAuthenticationInterceptor()))
                .build();
        
        // Configure SSL trust for development (trusts self-signed certificates)
        restTemplate.setRequestFactory(createSSLTrustingRequestFactory());
        return restTemplate;
    }

    /**
     * REST template for Adaptive Engine Service communication
     */
    @Bean(name = "adaptiveEngineRestTemplate")
    public RestTemplate adaptiveEngineRestTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri(adaptiveEngineUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
    }

    /**
     * REST template for Speech Service communication
     */
    @Bean(name = "speechServiceRestTemplate")
    public RestTemplate speechServiceRestTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri(speechServiceUrl)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
    }

    /**
     * REST template for AI Analytics Service communication
     */
    @Bean(name = "aiAnalyticsRestTemplate")
    public RestTemplate aiAnalyticsRestTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri(aiAnalyticsUrl)
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60)) // Longer timeout for analytics processing
                .interceptors(Collections.singletonList(createStandardHeadersInterceptor()))
                .build();
    }

    /**
     * Creates an interceptor for standard HTTP headers
     */
    private ClientHttpRequestInterceptor createStandardHeadersInterceptor() {
        return (request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.add("User-Agent", "ARIA-UserManagementService/1.0.0");
            headers.add("X-Service-Name", "user-management-service");
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
            headers.add("User-Agent", "ARIA-UserManagementService/1.0.0");
            headers.add("X-Service-Name", "user-management-service");
            
            // Add internal service authentication header
            // This should be replaced with proper service-to-service authentication
            headers.add("X-Internal-Service", "user-management-service");
            
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
