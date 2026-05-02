package com.example.datavalidator.domain;

import java.util.ArrayList;
import java.util.List;

public class ValidationFinding {
    private String findingId;
    private String ruleId;
    private String ruleName;
    private RuleCategory ruleCategory;
    private Severity severity;
    private String tableName;
    private String recordKey;
    private String fieldName;
    private String actualValue;
    private String expectedValue;
    private String description;
    private String reason;
    private String impact;
    private String suggestion;
    private List<String> scenarioIds = new ArrayList<>();
    private List<Evidence> evidences = new ArrayList<>();

    public String getFindingId() { return findingId; }
    public void setFindingId(String findingId) { this.findingId = findingId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public RuleCategory getRuleCategory() { return ruleCategory; }
    public void setRuleCategory(RuleCategory ruleCategory) { this.ruleCategory = ruleCategory; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getRecordKey() { return recordKey; }
    public void setRecordKey(String recordKey) { this.recordKey = recordKey; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getActualValue() { return actualValue; }
    public void setActualValue(String actualValue) { this.actualValue = actualValue; }
    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public List<String> getScenarioIds() { return scenarioIds; }
    public void setScenarioIds(List<String> scenarioIds) { this.scenarioIds = scenarioIds; }
    public List<Evidence> getEvidences() { return evidences; }
    public void setEvidences(List<Evidence> evidences) { this.evidences = evidences; }
}
