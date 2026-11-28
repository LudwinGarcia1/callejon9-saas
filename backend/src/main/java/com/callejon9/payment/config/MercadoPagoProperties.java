package com.callejon9.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment.mercadopago")
public class MercadoPagoProperties {

    private String accessToken = "";
    private String publicKey = "";
    private String webhookSecret = "";
    private boolean mockEnabled = true;
    private boolean sandbox = true;
    private String baseUrl = "https://api.mercadopago.com";
    private String frontendBaseUrl = "http://localhost:3000";
    private String backendBaseUrl = "http://localhost:8080";

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken != null ? accessToken.trim() : "";
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey != null ? publicKey.trim() : "";
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret != null ? webhookSecret.trim() : "";
    }

    public boolean isMockEnabled() {
        return mockEnabled || accessToken.isBlank();
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://api.mercadopago.com";
    }

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl != null ? frontendBaseUrl.trim() : "http://localhost:3000";
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public void setBackendBaseUrl(String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl != null ? backendBaseUrl.trim() : "http://localhost:8080";
    }
}
