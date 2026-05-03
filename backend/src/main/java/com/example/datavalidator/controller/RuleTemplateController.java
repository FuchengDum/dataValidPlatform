package com.example.datavalidator.controller;

import com.example.datavalidator.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rule-templates")
public class RuleTemplateController {
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> templates() {
        return ApiResponse.ok(Arrays.asList(
                template("NOT_NULL", "字段非空校验", "tableName", "fields"),
                template("NON_NEGATIVE", "数值非负校验", "tableName", "fields"),
                template("NUMERIC_TYPE", "数值类型校验", "tableName", "fields"),
                template("FIELD_EXPRESSION", "字段表达式校验", "tableName", "expression"),
                template("EXISTS_IN_TABLE", "跨表存在性校验", "source", "target", "key"),
                template("FIELD_EQUALS", "跨表字段一致性校验", "source", "target", "sourceField", "targetField"),
                template("AGGREGATION_EQUALS", "聚合一致性校验", "source", "target", "groupBy", "sum"),
                template("DUPLICATE_CHECK", "重复记录校验", "tableName", "groupBy")));
    }

    private Map<String, Object> template(String code, String name, String... params) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("templateCode", code);
        template.put("templateName", name);
        template.put("params", Arrays.stream(params).collect(Collectors.toList()));
        return template;
    }
}
