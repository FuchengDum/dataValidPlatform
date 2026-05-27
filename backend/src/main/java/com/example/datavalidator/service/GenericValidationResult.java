package com.example.datavalidator.service;

import com.example.datavalidator.domain.ValidationFinding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GenericValidationResult {
    private int totalRules;
    private int executedRules;
    private int findingCount;
    private int criticalCount;
    private int warningCount;
    private long durationMillis;
    private List<ValidationFinding> findings;
    private Map<String, String> reports = new LinkedHashMap<>();

    public int getTotalRules() { return totalRules; }
    public void setTotalRules(int totalRules) { this.totalRules = totalRules; }
    public int getExecutedRules() { return executedRules; }
    public void setExecutedRules(int executedRules) { this.executedRules = executedRules; }
    public int getFindingCount() { return findingCount; }
    public void setFindingCount(int findingCount) { this.findingCount = findingCount; }
    public int getCriticalCount() { return criticalCount; }
    public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }
    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }
    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
    public List<ValidationFinding> getFindings() { return findings; }
    public void setFindings(List<ValidationFinding> findings) { this.findings = findings; }
    public Map<String, String> getReports() { return reports; }
    public void setReports(Map<String, String> reports) { this.reports = reports; }
}
