package com.example.datavalidator.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "validation_job")
public class ValidationJobEntity {
    @Id
    @Column(name = "job_id")
    private String jobId;
    @Column(name = "dataset_id", nullable = false)
    private String datasetId;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "enable_ai_analysis", nullable = false)
    private boolean enableAiAnalysis;
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    @Column(name = "duration_millis")
    private Long durationMillis;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isEnableAiAnalysis() { return enableAiAnalysis; }
    public void setEnableAiAnalysis(boolean enableAiAnalysis) { this.enableAiAnalysis = enableAiAnalysis; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(Long durationMillis) { this.durationMillis = durationMillis; }
}
