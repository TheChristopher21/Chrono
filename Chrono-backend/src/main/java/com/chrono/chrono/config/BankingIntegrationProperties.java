package com.chrono.chrono.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "banking.integrations")
public class BankingIntegrationProperties {

    private final Payments payments = new Payments();
    private final Signatures signatures = new Signatures();
    private final Messages messages = new Messages();

    public Payments getPayments() {
        return payments;
    }

    public Signatures getSignatures() {
        return signatures;
    }

    public Messages getMessages() {
        return messages;
    }

    public static class Payments {
        private boolean enabled;
        private Mode mode = Mode.SIMULATED;
        private String endpoint;
        private String apiKey;
        private String contentType = "application/xml";
        private String exportDirectory;
        private String exportChannel = "EBICS";
        private boolean exportMetadata = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getExportDirectory() {
            return exportDirectory;
        }

        public void setExportDirectory(String exportDirectory) {
            this.exportDirectory = exportDirectory;
        }

        public String getExportChannel() {
            return exportChannel;
        }

        public void setExportChannel(String exportChannel) {
            this.exportChannel = exportChannel;
        }

        public boolean isExportMetadata() {
            return exportMetadata;
        }

        public void setExportMetadata(boolean exportMetadata) {
            this.exportMetadata = exportMetadata;
        }

        public enum Mode {
            SIMULATED,
            API,
            BANK_EXPORT
        }
    }

    public static class Signatures {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private Duration pollInterval = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }
    }

    public static class Messages {
        private boolean enabled;
        private String endpoint;
        private String apiKey;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
