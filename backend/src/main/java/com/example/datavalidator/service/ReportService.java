package com.example.datavalidator.service;

import com.example.datavalidator.config.AppProperties;
import com.example.datavalidator.persistence.ReportFileEntity;
import com.example.datavalidator.persistence.ValidationFindingEntity;
import com.example.datavalidator.repository.ReportFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportService {
    private final ValidationService validationService;
    private final ReportFileRepository reportFileRepository;
    private final AppProperties appProperties;

    public ReportService(ValidationService validationService,
                         ReportFileRepository reportFileRepository,
                         AppProperties appProperties) {
        this.validationService = validationService;
        this.reportFileRepository = reportFileRepository;
        this.appProperties = appProperties;
    }

    @Transactional
    public ReportResult generate(String jobId, String format) {
        String normalizedFormat = format == null || format.isEmpty() ? "MARKDOWN" : format.toUpperCase();
        ValidationService.SummaryResult summary = validationService.summary(jobId);
        List<ValidationFindingEntity> findings = validationService.listFindings(jobId, null, null, null);
        String reportId = IdFactory.next("rpt");
        String extension = "HTML".equals(normalizedFormat) ? ".html" : ".md";
        try {
            Path reportDir = Path.of(appProperties.getStorage().getReportDir());
            Files.createDirectories(reportDir);
            Path filePath = reportDir.resolve(reportId + extension);
            Files.write(filePath, render(normalizedFormat, summary, findings).getBytes(StandardCharsets.UTF_8));

            ReportFileEntity entity = new ReportFileEntity();
            entity.setReportId(reportId);
            entity.setJobId(jobId);
            entity.setFormat(normalizedFormat);
            entity.setFilePath(filePath.toAbsolutePath().toString());
            entity.setGeneratedAt(LocalDateTime.now());
            reportFileRepository.save(entity);
            return new ReportResult(reportId, normalizedFormat, filePath.toAbsolutePath().toString());
        } catch (Exception ex) {
            throw new IllegalStateException("报告生成失败: " + ex.getMessage(), ex);
        }
    }

    public ReportFileEntity getReport(String reportId) {
        return reportFileRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在: " + reportId));
    }

    private String render(String format, ValidationService.SummaryResult summary, List<ValidationFindingEntity> findings) {
        StringBuilder builder = new StringBuilder();
        if ("HTML".equals(format)) {
            builder.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>核验报告</title></head><body>");
        }
        builder.append("# 业务数据准确性核验报告\n\n");
        builder.append("## 汇总\n\n");
        builder.append("- 任务编号：").append(summary.jobId).append("\n");
        builder.append("- 规则总数：").append(summary.totalRules).append("\n");
        builder.append("- 已执行规则数：").append(summary.executedRules).append("\n");
        builder.append("- 异常总数：").append(summary.findingCount).append("\n");
        builder.append("- 严重异常：").append(summary.criticalCount).append("\n");
        builder.append("- 警告异常：").append(summary.warningCount).append("\n");
        builder.append("- 校验耗时：").append(summary.durationMillis).append(" ms\n\n");
        builder.append("## 异常明细\n\n");
        builder.append("| 规则 | 等级 | 表 | 主键 | 描述 | 建议 |\n");
        builder.append("|---|---|---|---|---|---|\n");
        for (ValidationFindingEntity finding : findings) {
            builder.append("| ")
                    .append(finding.getRuleId()).append(" ")
                    .append(finding.getRuleName()).append(" | ")
                    .append(finding.getSeverity()).append(" | ")
                    .append(nullToEmpty(finding.getTableName())).append(" | ")
                    .append(nullToEmpty(finding.getRecordKey())).append(" | ")
                    .append(clean(finding.getDescription())).append(" | ")
                    .append(clean(finding.getSuggestion())).append(" |\n");
        }
        if ("HTML".equals(format)) {
            builder.append("</body></html>");
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String clean(String value) {
        return nullToEmpty(value).replace("|", "/").replace("\n", " ");
    }

    public static class ReportResult {
        private final String reportId;
        private final String format;
        private final String filePath;

        public ReportResult(String reportId, String format, String filePath) {
            this.reportId = reportId;
            this.format = format;
            this.filePath = filePath;
        }

        public String getReportId() { return reportId; }
        public String getFormat() { return format; }
        public String getFilePath() { return filePath; }
    }
}
