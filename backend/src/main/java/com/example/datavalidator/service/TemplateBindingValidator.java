package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.DataTableSnapshotEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class TemplateBindingValidator {
    private TemplateBindingValidator() {
    }

    static void validate(String templateCode, Map<String, Object> params,
                         List<DataTableSnapshotEntity> tables, JsonService jsonService) {
        validate(templateCode, params, headersByTable(tables, jsonService));
    }

    static void validate(String templateCode, Map<String, Object> params,
                         Map<String, List<String>> headersByTable) {
        switch (templateCode) {
            case "NOT_NULL":
            case "NON_NEGATIVE":
            case "NUMERIC_TYPE":
                validateFieldList(params, headersByTable);
                break;
            case "FIELD_EXPRESSION":
                validateFieldExpression(params, headersByTable);
                break;
            case "ROW_EXPRESSION":
                validateRowExpression(params, headersByTable);
                break;
            case "EXISTS_IN_TABLE":
                validateExistsInTable(params, headersByTable);
                break;
            case "RELATION_EXISTS":
                validateRelationExists(params, headersByTable);
                break;
            case "FIELD_EQUALS":
                validateFieldEquals(params, headersByTable);
                break;
            case "JOIN_ASSERT":
                validateJoinAssert(params, headersByTable);
                break;
            case "AGGREGATION_EQUALS":
                validateAggregationEquals(params, headersByTable);
                break;
            case "AGGREGATE_ASSERT":
                validateAggregateAssert(params, headersByTable);
                break;
            case "DUPLICATE_ASSERT":
                validateDuplicateAssert(params, headersByTable);
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
        if (params.containsKey("where")) {
            RowExpressionEvaluator.validatePredicate(params.get("where"), headers);
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
        if (!TemplateExpressionEvaluator.isValidExpression(expression, headers)) {
            throw new BadRequestException("表达式格式不支持: " + expression);
        }
    }

    private static void validateRowExpression(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        String tableName = requireParam(params, "tableName");
        List<String> headers = requireTable(headersByTable, tableName);
        RowExpressionEvaluator.validate(params.get("conditions"), headers);
    }

    private static void validateExistsInTable(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        List<String> sourceHeaders = requireTable(headersByTable, requireParam(params, "source"));
        List<String> targetHeaders = requireTable(headersByTable, requireParam(params, "target"));
        String key = requireParam(params, "key");
        requireField(sourceHeaders, key);
        requireField(targetHeaders, key);
    }

    private static void validateRelationExists(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        String source = requireParam(params, "source");
        String target = requireParam(params, "target");
        List<String> sourceHeaders = requireTable(headersByTable, source);
        List<String> targetHeaders = requireTable(headersByTable, target);
        requireRelationKeys(params, sourceHeaders, targetHeaders);
        if (params.containsKey("sourceWhere")) {
            RowExpressionEvaluator.validatePredicate(params.get("sourceWhere"), sourceHeaders);
        }
        if (params.containsKey("targetWhere")) {
            RowExpressionEvaluator.validatePredicate(params.get("targetWhere"), targetHeaders);
        }
        validateSourceExists(params.get("sourceExists"), headersByTable, sourceHeaders);
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

    @SuppressWarnings("unchecked")
    private static void validateJoinAssert(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        String source = requireParam(params, "source");
        String target = requireParam(params, "target");
        List<String> sourceHeaders = requireTable(headersByTable, source);
        List<String> targetHeaders = requireTable(headersByTable, target);
        requireJoinKeys(params, sourceHeaders, targetHeaders);
        if (!(params.get("assert") instanceof Map)) {
            throw new BadRequestException("模板参数 assert 格式不支持");
        }
        Map<String, Object> assertion = (Map<String, Object>) params.get("assert");
        String operator = asString(assertion.get("op"));
        if (isBlank(operator)) {
            operator = asString(assertion.get("operator"));
        }
        if (!isJoinOperator(operator)) {
            throw new BadRequestException("关联断言操作符不支持: " + operator);
        }
        validateTolerance(assertion.get("tolerance"));
        validateJoinNode(assertion.get("left"), sourceHeaders, targetHeaders);
        validateJoinNode(assertion.get("right"), sourceHeaders, targetHeaders);
        if (params.containsKey("sourceWhere")) {
            RowExpressionEvaluator.validatePredicate(params.get("sourceWhere"), sourceHeaders);
        }
        if (params.containsKey("targetWhere")) {
            RowExpressionEvaluator.validatePredicate(params.get("targetWhere"), targetHeaders);
        }
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

    @SuppressWarnings("unchecked")
    private static void validateAggregateAssert(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        List<String> sourceHeaders = requireTable(headersByTable, requireParam(params, "source"));
        List<String> targetHeaders = requireTable(headersByTable, requireParam(params, "target"));
        requireAggregateGroupBy(params, sourceHeaders, targetHeaders);
        validateAggregateSpec(params.get("aggregate"), sourceHeaders, "aggregate");
        if (!(params.get("assert") instanceof Map)) {
            throw new BadRequestException("模板参数 assert 格式不支持");
        }
        Map<String, Object> assertion = (Map<String, Object>) params.get("assert");
        String operator = asString(assertion.get("op"));
        if (!isBlank(operator) && !isAggregateOperator(operator)) {
            throw new BadRequestException("聚合断言操作符不支持: " + operator);
        }
        validateTolerance(assertion.get("tolerance"));
        String targetField = asString(assertion.get("targetField"));
        Object targetAggregate = assertion.get("aggregate");
        if (targetAggregate == null) {
            targetAggregate = params.get("targetAggregate");
        }
        if (isBlank(targetField) && targetAggregate == null) {
            throw new BadRequestException("模板参数 assert.targetField 或 assert.aggregate 必须提供一个");
        }
        if (!isBlank(targetField)) {
            requireField(targetHeaders, targetField);
        }
        if (targetAggregate != null) {
            validateAggregateSpec(targetAggregate, targetHeaders, "assert.aggregate");
        }
        if (params.containsKey("sourceWhere")) {
            RowExpressionEvaluator.validatePredicate(params.get("sourceWhere"), sourceHeaders);
        }
        if (params.containsKey("targetWhere")) {
            RowExpressionEvaluator.validatePredicate(params.get("targetWhere"), targetHeaders);
        }
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
        if (params.containsKey("where")) {
            RowExpressionEvaluator.validatePredicate(params.get("where"), headers);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateDuplicateAssert(Map<String, Object> params, Map<String, List<String>> headersByTable) {
        List<String> headers = requireTable(headersByTable, requireTableParam(params));
        List<String> fields = fields(params.get("groupBy"));
        if (fields.isEmpty()) {
            throw new BadRequestException("模板参数 groupBy 不能为空");
        }
        for (String field : fields) {
            requireField(headers, field);
        }
        if (!(params.get("assert") instanceof Map)) {
            throw new BadRequestException("模板参数 assert 格式不支持");
        }
        validateDuplicateAssertion((Map<String, Object>) params.get("assert"));
        if (params.containsKey("where")) {
            RowExpressionEvaluator.validatePredicate(params.get("where"), headers);
        }
    }

    private static String requireTableParam(Map<String, Object> params) {
        String tableName = asString(params.get("table"));
        if (isBlank(tableName)) {
            tableName = asString(params.get("tableName"));
        }
        if (isBlank(tableName)) {
            throw new BadRequestException("模板参数 table 不能为空");
        }
        return tableName;
    }

    private static void validateDuplicateAssertion(Map<String, Object> assertion) {
        String operator = asString(assertion.get("op"));
        if (isBlank(operator)) {
            operator = asString(assertion.get("operator"));
        }
        Object rawCount = assertion.containsKey("count") ? assertion.get("count") : assertion.get("value");
        String countText = asString(rawCount).trim();
        for (String candidate : Arrays.asList(">=", "<=", "==", "!=", ">", "<", "=")) {
            if (countText.startsWith(candidate)) {
                operator = candidate;
                countText = countText.substring(candidate.length()).trim();
                break;
            }
        }
        if (isBlank(operator)) {
            operator = "<=";
        }
        if (!isAggregateOperator(operator)) {
            throw new BadRequestException("分组次数断言操作符不支持: " + operator);
        }
        if (!ValueParsers.decimal(countText).isPresent()) {
            throw new BadRequestException("模板参数 assert.count 必须是数值");
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireAggregateGroupBy(Map<String, Object> params, List<String> sourceHeaders,
                                                List<String> targetHeaders) {
        int matchedKeys = 0;
        Object rawGroupBy = params.get("groupBy");
        if (rawGroupBy instanceof List) {
            for (Object rawKey : (List<?>) rawGroupBy) {
                if (rawKey instanceof Map) {
                    Map<String, Object> key = (Map<String, Object>) rawKey;
                    requireField(sourceHeaders, requireParam(key, "sourceField"));
                    requireField(targetHeaders, requireParam(key, "targetField"));
                    matchedKeys++;
                } else if (!isBlank(asString(rawKey))) {
                    requireField(sourceHeaders, asString(rawKey));
                    requireField(targetHeaders, asString(rawKey));
                    matchedKeys++;
                }
            }
        }
        if (matchedKeys == 0) {
            String groupBy = requireParam(params, "groupBy");
            String targetKey = asString(params.get("targetKey"));
            if (isBlank(targetKey)) {
                targetKey = groupBy;
            }
            requireField(sourceHeaders, groupBy);
            requireField(targetHeaders, targetKey);
        }
    }

    private static void requireJoinKeys(Map<String, Object> params, List<String> sourceHeaders,
                                        List<String> targetHeaders) {
        if (params.containsKey("keys") || params.containsKey("key")) {
            requireRelationKeys(params, sourceHeaders, targetHeaders);
            return;
        }
        Map<String, Object> joinParams = new LinkedHashMap<>();
        joinParams.put("keys", params.get("join"));
        requireRelationKeys(joinParams, sourceHeaders, targetHeaders);
    }

    @SuppressWarnings("unchecked")
    private static void validateJoinNode(Object rawNode, List<String> sourceHeaders, List<String> targetHeaders) {
        if (rawNode instanceof Number || rawNode instanceof String) {
            return;
        }
        if (!(rawNode instanceof Map)) {
            throw new BadRequestException("关联断言表达式节点格式不支持");
        }
        Map<String, Object> node = (Map<String, Object>) rawNode;
        String sourceField = asString(node.get("sourceField"));
        if (!isBlank(sourceField)) {
            requireField(sourceHeaders, sourceField);
            return;
        }
        String targetField = asString(node.get("targetField"));
        if (!isBlank(targetField)) {
            requireField(targetHeaders, targetField);
            return;
        }
        if (node.containsKey("literal") || node.containsKey("value")) {
            return;
        }
        String operator = asString(node.get("op"));
        if (!isArithmeticOperator(operator)) {
            throw new BadRequestException("不支持的关联断言算术操作符: " + operator);
        }
        validateJoinNode(node.get("left"), sourceHeaders, targetHeaders);
        validateJoinNode(node.get("right"), sourceHeaders, targetHeaders);
    }

    @SuppressWarnings("unchecked")
    private static void validateAggregateSpec(Object rawAggregate, List<String> headers, String paramName) {
        if (!(rawAggregate instanceof Map)) {
            throw new BadRequestException("模板参数 " + paramName + " 格式不支持");
        }
        Map<String, Object> aggregate = (Map<String, Object>) rawAggregate;
        String fn = asString(aggregate.get("fn"));
        if (isBlank(fn)) {
            fn = "SUM";
        }
        String normalized = fn.toUpperCase(Locale.ROOT);
        if (!"SUM".equals(normalized) && !"COUNT".equals(normalized)) {
            throw new BadRequestException("聚合函数不支持: " + fn);
        }
        if (!"COUNT".equals(normalized)) {
            requireField(headers, requireParam(aggregate, "field"));
        }
    }

    private static void validateTolerance(Object value) {
        if (value != null && !ValueParsers.decimal(asString(value)).isPresent()) {
            throw new BadRequestException("模板参数 tolerance 必须是数值");
        }
    }

    private static boolean isAggregateOperator(String operator) {
        return "==".equals(operator) || "=".equals(operator) || "!=".equals(operator)
                || ">".equals(operator) || ">=".equals(operator)
                || "<".equals(operator) || "<=".equals(operator);
    }

    private static boolean isJoinOperator(String operator) {
        return isAggregateOperator(operator);
    }

    private static boolean isArithmeticOperator(String operator) {
        return "+".equals(operator) || "-".equals(operator) || "*".equals(operator) || "/".equals(operator);
    }

    @SuppressWarnings("unchecked")
    private static void validateSourceExists(Object rawSourceExists, Map<String, List<String>> headersByTable,
                                             List<String> sourceHeaders) {
        if (rawSourceExists == null) {
            return;
        }
        if (!(rawSourceExists instanceof Map)) {
            throw new BadRequestException("模板参数 sourceExists 格式不支持");
        }
        Map<String, Object> sourceExists = (Map<String, Object>) rawSourceExists;
        List<String> targetHeaders = requireTable(headersByTable, requireParam(sourceExists, "target"));
        requireRelationKeys(sourceExists, sourceHeaders, targetHeaders);
        if (sourceExists.containsKey("targetWhere")) {
            RowExpressionEvaluator.validatePredicate(sourceExists.get("targetWhere"), targetHeaders);
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireRelationKeys(Map<String, Object> params, List<String> sourceHeaders,
                                            List<String> targetHeaders) {
        int matchedKeys = 0;
        Object rawKeys = params.get("keys");
        if (rawKeys instanceof List) {
            for (Object rawKey : (List<?>) rawKeys) {
                if (!(rawKey instanceof Map)) {
                    String field = asString(rawKey);
                    if (isBlank(field)) {
                        throw new BadRequestException("模板参数 keys 格式不支持");
                    }
                    requireField(sourceHeaders, field);
                    requireField(targetHeaders, field);
                    matchedKeys++;
                    continue;
                }
                Map<String, Object> key = (Map<String, Object>) rawKey;
                String sourceField = requireParam(key, "sourceField");
                String targetField = requireParam(key, "targetField");
                requireField(sourceHeaders, sourceField);
                requireField(targetHeaders, targetField);
                matchedKeys++;
            }
        }
        if (matchedKeys == 0) {
            String key = asString(params.get("key"));
            if (!isBlank(key)) {
                String targetKey = asString(params.get("targetKey"));
                if (isBlank(targetKey)) {
                    targetKey = key;
                }
                requireField(sourceHeaders, key);
                requireField(targetHeaders, targetKey);
                matchedKeys++;
            }
        }
        if (matchedKeys == 0) {
            throw new BadRequestException("模板参数 keys 不能为空");
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
                || "*".equals(token) || "/".equals(token) || "&&".equals(token);
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
