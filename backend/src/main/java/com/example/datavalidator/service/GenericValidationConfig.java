package com.example.datavalidator.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GenericValidationConfig {
    private String schemaVersion;
    private SourceConfig source = new SourceConfig();
    private RuleConfig rules = new RuleConfig();
    private ValidationConfig validation = new ValidationConfig();
    private AiConfig ai = new AiConfig();

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public SourceConfig getSource() { return source; }
    public void setSource(SourceConfig source) { this.source = source; }
    public RuleConfig getRules() { return rules; }
    public void setRules(RuleConfig rules) { this.rules = rules; }
    public ValidationConfig getValidation() { return validation; }
    public void setValidation(ValidationConfig validation) { this.validation = validation; }
    public AiConfig getAi() { return ai; }
    public void setAi(AiConfig ai) { this.ai = ai; }

    public static class SourceConfig {
        private String schemaVersion;
        private String type = "file";
        private String file;
        private List<TableConfig> tables = new ArrayList<>();

        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public List<TableConfig> getTables() { return tables; }
        public void setTables(List<TableConfig> tables) { this.tables = tables; }
    }

    public static class TableConfig {
        private String logicalName;
        private String physicalName;
        private String primaryKey;
        private String sql;
        private List<String> headers = new ArrayList<>();
        private List<Map<String, Object>> rows = new ArrayList<>();

        public String getLogicalName() { return logicalName; }
        public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
        public String getPhysicalName() { return physicalName; }
        public void setPhysicalName(String physicalName) { this.physicalName = physicalName; }
        public String getPrimaryKey() { return primaryKey; }
        public void setPrimaryKey(String primaryKey) { this.primaryKey = primaryKey; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public List<String> getHeaders() { return headers; }
        public void setHeaders(List<String> headers) { this.headers = headers; }
        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
    }

    public static class RuleConfig {
        private String file;

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
    }

    public static class ValidationConfig {
        private String failOnSeverity = "CRITICAL";
        private List<String> formats = new ArrayList<>();
        private String outputDir = "reports";

        public String getFailOnSeverity() { return failOnSeverity; }
        public void setFailOnSeverity(String failOnSeverity) { this.failOnSeverity = failOnSeverity; }
        public List<String> getFormats() { return formats; }
        public void setFormats(List<String> formats) { this.formats = formats; }
        public String getOutputDir() { return outputDir; }
        public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    }

    public static class AiConfig {
        private boolean enabled;
        private Map<String, Object> options = new LinkedHashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Map<String, Object> getOptions() { return options; }
        public void setOptions(Map<String, Object> options) { this.options = options; }
    }
}
