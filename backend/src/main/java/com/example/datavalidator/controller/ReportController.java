package com.example.datavalidator.controller;

import com.example.datavalidator.persistence.ReportFileEntity;
import com.example.datavalidator.service.ReportService;
import com.example.datavalidator.web.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ApiResponse<ReportService.ReportResult> generate(@RequestBody ReportRequest request) {
        return ApiResponse.ok(reportService.generate(request.getJobId(), request.getFormat()));
    }

    @GetMapping("/{reportId}/download")
    public ResponseEntity<Resource> download(@PathVariable String reportId) {
        ReportFileEntity report = reportService.getReport(reportId);
        Resource resource = new FileSystemResource(Path.of(report.getFilePath()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    public static class ReportRequest {
        private String jobId;
        private String format;

        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }
}
