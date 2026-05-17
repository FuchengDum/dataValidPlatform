package com.example.datavalidator.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GenericRulePackage {
    private String schemaVersion;
    private List<GenericRule> rules = new ArrayList<>();

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public List<GenericRule> getRules() { return rules; }
    public void setRules(List<GenericRule> rules) { this.rules = rules; }

    public static class GenericRule {
        private String ruleId;
        private String ruleName;
        private String category = "SINGLE_BUSINESS_RULE";
        private String severity = "CRITICAL";
        private String description;
        private List<String> applicableTables = new ArrayList<>();
        private List<String> scenarioIds = new ArrayList<>();
        private boolean enabled = true;
        private String templateCode;
        private Map<String, Object> templateParams = new LinkedHashMap<>();

        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getApplicableTables() { return applicableTables; }
        public void setApplicableTables(List<String> applicableTables) { this.applicableTables = applicableTables; }
        public List<String> getScenarioIds() { return scenarioIds; }
        public void setScenarioIds(List<String> scenarioIds) { this.scenarioIds = scenarioIds; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
        public Map<String, Object> getTemplateParams() { return templateParams; }
        public void setTemplateParams(Map<String, Object> templateParams) { this.templateParams = templateParams; }
    }
}
