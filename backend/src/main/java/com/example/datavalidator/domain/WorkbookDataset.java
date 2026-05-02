package com.example.datavalidator.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkbookDataset {
    private String datasetId;
    private DatasetSourceType sourceType = DatasetSourceType.EXCEL_WORKBOOK;
    private String sourceName;
    private String fileName;
    private Map<String, DataTable> businessTables = new LinkedHashMap<>();
    private List<RuleDefinition> rules = new ArrayList<>();

    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public DatasetSourceType getSourceType() { return sourceType; }
    public void setSourceType(DatasetSourceType sourceType) { this.sourceType = sourceType; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Map<String, DataTable> getBusinessTables() { return businessTables; }
    public void setBusinessTables(Map<String, DataTable> businessTables) { this.businessTables = businessTables; }
    public List<RuleDefinition> getRules() { return rules; }
    public void setRules(List<RuleDefinition> rules) { this.rules = rules; }
}
