package com.prodbuddy.tools.splunk;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLParameters;
import java.security.cert.X509Certificate;

public final class SplunkHttpClientFactory {

    private SplunkHttpClientFactory() {}

    public static HttpClient buildInsecure() {
        SSLParameters sslParams = new SSLParameters();
        sslParams.setEndpointIdentificationAlgorithm("");
        
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .sslContext(insecureSslContext())
                .sslParameters(sslParams)
                .build();
    }

    private static SSLContext insecureSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
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
