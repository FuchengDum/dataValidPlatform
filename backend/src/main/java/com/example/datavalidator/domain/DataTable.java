package com.example.datavalidator.domain;

import java.util.ArrayList;
import java.util.List;

public class DataTable {
    private String sheetName;
    private String logicalName;
    private DatasetSourceType sourceType = DatasetSourceType.EXCEL_WORKBOOK;
    private List<String> headers = new ArrayList<>();
    private List<DataRow> rows = new ArrayList<>();

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    public String getLogicalName() { return logicalName; }
    public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
    public DatasetSourceType getSourceType() { return sourceType; }
    public void setSourceType(DatasetSourceType sourceType) { this.sourceType = sourceType; }
    public List<String> getHeaders() { return headers; }
    public void setHeaders(List<String> headers) { this.headers = headers; }
    public List<DataRow> getRows() { return rows; }
    public void setRows(List<DataRow> rows) { this.rows = rows; }
}
