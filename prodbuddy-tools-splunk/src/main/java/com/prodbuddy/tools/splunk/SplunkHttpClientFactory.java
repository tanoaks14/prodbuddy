package com.prodbuddy.tools.splunk;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLParameters;
import java.security.cert.X509Certificate;

/** Factory for Splunk HTTP clients. */
public final class SplunkHttpClientFactory {

    /** Default timeout. */
    private static final int TIMEOUT = 5;

    private SplunkHttpClientFactory() { }

    /**
     * Builds an insecure HTTP client for Splunk.
     * @return Insecure HttpClient.
     */
    public static HttpClient buildInsecure() {
        SSLParameters sslParams = new SSLParameters();
        sslParams.setEndpointIdentificationAlgorithm("");

        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .sslContext(insecureSslContext())
                .sslParameters(sslParams)
                .build();
    }

    private static SSLContext insecureSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    @Override
                    public void checkClientTrusted(
                            final X509Certificate[] certs,
                            final String authType) {
                    }
                    @Override
                    public void checkServerTrusted(
                            final X509Certificate[] certs,
                            final String authType) {
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to init insecure SSL", e);
        }
    }
}
