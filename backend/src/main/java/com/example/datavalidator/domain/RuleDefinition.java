package com.example.datavalidator.domain;

import java.util.ArrayList;
import java.util.List;

public class RuleDefinition {
    private String ruleId;
    private String ruleName;
    private RuleCategory category;
    private List<String> applicableTables = new ArrayList<>();
    private String description;
    private String pseudoLogic;
    private Severity severity;
    private String example;
    private List<String> scenarioIds = new ArrayList<>();
    private String executorType = "BUILTIN";
    private String templateCode;

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public RuleCategory getCategory() { return category; }
    public void setCategory(RuleCategory category) { this.category = category; }
    public List<String> getApplicableTables() { return applicableTables; }
    public void setApplicableTables(List<String> applicableTables) { this.applicableTables = applicableTables; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPseudoLogic() { return pseudoLogic; }
    public void setPseudoLogic(String pseudoLogic) { this.pseudoLogic = pseudoLogic; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }
    public List<String> getScenarioIds() { return scenarioIds; }
    public void setScenarioIds(List<String> scenarioIds) { this.scenarioIds = scenarioIds; }
    public String getExecutorType() { return executorType; }
    public void setExecutorType(String executorType) { this.executorType = executorType; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
}
