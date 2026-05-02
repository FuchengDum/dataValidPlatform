package com.example.datavalidator.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataRow {
    private int rowIndex;
    private String primaryKey;
    private Map<String, String> values = new LinkedHashMap<>();

    public int getRowIndex() { return rowIndex; }
    public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
    public String getPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(String primaryKey) { this.primaryKey = primaryKey; }
    public Map<String, String> getValues() { return values; }
    public void setValues(Map<String, String> values) { this.values = values; }

    public String value(String field) {
        return values.getOrDefault(field, "");
    }
}
