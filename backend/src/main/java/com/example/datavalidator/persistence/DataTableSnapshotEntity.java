package com.example.datavalidator.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name = "data_table_snapshot")
public class DataTableSnapshotEntity {
    @Id
    private String id;
    @Column(name = "dataset_id", nullable = false)
    private String datasetId;
    @Column(name = "sheet_name")
    private String sheetName;
    @Column(name = "logical_name", nullable = false)
    private String logicalName;
    @Column(name = "source_type", nullable = false)
    private String sourceType;
    @Lob
    @Column(name = "headers_json", nullable = false)
    private String headersJson;
    @Column(name = "row_count", nullable = false)
    private int rowCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    public String getLogicalName() { return logicalName; }
    public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
}
