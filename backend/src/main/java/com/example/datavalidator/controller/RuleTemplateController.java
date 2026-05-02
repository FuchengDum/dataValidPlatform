package com.example.datavalidator.controller;

import com.example.datavalidator.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/rule-templates")
public class RuleTemplateController {
    @GetMapping
    public ApiResponse<List<String>> templates() {
        return ApiResponse.ok(Arrays.asList(
                "NOT_NULL", "NON_NEGATIVE", "NUMERIC_TYPE", "FIELD_EXPRESSION",
                "EXISTS_IN_TABLE", "FIELD_EQUALS", "AGGREGATION_EQUALS", "DUPLICATE_CHECK"));
    }
}
