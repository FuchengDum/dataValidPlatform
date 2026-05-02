package com.example.datavalidator.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name = "validation_finding")
public class ValidationFindingEntity {
    @Id
    @Column(name = "finding_id")
    private String findingId;
    @Column(name = "job_id", nullable = false)
    private String jobId;
    @Column(name = "rule_id", nullable = false)
    private String ruleId;
    @Column(name = "rule_name", nullable = false)
    private String ruleName;
    @Column(name = "rule_category", nullable = false)
    private String ruleCategory;
    @Column(name = "severity", nullable = false)
    private String severity;
    @Column(name = "table_name")
    private String tableName;
    @Column(name = "record_key")
    private String recordKey;
    @Column(name = "field_name")
    private String fieldName;
    @Lob
    @Column(name = "actual_value")
    private String actualValue;
    @Lob
    @Column(name = "expected_value")
    private String expectedValue;
    @Lob
    @Column(name = "description")
    private String description;
    @Lob
    @Column(name = "reason")
    private String reason;
    @Lob
    @Column(name = "impact")
    private String impact;
    @Lob
    @Column(name = "suggestion")
    private String suggestion;
    @Column(name = "scenario_ids")
    private String scenarioIds;

    public String getFindingId() { return findingId; }
    public void setFindingId(String findingId) { this.findingId = findingId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleCategory() { return ruleCategory; }
    public void setRuleCategory(String ruleCategory) { this.ruleCategory = ruleCategory; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
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
    public String getScenarioIds() { return scenarioIds; }
    public void setScenarioIds(String scenarioIds) { this.scenarioIds = scenarioIds; }
}
