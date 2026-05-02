package com.example.datavalidator.controller;

import com.example.datavalidator.persistence.ValidationFindingEntity;
import com.example.datavalidator.service.ValidationService;
import com.example.datavalidator.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/findings")
public class FindingController {
    private final ValidationService validationService;

    public FindingController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam String jobId,
                                                 @RequestParam(required = false) String severity,
                                                 @RequestParam(required = false) String tableName,
                                                 @RequestParam(required = false) String ruleId) {
        List<ValidationFindingEntity> items = validationService.listFindings(jobId, severity, tableName, ruleId);
        Map<String, Object> result = new HashMap<>();
        result.put("total", items.size());
        result.put("items", items);
        return ApiResponse.ok(result);
    }

    @GetMapping("/{findingId}")
    public ApiResponse<ValidationService.FindingDetail> detail(@PathVariable String findingId) {
        return ApiResponse.ok(validationService.detail(findingId));
    }
}
