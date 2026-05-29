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
        private JdbcConfig jdbc = new JdbcConfig();
        private List<TableConfig> tables = new ArrayList<>();

        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public JdbcConfig getJdbc() { return jdbc; }
        public void setJdbc(JdbcConfig jdbc) { this.jdbc = jdbc; }
        public List<TableConfig> getTables() { return tables; }
        public void setTables(List<TableConfig> tables) { this.tables = tables; }
    }

    public static class JdbcConfig {
        private String url;
        private String driverClassName;
        private String username;
        private String password;
        private String passwordEnv;
        private String dialect = "h2";
        private int connectionTimeoutMs = 3000;
        private int queryTimeoutSeconds = 30;
        private int fetchSize = 500;
        private int maxRows = 10000;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getPasswordEnv() { return passwordEnv; }
        public void setPasswordEnv(String passwordEnv) { this.passwordEnv = passwordEnv; }
        public String getDialect() { return dialect; }
        public void setDialect(String dialect) { this.dialect = dialect; }
        public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
        public void setConnectionTimeoutMs(int connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
        public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
        public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
        public int getFetchSize() { return fetchSize; }
        public void setFetchSize(int fetchSize) { this.fetchSize = fetchSize; }
        public int getMaxRows() { return maxRows; }
        public void setMaxRows(int maxRows) { this.maxRows = maxRows; }
    }

    public static class TableConfig {
        private String logicalName;
        private String physicalName;
        private String primaryKey;
        private String sql;
        private String file;
        private String format;
        private String encoding = "UTF-8";
        private String delimiter = ",";
        private String sheet;
        private int headerRow = 1;
        private int dataStartRow = 2;
        private List<String> nullValues = new ArrayList<>();
        private List<String> headers = new ArrayList<>();
        private Map<String, String> fieldMappings = new LinkedHashMap<>();
        private List<Map<String, Object>> rows = new ArrayList<>();

        public String getLogicalName() { return logicalName; }
        public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
        public String getPhysicalName() { return physicalName; }
        public void setPhysicalName(String physicalName) { this.physicalName = physicalName; }
        public String getPrimaryKey() { return primaryKey; }
        public void setPrimaryKey(String primaryKey) { this.primaryKey = primaryKey; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
        public String getSheet() { return sheet; }
        public void setSheet(String sheet) { this.sheet = sheet; }
        public int getHeaderRow() { return headerRow; }
        public void setHeaderRow(int headerRow) { this.headerRow = headerRow; }
        public int getDataStartRow() { return dataStartRow; }
        public void setDataStartRow(int dataStartRow) { this.dataStartRow = dataStartRow; }
        public List<String> getNullValues() { return nullValues; }
        public void setNullValues(List<String> nullValues) { this.nullValues = nullValues; }
        public List<String> getHeaders() { return headers; }
        public void setHeaders(List<String> headers) { this.headers = headers; }
        public Map<String, String> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(Map<String, String> fieldMappings) { this.fieldMappings = fieldMappings; }
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
        private boolean noReport;
        private String commandSummary;

        public String getFailOnSeverity() { return failOnSeverity; }
        public void setFailOnSeverity(String failOnSeverity) { this.failOnSeverity = failOnSeverity; }
        public List<String> getFormats() { return formats; }
        public void setFormats(List<String> formats) { this.formats = formats; }
        public String getOutputDir() { return outputDir; }
        public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
        public boolean isNoReport() { return noReport; }
        public void setNoReport(boolean noReport) { this.noReport = noReport; }
        public String getCommandSummary() { return commandSummary; }
        public void setCommandSummary(String commandSummary) { this.commandSummary = commandSummary; }
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
