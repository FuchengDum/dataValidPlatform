package com.example.datavalidator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Storage storage = new Storage();
    private final Validation validation = new Validation();
    private final Ai ai = new Ai();

    public Storage getStorage() {
        return storage;
    }

    public Validation getValidation() {
        return validation;
    }

    public Ai getAi() {
        return ai;
    }

    public static class Storage {
        private String uploadDir = "data/uploads";
        private String reportDir = "data/reports";

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }

        public String getReportDir() {
            return reportDir;
        }

        public void setReportDir(String reportDir) {
            this.reportDir = reportDir;
        }
    }

    public static class Validation {
        private boolean enableAiDefault;
        private boolean stopOnRuleError;

        public boolean isEnableAiDefault() {
            return enableAiDefault;
        }

        public void setEnableAiDefault(boolean enableAiDefault) {
            this.enableAiDefault = enableAiDefault;
        }

        public boolean isStopOnRuleError() {
            return stopOnRuleError;
        }

        public void setStopOnRuleError(boolean stopOnRuleError) {
            this.stopOnRuleError = stopOnRuleError;
        }
    }

    public static class Ai {
        private boolean enabled;
        private String provider = "openai-compatible";
        private String endpoint = "";
        private String apiKey = "";
        private String model = "local-model";
        private int timeoutSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
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

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
