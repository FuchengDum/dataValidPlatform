package com.example.datavalidator.controller;

import com.example.datavalidator.service.RuleBindingService;
import com.example.datavalidator.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {
    private final RuleBindingService ruleBindingService;

    public RuleController(RuleBindingService ruleBindingService) {
        this.ruleBindingService = ruleBindingService;
    }

    @GetMapping
    public ApiResponse<List<RuleBindingService.RuleView>> list(@RequestParam String datasetId) {
        return ApiResponse.ok(ruleBindingService.listRules(datasetId));
    }

    @GetMapping("/{datasetId}/{ruleId}/binding")
    public ApiResponse<RuleBindingService.BindingView> binding(@PathVariable String datasetId,
                                                               @PathVariable String ruleId) {
        return ApiResponse.ok(ruleBindingService.getBinding(datasetId, ruleId));
    }

    @PutMapping("/{datasetId}/{ruleId}/binding")
    public ApiResponse<RuleBindingService.BindingView> updateBinding(
            @PathVariable String datasetId,
            @PathVariable String ruleId,
            @RequestBody RuleBindingService.BindingRequest request) {
        return ApiResponse.ok(ruleBindingService.updateBinding(datasetId, ruleId, request));
    }
}
