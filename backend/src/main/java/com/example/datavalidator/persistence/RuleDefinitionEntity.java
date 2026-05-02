package com.example.datavalidator.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "rule_definition")
@IdClass(RuleDefinitionEntity.Key.class)
public class RuleDefinitionEntity {
    @Id
    @Column(name = "rule_id")
    private String ruleId;
    @Id
    @Column(name = "dataset_id")
    private String datasetId;
    @Column(name = "rule_name", nullable = false)
    private String ruleName;
    @Column(name = "category", nullable = false)
    private String category;
    @Column(name = "applicable_tables")
    private String applicableTables;
    @Lob
    @Column(name = "description")
    private String description;
    @Lob
    @Column(name = "pseudo_logic")
    private String pseudoLogic;
    @Column(name = "severity", nullable = false)
    private String severity;
    @Lob
    @Column(name = "example")
    private String example;
    @Column(name = "scenario_ids")
    private String scenarioIds;
    @Column(name = "executor_type")
    private String executorType;
    @Column(name = "template_code")
    private String templateCode;

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getApplicableTables() { return applicableTables; }
    public void setApplicableTables(String applicableTables) { this.applicableTables = applicableTables; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPseudoLogic() { return pseudoLogic; }
    public void setPseudoLogic(String pseudoLogic) { this.pseudoLogic = pseudoLogic; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }
    public String getScenarioIds() { return scenarioIds; }
    public void setScenarioIds(String scenarioIds) { this.scenarioIds = scenarioIds; }
    public String getExecutorType() { return executorType; }
    public void setExecutorType(String executorType) { this.executorType = executorType; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public static class Key implements Serializable {
        private String ruleId;
        private String datasetId;
        public Key() {}
        public Key(String ruleId, String datasetId) {
            this.ruleId = ruleId;
            this.datasetId = datasetId;
        }
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getDatasetId() { return datasetId; }
        public void setDatasetId(String datasetId) { this.datasetId = datasetId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key key = (Key) o;
            return Objects.equals(ruleId, key.ruleId)
                    && Objects.equals(datasetId, key.datasetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ruleId, datasetId);
        }
    }
}
