package com.example.datavalidator.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_file")
public class ReportFileEntity {
    @Id
    @Column(name = "report_id")
    private String reportId;
    @Column(name = "job_id", nullable = false)
    private String jobId;
    @Column(name = "format", nullable = false)
    private String format;
    @Column(name = "file_path", nullable = false)
    private String filePath;
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
