package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.DataTableSnapshotEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class TemplateBindingValidator {
    private TemplateBindingValidator() {
    }

    static void validate(String templateCode, Map<String, Object> params,
                         List<DataTableSnapshotEntity> tables, JsonService jsonService) {
        Map<String, List<String>> headersByTable = headersByTable(tables, jsonService);
        switch (templateCode) {
            case "NOT_NULL":
            case "NON_NEGATIVE":
            case "NUMERIC_TYPE":
                validateFieldList(params, headersByTable);
                break;
            case "FIELD_EXPRESSION":
                validateFieldExpression(params, headersByTable);
                break;
            case "EXISTS_IN_TABLE":
                validateExistsInTable(params, headersByTable);
                break;
            case "FIELD_EQUALS":
                validateFieldEquals(params, headersByTable);
                break;
            case "AGGREGATION_EQUALS":
                validateAggregationEquals(params, headersByTable);
                break;
            case "DUPLICATE_CHECK":
                validateDuplicateCheck(params, headersByTable);
                break;
            default:
                throw new BadRequestException("暂不支持的规则模板: " + templateCode);
        }
    }

    private static void validateFieldList(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        String tableName = requireParam(params, "tableName");
        List<String> headers = requireTable(headersByTable, tableName);
        List<String> fields = fields(params.get("fields"));
        if (fields.isEmpty()) {
            throw new BadRequestException("模板参数 fields 不能为空");
        }
        for (String field : fields) {
            requireField(headers, field);
        }
    }

    private static void validateFieldExpression(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        String tableName = requireParam(params, "tableName");
        List<String> headers = requireTable(headersByTable, tableName);
        String expression = requireParam(params, "expression");
        boolean hasField = false;
        for (String token : expression.split("\\s+")) {
            if (isExpressionOperator(token) || ValueParsers.decimal(token).isPresent()) {
                continue;
            }
            requireField(headers, token);
            hasField = true;
        }
        if (!hasField) {
            throw new BadRequestException("表达式未引用有效字段");
        }
    }

    private static void validateExistsInTable(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        List<String> sourceHeaders = requireTable(headersByTable, requireParam(params, "source"));
        List<String> targetHeaders = requireTable(headersByTable, requireParam(params, "target"));
        String key = requireParam(params, "key");
        requireField(sourceHeaders, key);
        requireField(targetHeaders, key);
    }

    private static void validateFieldEquals(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        List<String> sourceHeaders = requireTable(headersByTable, requireParam(params, "source"));
        List<String> targetHeaders = requireTable(headersByTable, requireParam(params, "target"));
        String key = requireParam(params, "key");
        requireField(sourceHeaders, key);
        requireField(targetHeaders, key);
        requireField(sourceHeaders, requireParam(params, "sourceField"));
        requireField(targetHeaders, requireParam(params, "targetField"));
    }

    private static void validateAggregationEquals(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        List<String> sourceHeaders = requireTable(headersByTable, requireParam(params, "source"));
        List<String> targetHeaders = requireTable(headersByTable, requireParam(params, "target"));
        String groupBy = requireParam(params, "groupBy");
        String targetKey = asString(params.get("targetKey"));
        if (isBlank(targetKey)) {
            targetKey = groupBy;
        }
        requireField(sourceHeaders, groupBy);
        requireField(targetHeaders, targetKey);
        requireField(sourceHeaders, requireParam(params, "sum"));
        requireField(targetHeaders, requireParam(params, "targetField"));
    }

    private static void validateDuplicateCheck(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        List<String> headers = requireTable(headersByTable, requireParam(params, "tableName"));
        List<String> fields = fields(params.get("groupBy"));
        if (fields.isEmpty()) {
            throw new BadRequestException("模板参数 groupBy 不能为空");
        }
        for (String field : fields) {
            requireField(headers, field);
        }
    }

    private static Map<String, List<String>> headersByTable(List<DataTableSnapshotEntity> tables,
                                                           JsonService jsonService) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (DataTableSnapshotEntity table : tables) {
            result.put(table.getLogicalName(), jsonService.readStringList(table.getHeadersJson()));
        }
        return result;
    }

    private static List<String> requireTable(Map<String, List<String>> headersByTable, String tableName) {
        List<String> headers = headersByTable.get(tableName);
        if (headers == null) {
            throw new BadRequestException("逻辑表不存在: " + tableName);
        }
        return headers;
    }

    private static void requireField(List<String> headers, String field) {
        if (isBlank(field)) {
            throw new BadRequestException("模板字段不能为空");
        }
        if (!headers.contains(field)) {
            throw new BadRequestException("字段不存在: " + field);
        }
    }

    private static String requireParam(Map<String, Object> params, String name) {
        String value = asString(params.get(name));
        if (isBlank(value)) {
            throw new BadRequestException("模板参数 " + name + " 不能为空");
        }
        return value;
    }

    private static List<String> fields(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                result.add(asString(item));
            }
            return result;
        }
        String single = asString(value);
        if (!isBlank(single)) {
            result.add(single);
        }
        return result;
    }

    private static boolean isExpressionOperator(String token) {
        return "==".equals(token) || "=".equals(token) || "!=".equals(token)
                || ">=".equals(token) || "<=".equals(token) || ">".equals(token)
                || "<".equals(token) || "+".equals(token) || "-".equals(token)
                || "*".equals(token) || "/".equals(token);
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
