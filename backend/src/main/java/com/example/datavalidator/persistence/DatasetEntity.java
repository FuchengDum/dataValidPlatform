package com.example.datavalidator.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "dataset")
public class DatasetEntity {
    @Id
    @Column(name = "dataset_id", length = 64)
    private String datasetId;
    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;
    @Column(name = "source_name")
    private String sourceName;
    @Column(name = "file_name")
    private String fileName;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
}
