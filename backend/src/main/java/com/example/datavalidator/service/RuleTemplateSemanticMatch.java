package com.example.datavalidator.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class RuleTemplateSemanticMatch {
    private String templateCode;
    private Map<String, Object> templateParams = new LinkedHashMap<>();
    private String confidence = "MEDIUM";
    private String matchedReason;
    private List<String> warnings = new ArrayList<>();
    private boolean applicable;

    static RuleTemplateSemanticMatch unavailable(String reason) {
        RuleTemplateSemanticMatch match = new RuleTemplateSemanticMatch();
        match.setApplicable(false);
        match.setConfidence("LOW");
        match.setMatchedReason(reason);
        match.getWarnings().add(reason);
        return match;
    }

    String getTemplateCode() {
        return templateCode;
    }

    void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    Map<String, Object> getTemplateParams() {
        return templateParams;
    }

    void setTemplateParams(Map<String, Object> templateParams) {
        this.templateParams = templateParams;
    }

    String getConfidence() {
        return confidence;
    }

    void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    String getMatchedReason() {
        return matchedReason;
    }

    void setMatchedReason(String matchedReason) {
        this.matchedReason = matchedReason;
    }

    List<String> getWarnings() {
        return warnings;
    }

    boolean isApplicable() {
        return applicable;
    }

    void setApplicable(boolean applicable) {
        this.applicable = applicable;
    }
}
