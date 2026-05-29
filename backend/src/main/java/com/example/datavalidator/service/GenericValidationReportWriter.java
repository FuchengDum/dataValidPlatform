package com.example.datavalidator.service;

import com.example.datavalidator.domain.ValidationFinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class GenericValidationReportWriter {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ObjectMapper objectMapper;

    public GenericValidationReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeReports(GenericValidationResult result, GenericValidationConfig.ValidationConfig config,
                             Path baseDir) {
        if (config.isNoReport()) {
            return;
        }
        Path outputDir = resolve(baseDir, config.getOutputDir());
        try {
            Files.createDirectories(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("报告目录创建失败: " + outputDir, ex);
        }
        List<String> formats = config.getFormats().isEmpty()
                ? java.util.Arrays.asList("json", "markdown") : config.getFormats();
        for (String rawFormat : formats) {
            String format = rawFormat == null ? "" : rawFormat.trim().toLowerCase();
            if ("json".equals(format)) {
                Path path = outputDir.resolve(fileName("json"));
                write(path, json(result));
                result.getReports().put("json", path.toString());
            } else if ("html".equals(format)) {
                Path path = outputDir.resolve(fileName("html"));
                write(path, html(result));
                result.getReports().put("html", path.toString());
            } else if ("junit".equals(format) || "junitxml".equals(format)) {
                Path path = outputDir.resolve(fileName("xml"));
                write(path, junit(result));
                result.getReports().put("junit", path.toString());
            } else {
                Path path = outputDir.resolve(fileName("md"));
                write(path, markdown(result));
                result.getReports().put("markdown", path.toString());
            }
        }
    }

    private String json(GenericValidationResult result) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON 报告生成失败", ex);
        }
    }

    private String markdown(GenericValidationResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 通用数据验证报告\n\n");
        builder.append("- 总规则数: ").append(result.getTotalRules()).append('\n');
        builder.append("- 已执行规则数: ").append(result.getExecutedRules()).append('\n');
        builder.append("- 异常总数: ").append(result.getFindingCount()).append('\n');
        builder.append("- 严重异常: ").append(result.getCriticalCount()).append('\n');
        builder.append("- 警告异常: ").append(result.getWarningCount()).append('\n');
        builder.append("- 校验耗时: ").append(result.getDurationMillis()).append(" ms\n\n");
        builder.append("| 规则 | 等级 | 表 | 主键 | 字段 | 描述 |\n");
        builder.append("|---|---|---|---|---|---|\n");
        for (ValidationFinding finding : result.getFindings()) {
            builder.append('|').append(safe(finding.getRuleId()))
                    .append('|').append(finding.getSeverity())
                    .append('|').append(safe(finding.getTableName()))
                    .append('|').append(safe(finding.getRecordKey()))
                    .append('|').append(safe(finding.getFieldName()))
                    .append('|').append(safe(finding.getDescription()))
                    .append("|\n");
        }
        return builder.toString();
    }

    private String html(GenericValidationResult result) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>通用数据验证报告</title>"
                + "<style>body{font-family:sans-serif;margin:24px}table{border-collapse:collapse;width:100%}"
                + "td,th{border:1px solid #ddd;padding:6px}th{background:#f5f5f5}</style></head><body>"
                + markdown(result).replace("\n", "<br>")
                + "</body></html>";
    }

    private String junit(GenericValidationResult result) {
        int failures = result.getFindingCount();
        int tests = Math.max(1, failures);
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<testsuite name=\"generic-validation\" tests=\"").append(tests)
                .append("\" failures=\"").append(failures)
                .append("\" errors=\"0\" skipped=\"0\" time=\"")
                .append(result.getDurationMillis() / 1000.0).append("\">\n");
        if (result.getFindings() == null || result.getFindings().isEmpty()) {
            builder.append("  <testcase classname=\"generic-validation\" name=\"no-findings\"/>\n");
        } else {
            for (ValidationFinding finding : result.getFindings()) {
                builder.append("  <testcase classname=\"")
                        .append(xml(safe(finding.getTableName())))
                        .append("\" name=\"")
                        .append(xml(safe(finding.getRuleId())))
                        .append("\">\n");
                builder.append("    <failure message=\"")
                        .append(xml(safe(finding.getDescription())))
                        .append("\" type=\"")
                        .append(finding.getSeverity())
                        .append("\">")
                        .append(xml(failureText(finding)))
                        .append("</failure>\n");
                builder.append("  </testcase>\n");
            }
        }
        builder.append("</testsuite>\n");
        return builder.toString();
    }

    private String failureText(ValidationFinding finding) {
        return "ruleId=" + safe(finding.getRuleId())
                + ", table=" + safe(finding.getTableName())
                + ", recordKey=" + safe(finding.getRecordKey())
                + ", field=" + safe(finding.getFieldName())
                + ", expected=" + safe(finding.getExpectedValue())
                + ", actual=" + safe(finding.getActualValue());
    }

    private void write(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("报告写入失败: " + path, ex);
        }
    }

    private Path resolve(Path baseDir, String outputDir) {
        Path path = Path.of(outputDir == null || outputDir.trim().isEmpty() ? "reports" : outputDir);
        return path.isAbsolute() ? path : baseDir.resolve(path).normalize();
    }

    private String fileName(String extension) {
        return "validation-report-" + LocalDateTime.now().format(STAMP) + "." + extension;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private String xml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
