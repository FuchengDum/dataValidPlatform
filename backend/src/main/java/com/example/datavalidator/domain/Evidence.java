package com.example.datavalidator.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class Evidence {
    private String evidenceType;
    private String tableName;
    private String recordKey;
    private String fieldName;
    private String actualValue;
    private String expectedValue;
    private String calculation;
    private Map<String, String> relatedValues = new LinkedHashMap<>();

    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
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
    public String getCalculation() { return calculation; }
    public void setCalculation(String calculation) { this.calculation = calculation; }
    public Map<String, String> getRelatedValues() { return relatedValues; }
    public void setRelatedValues(Map<String, String> relatedValues) { this.relatedValues = relatedValues; }
}
