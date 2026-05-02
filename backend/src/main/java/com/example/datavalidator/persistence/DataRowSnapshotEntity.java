package com.example.datavalidator.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name = "data_row_snapshot")
public class DataRowSnapshotEntity {
    @Id
    private String id;
    @Column(name = "dataset_id", nullable = false)
    private String datasetId;
    @Column(name = "table_name", nullable = false)
    private String tableName;
    @Column(name = "row_index", nullable = false)
    private int rowIndex;
    @Column(name = "primary_key")
    private String primaryKey;
    @Lob
    @Column(name = "values_json", nullable = false)
    private String valuesJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public int getRowIndex() { return rowIndex; }
    public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
    public String getPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(String primaryKey) { this.primaryKey = primaryKey; }
    public String getValuesJson() { return valuesJson; }
    public void setValuesJson(String valuesJson) { this.valuesJson = valuesJson; }
}
