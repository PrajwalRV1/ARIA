package com.company.interview.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * SSL Trust Configuration for Development Environment
 * 
 * WARNING: This configuration disables SSL certificate validation for development.
 * This should NEVER be used in production environments!
 */
@Configuration
@Profile({"dev", "dev-ssl", "dev-ssl-relaxed", "ssl-complete"})
public class SSLTrustConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SSLTrustConfig.class);

    @Value("${app.ssl.trust-all-certificates:true}")
    private boolean trustAllCertificates;

    @Value("${app.ssl.disable-hostname-verification:true}")
    private boolean disableHostnameVerification;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (trustAllCertificates) {
            configureSSLTrustAll();
        }
    }

    /**
     * Configure SSL to trust all certificates (development only)
     * This method disables SSL certificate validation globally
     */
    private void configureSSLTrustAll() {
        try {
            logger.warn("🔒 CONFIGURING SSL TO TRUST ALL CERTIFICATES - FOR DEVELOPMENT ONLY!");
            logger.warn("⚠️  This configuration should NEVER be used in production!");

            // Create trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Accept all certificates
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Accept all certificates
                        if (logger.isDebugEnabled()) {
                            logger.debug("🔐 Accepting server certificate: {}", 
                                certs[0].getSubjectDN().getName());
                        }
                    }
                }
            };

            // Create and install SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            logger.info("✅ SSL context configured to trust all certificates");

            // Disable hostname verification if configured
            if (disableHostnameVerification) {
                HostnameVerifier hostnameVerifier = (hostname, session) -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("🌐 Accepting hostname: {}", hostname);
                    }
                    return true;
                };

                HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
                logger.info("✅ Hostname verification disabled for development");
            }

            logger.info("🔓 SSL trust configuration completed successfully");

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.error("❌ Failed to configure SSL trust settings: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure SSL trust settings", e);
        }
    }

    /**
     * Create a hostname verifier that accepts all hostnames (development only)
     */
    public static HostnameVerifier createTrustAllHostnameVerifier() {
        return (hostname, session) -> {
            // Accept all hostnames in development
            return true;
        };
    }

    /**
     * Create an X509TrustManager that trusts all certificates (development only)
     */
    public static X509TrustManager createTrustAllTrustManager() {
        return new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                // Trust all client certificates
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                // Trust all server certificates
            }
        };
    }

    /**
     * Utility method to create an SSL context that trusts all certificates
     */
    public static SSLContext createTrustAllSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[] { createTrustAllTrustManager() };
        
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        
        return sslContext;
    }
}
