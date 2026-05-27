package com.example.datavalidator.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class RuleBinding {
    private String ruleId;
    private String executorType = "BUILTIN";
    private String builtinExecutorName;
    private String templateCode;
    private Map<String, Object> templateParams = new LinkedHashMap<>();
    private String bindingVersion = "v1";
    private boolean enabled = true;

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getExecutorType() { return executorType; }
    public void setExecutorType(String executorType) { this.executorType = executorType; }
    public String getBuiltinExecutorName() { return builtinExecutorName; }
    public void setBuiltinExecutorName(String builtinExecutorName) { this.builtinExecutorName = builtinExecutorName; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public Map<String, Object> getTemplateParams() { return templateParams; }
    public void setTemplateParams(Map<String, Object> templateParams) { this.templateParams = templateParams; }
    public String getBindingVersion() { return bindingVersion; }
    public void setBindingVersion(String bindingVersion) { this.bindingVersion = bindingVersion; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
