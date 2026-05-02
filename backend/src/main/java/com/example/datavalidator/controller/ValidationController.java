package com.example.datavalidator.controller;

import com.example.datavalidator.service.ValidationService;
import com.example.datavalidator.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/validations")
public class ValidationController {
    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping
    public ApiResponse<ValidationService.ValidationJobResult> validate(@RequestBody ValidationRequest request) {
        return ApiResponse.ok(validationService.validate(request.getDatasetId(), request.isEnableAiAnalysis()));
    }

    @GetMapping("/{jobId}/summary")
    public ApiResponse<ValidationService.SummaryResult> summary(@PathVariable String jobId) {
        return ApiResponse.ok(validationService.summary(jobId));
    }

    public static class ValidationRequest {
        private String datasetId;
        private boolean enableAiAnalysis;

        public String getDatasetId() { return datasetId; }
        public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
        public boolean isEnableAiAnalysis() { return enableAiAnalysis; }
        public void setEnableAiAnalysis(boolean enableAiAnalysis) { this.enableAiAnalysis = enableAiAnalysis; }
    }
}
