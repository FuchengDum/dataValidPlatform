package com.example.datavalidator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Storage storage = new Storage();
    private final Validation validation = new Validation();

    public Storage getStorage() {
        return storage;
    }

    public Validation getValidation() {
        return validation;
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
}
