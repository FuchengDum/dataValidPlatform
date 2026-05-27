package com.example.datavalidator.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name = "rule_binding")
public class RuleBindingEntity {
    @Id
    @Column(name = "id")
    private String id;
    @Column(name = "dataset_id", nullable = false)
    private String datasetId;
    @Column(name = "rule_id", nullable = false)
    private String ruleId;
    @Column(name = "executor_type", nullable = false)
    private String executorType;
    @Column(name = "builtin_executor_name")
    private String builtinExecutorName;
    @Column(name = "template_code")
    private String templateCode;
    @Lob
    @Column(name = "template_params_json")
    private String templateParamsJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getExecutorType() { return executorType; }
    public void setExecutorType(String executorType) { this.executorType = executorType; }
    public String getBuiltinExecutorName() { return builtinExecutorName; }
    public void setBuiltinExecutorName(String builtinExecutorName) { this.builtinExecutorName = builtinExecutorName; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getTemplateParamsJson() { return templateParamsJson; }
    public void setTemplateParamsJson(String templateParamsJson) { this.templateParamsJson = templateParamsJson; }
}
