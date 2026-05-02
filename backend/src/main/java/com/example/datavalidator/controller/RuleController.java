package com.example.datavalidator.controller;

import com.example.datavalidator.persistence.RuleDefinitionEntity;
import com.example.datavalidator.repository.RuleDefinitionRepository;
import com.example.datavalidator.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {
    private final RuleDefinitionRepository ruleDefinitionRepository;

    public RuleController(RuleDefinitionRepository ruleDefinitionRepository) {
        this.ruleDefinitionRepository = ruleDefinitionRepository;
    }

    @GetMapping
    public ApiResponse<List<RuleDefinitionEntity>> list(@RequestParam String datasetId) {
        return ApiResponse.ok(ruleDefinitionRepository.findByDatasetIdOrderByRuleId(datasetId));
    }
}
