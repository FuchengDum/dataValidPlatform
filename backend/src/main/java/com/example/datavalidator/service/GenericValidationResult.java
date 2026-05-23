package com.example.datavalidator.service;

import com.example.datavalidator.domain.ValidationFinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GenericValidationResult {
    private String reportVersion = "1";
    private String toolVersion;
    private String rulePackageHash;
    private SourceSummary sourceSummary = new SourceSummary();
    private ExecutionSummary execution = new ExecutionSummary();
    private int totalRules;
    private int executedRules;
    private int findingCount;
    private int criticalCount;
    private int warningCount;
    private long durationMillis;
    private List<ValidationFinding> findings;
    private Map<String, String> reports = new LinkedHashMap<>();

    public String getReportVersion() { return reportVersion; }
    public void setReportVersion(String reportVersion) { this.reportVersion = reportVersion; }
    public String getToolVersion() { return toolVersion; }
    public void setToolVersion(String toolVersion) { this.toolVersion = toolVersion; }
    public String getRulePackageHash() { return rulePackageHash; }
    public void setRulePackageHash(String rulePackageHash) { this.rulePackageHash = rulePackageHash; }
    public SourceSummary getSourceSummary() { return sourceSummary; }
    public void setSourceSummary(SourceSummary sourceSummary) { this.sourceSummary = sourceSummary; }
    public ExecutionSummary getExecution() { return execution; }
    public void setExecution(ExecutionSummary execution) { this.execution = execution; }
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

    public static class SourceSummary {
        private String type;
        private int tableCount;
        private int totalRows;
        private List<TableSummary> tables = new ArrayList<>();

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public int getTableCount() { return tableCount; }
        public void setTableCount(int tableCount) { this.tableCount = tableCount; }
        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
        public List<TableSummary> getTables() { return tables; }
        public void setTables(List<TableSummary> tables) { this.tables = tables; }
    }

    public static class TableSummary {
        private String logicalName;
        private int rowCount;
        private int fieldCount;

        public String getLogicalName() { return logicalName; }
        public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
        public int getRowCount() { return rowCount; }
        public void setRowCount(int rowCount) { this.rowCount = rowCount; }
        public int getFieldCount() { return fieldCount; }
        public void setFieldCount(int fieldCount) { this.fieldCount = fieldCount; }
    }

    public static class ExecutionSummary {
        private String startedAt;
        private long durationMillis;
        private String command;

        public String getStartedAt() { return startedAt; }
        public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
        public long getDurationMillis() { return durationMillis; }
        public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
    }
}
