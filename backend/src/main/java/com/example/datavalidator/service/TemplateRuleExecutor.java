package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.RuleBinding;
import com.example.datavalidator.domain.RuleDefinition;
import com.example.datavalidator.domain.ValidationFinding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.example.datavalidator.service.TemplateFindingFactory.finding;

@Service
public class TemplateRuleExecutor {
    public List<ValidationFinding> execute(RuleDefinition rule, Map<String, DataTable> tables, RuleBinding binding) {
        if (binding == null || !binding.isEnabled() || !"TEMPLATE".equals(binding.getExecutorType())) {
            return Collections.emptyList();
        }
        Map<String, Object> params = binding.getTemplateParams();
        switch (binding.getTemplateCode()) {
            case "NOT_NULL":
                return notNull(rule, table(tables, params), fields(params.get("fields")));
            case "NON_NEGATIVE":
                return nonNegative(rule, table(tables, params), fields(params.get("fields")));
            case "NUMERIC_TYPE":
                return numericType(rule, table(tables, params), fields(params.get("fields")),
                        allowBlank(params));
            case "FIELD_EXPRESSION":
                return fieldExpression(rule, table(tables, params), asString(params.get("expression")));
            case "ROW_EXPRESSION":
                return rowExpression(rule, table(tables, params), params.get("conditions"));
            case "EXISTS_IN_TABLE":
                return existsInTable(rule, tables, params);
            case "RELATION_EXISTS":
                return relationExists(rule, tables, params);
            case "FIELD_EQUALS":
                return fieldEquals(rule, tables, params);
            case "JOIN_ASSERT":
                return joinAssert(rule, tables, params);
            case "AGGREGATION_EQUALS":
                return aggregationEquals(rule, tables, params);
            case "AGGREGATE_ASSERT":
                return aggregateAssert(rule, tables, params);
            case "DUPLICATE_ASSERT":
                return duplicateAssert(rule, table(tables, params), fields(params.get("groupBy")),
                        params.get("where"), params.get("assert"));
            case "DUPLICATE_CHECK":
                return duplicateCheck(rule, table(tables, params), fields(params.get("groupBy")), params.get("where"));
            default:
                return Collections.emptyList();
        }
    }

    private List<ValidationFinding> notNull(RuleDefinition rule, DataTable table, List<String> fields) {
        if (table == null) {
            return Collections.emptyList();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow row : table.getRows()) {
            for (String field : fields) {
                if (ValueParsers.isBlank(row.value(field))) {
                    findings.add(finding(rule, table, row, field, row.value(field), "非空",
                            field + "不能为空", "FIELD_VALUE"));
                }
            }
        }
        return findings;
    }

    private List<ValidationFinding> nonNegative(RuleDefinition rule, DataTable table, List<String> fields) {
        if (table == null) {
            return Collections.emptyList();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow row : table.getRows()) {
            for (String field : fields) {
                Optional<BigDecimal> value = ValueParsers.decimal(row.value(field));
                if (value.isPresent() && value.get().compareTo(BigDecimal.ZERO) < 0) {
                    findings.add(finding(rule, table, row, field, row.value(field), ">= 0",
                            field + "不得为负数", "FIELD_VALUE"));
                }
            }
        }
        return findings;
    }

    private List<ValidationFinding> numericType(RuleDefinition rule, DataTable table, List<String> fields,
                                                boolean allowBlank) {
        if (table == null) {
            return Collections.emptyList();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow row : table.getRows()) {
            for (String field : fields) {
                String value = row.value(field);
                if ((ValueParsers.isBlank(value) && !allowBlank)
                        || (!ValueParsers.isBlank(value) && !ValueParsers.decimal(value).isPresent())) {
                    findings.add(finding(rule, table, row, field, value, "数值类型",
                            field + "必须为数值", "FIELD_VALUE"));
                }
            }
        }
        return findings;
    }

    private List<ValidationFinding> fieldExpression(RuleDefinition rule, DataTable table, String expression) {
        if (table == null || ValueParsers.isBlank(expression)) {
            return Collections.emptyList();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow row : table.getRows()) {
            Optional<TemplateExpressionEvaluator.Result> result =
                    TemplateExpressionEvaluator.evaluate(expression, row);
            if (result.isPresent() && !result.get().isSatisfied()) {
                findings.add(finding(rule, table, row, result.get().getLeftField(),
                        result.get().getActualSummary(), result.get().getExpectedSummary(),
                        expression + " 不成立", "CALCULATION"));
            }
        }
        return findings;
    }

    private List<ValidationFinding> rowExpression(RuleDefinition rule, DataTable table, Object conditions) {
        if (table == null) {
            return Collections.emptyList();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow row : table.getRows()) {
            Optional<RowExpressionEvaluator.Result> result = RowExpressionEvaluator.evaluate(conditions, row);
            if (result.isPresent()) {
                RowExpressionEvaluator.Result failed = result.get();
                findings.add(finding(rule, table, row, failed.getFieldName(),
                        failed.getActualSummary(), failed.getExpectedSummary(),
                        "行表达式条件不成立", "CALCULATION"));
            }
        }
        return findings;
    }

    private List<ValidationFinding> existsInTable(RuleDefinition rule, Map<String, DataTable> tables,
                                                  Map<String, Object> params) {
        DataTable source = tables.get(asString(params.get("source")));
        DataTable target = tables.get(asString(params.get("target")));
        String key = asString(params.get("key"));
        if (source == null || target == null || ValueParsers.isBlank(key)) {
            return Collections.emptyList();
        }
        List<String> targetKeys = target.getRows().stream()
                .map(row -> row.value(key))
                .filter(value -> !ValueParsers.isBlank(value))
                .collect(Collectors.toList());
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow row : source.getRows()) {
            String actual = row.value(key);
            if (!ValueParsers.isBlank(actual) && !targetKeys.contains(actual)) {
                findings.add(finding(rule, source, row, key, source.getLogicalName() + "." + key + "=" + actual,
                        target.getLogicalName() + "." + key + " 中存在对应记录",
                        "关联记录不存在", "RELATION"));
            }
        }
        return findings;
    }

    private List<ValidationFinding> relationExists(RuleDefinition rule, Map<String, DataTable> tables,
                                                   Map<String, Object> params) {
        DataTable source = tables.get(asString(params.get("source")));
        DataTable target = tables.get(asString(params.get("target")));
        List<RelationKey> keys = relationKeys(params.get("keys"), asString(params.get("key")),
                asString(params.get("targetKey")));
        boolean expectExists = !Boolean.FALSE.equals(params.get("expectExists"));
        if (source == null || target == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow row : source.getRows()) {
            if (!matchesWhere(params.get("sourceWhere"), row)
                    || !matchesSourceExists(params.get("sourceExists"), row, tables)) {
                continue;
            }
            Optional<DataRow> matched = firstMatchedTarget(target, keys, row, params.get("targetWhere"));
            if (expectExists && !matched.isPresent()) {
                findings.add(finding(rule, source, row, keys.get(0).sourceField,
                        source.getLogicalName() + "." + keySummary(row, keys, true),
                        target.getLogicalName() + " 中存在匹配记录",
                        "关联记录不存在", "RELATION"));
            }
            if (!expectExists && matched.isPresent()) {
                findings.add(finding(rule, source, row, keys.get(0).sourceField,
                        source.getLogicalName() + "." + keySummary(row, keys, true)
                                + "；" + target.getLogicalName() + "." + keySummary(matched.get(), keys, false),
                        target.getLogicalName() + " 中不应存在匹配记录",
                        "不应存在关联记录", "RELATION"));
            }
        }
        return findings;
    }

    @SuppressWarnings("unchecked")
    private boolean matchesSourceExists(Object rawSourceExists, DataRow sourceRow, Map<String, DataTable> tables) {
        if (!(rawSourceExists instanceof Map)) {
            return true;
        }
        Map<String, Object> sourceExists = (Map<String, Object>) rawSourceExists;
        DataTable target = tables.get(asString(sourceExists.get("target")));
        List<RelationKey> keys = relationKeys(sourceExists.get("keys"), asString(sourceExists.get("key")),
                asString(sourceExists.get("targetKey")));
        return target != null && firstMatchedTarget(target, keys, sourceRow, sourceExists.get("targetWhere")).isPresent();
    }

    private Optional<DataRow> firstMatchedTarget(DataTable target, List<RelationKey> keys,
                                                DataRow sourceRow, Object targetWhere) {
        for (DataRow targetRow : target.getRows()) {
            if (keysMatch(sourceRow, targetRow, keys) && matchesWhere(targetWhere, targetRow)) {
                return Optional.of(targetRow);
            }
        }
        return Optional.empty();
    }

    private boolean keysMatch(DataRow sourceRow, DataRow targetRow, List<RelationKey> keys) {
        for (RelationKey key : keys) {
            String sourceValue = sourceRow.value(key.sourceField);
            if (ValueParsers.isBlank(sourceValue) || !sourceValue.equals(targetRow.value(key.targetField))) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesWhere(Object where, DataRow row) {
        return !(where instanceof Map) || RowExpressionEvaluator.matches(where, row);
    }

    private boolean allowBlank(Map<String, Object> params) {
        if (params == null || !params.containsKey("allowBlank")) {
            return true;
        }
        Object value = params.get("allowBlank");
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(asString(value));
    }

    private List<ValidationFinding> fieldEquals(RuleDefinition rule, Map<String, DataTable> tables,
                                                Map<String, Object> params) {
        DataTable source = tables.get(asString(params.get("source")));
        DataTable target = tables.get(asString(params.get("target")));
        String key = asString(params.get("key"));
        String sourceField = asString(params.get("sourceField"));
        String targetField = asString(params.get("targetField"));
        if (source == null || target == null || ValueParsers.isBlank(key)
                || ValueParsers.isBlank(sourceField) || ValueParsers.isBlank(targetField)) {
            return Collections.emptyList();
        }
        Map<String, DataRow> targetRows = indexBy(target, key);
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow row : source.getRows()) {
            DataRow targetRow = targetRows.get(row.value(key));
            String actual = row.value(sourceField);
            if (targetRow == null) {
                findings.add(finding(rule, source, row, key, row.value(key),
                        target.getLogicalName() + "." + key,
                        key + " 未找到关联记录", "RELATION"));
                continue;
            }
            String expected = targetRow.value(targetField);
            if (!actual.equals(expected)) {
                findings.add(finding(rule, source, row, sourceField,
                        source.getLogicalName() + "." + sourceField + "=" + actual,
                        target.getLogicalName() + "." + targetField + "=" + expected,
                        "关联字段值不一致", "RELATION"));
            }
        }
        return findings;
    }

    private List<ValidationFinding> joinAssert(RuleDefinition rule, Map<String, DataTable> tables,
                                               Map<String, Object> params) {
        DataTable source = tables.get(asString(params.get("source")));
        DataTable target = tables.get(asString(params.get("target")));
        List<RelationKey> keys = joinKeys(params);
        JoinAssertion assertion = joinAssertion(params.get("assert"));
        if (source == null || target == null || keys.isEmpty() || assertion == null) {
            return Collections.emptyList();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow sourceRow : source.getRows()) {
            if (!matchesWhere(params.get("sourceWhere"), sourceRow)
                    || !matchesSourceExists(params.get("sourceExists"), sourceRow, tables)) {
                continue;
            }
            List<DataRow> matchedRows = matchedTargets(target, keys, sourceRow, params.get("targetWhere"));
            if (matchedRows.isEmpty()) {
                findings.add(finding(rule, source, sourceRow, keys.get(0).sourceField,
                        source.getLogicalName() + "." + keySummary(sourceRow, keys, true),
                        target.getLogicalName() + " 中存在匹配记录",
                        "关联记录不存在", "RELATION"));
                continue;
            }
            for (DataRow targetRow : matchedRows) {
                JoinValue actual = joinValue(assertion.left, source, sourceRow, target, targetRow);
                JoinValue expected = joinValue(assertion.right, source, sourceRow, target, targetRow);
                if (!joinCompare(actual, expected, assertion)) {
                    findings.add(finding(rule, source, sourceRow, actual.fieldName,
                            actual.summary,
                            expected.summary,
                            "关联断言不成立", "RELATION"));
                }
            }
        }
        return findings;
    }

    private List<DataRow> matchedTargets(DataTable target, List<RelationKey> keys,
                                         DataRow sourceRow, Object targetWhere) {
        List<DataRow> result = new ArrayList<>();
        for (DataRow targetRow : target.getRows()) {
            if (keysMatch(sourceRow, targetRow, keys) && matchesWhere(targetWhere, targetRow)) {
                result.add(targetRow);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private JoinAssertion joinAssertion(Object rawAssert) {
        if (!(rawAssert instanceof Map)) {
            return null;
        }
        Map<String, Object> assertion = (Map<String, Object>) rawAssert;
        String operator = asString(assertion.get("op"));
        if (ValueParsers.isBlank(operator)) {
            operator = asString(assertion.get("operator"));
        }
        if (!isSupportedJoinOperator(operator)) {
            return null;
        }
        return new JoinAssertion(assertion.get("left"), operator, assertion.get("right"),
                decimal(assertion.get("tolerance")));
    }

    @SuppressWarnings("unchecked")
    private JoinValue joinValue(Object rawNode, DataTable source, DataRow sourceRow,
                                DataTable target, DataRow targetRow) {
        if (rawNode instanceof Number || rawNode instanceof String) {
            String value = asString(rawNode);
            return new JoinValue(value, value, value, ValueParsers.decimal(value), "");
        }
        if (!(rawNode instanceof Map)) {
            return JoinValue.unavailable();
        }
        Map<String, Object> node = (Map<String, Object>) rawNode;
        String sourceField = asString(node.get("sourceField"));
        if (!ValueParsers.isBlank(sourceField)) {
            String value = sourceRow.value(sourceField);
            return new JoinValue(source.getLogicalName() + "." + sourceField,
                    source.getLogicalName() + "." + sourceField + "=" + value,
                    value, ValueParsers.decimal(value), sourceField);
        }
        String targetField = asString(node.get("targetField"));
        if (!ValueParsers.isBlank(targetField)) {
            String value = targetRow.value(targetField);
            return new JoinValue(target.getLogicalName() + "." + targetField,
                    target.getLogicalName() + "." + targetField + "=" + value,
                    value, ValueParsers.decimal(value), targetField);
        }
        if (node.containsKey("literal") || node.containsKey("value")) {
            Object literal = node.containsKey("literal") ? node.get("literal") : node.get("value");
            String value = asString(literal);
            return new JoinValue(value, value, value, ValueParsers.decimal(value), "");
        }
        JoinValue left = joinValue(node.get("left"), source, sourceRow, target, targetRow);
        JoinValue right = joinValue(node.get("right"), source, sourceRow, target, targetRow);
        String operator = asString(node.get("op"));
        Optional<BigDecimal> calculated = calculateJoinValue(left, operator, right);
        String text = left.text + " " + operator + " " + right.text;
        String value = calculated.map(this::formatDecimal).orElse("无法计算");
        return new JoinValue(text, text + "=" + value, value, calculated, left.fieldName);
    }

    private Optional<BigDecimal> calculateJoinValue(JoinValue left, String operator, JoinValue right) {
        if (!left.decimal.isPresent() || !right.decimal.isPresent()) {
            return Optional.empty();
        }
        BigDecimal leftValue = left.decimal.get();
        BigDecimal rightValue = right.decimal.get();
        switch (operator) {
            case "+": return Optional.of(leftValue.add(rightValue));
            case "-": return Optional.of(leftValue.subtract(rightValue));
            case "*": return Optional.of(leftValue.multiply(rightValue));
            case "/":
                if (rightValue.compareTo(BigDecimal.ZERO) == 0) {
                    return Optional.empty();
                }
                return Optional.of(leftValue.divide(rightValue, 10, java.math.RoundingMode.HALF_UP)
                        .stripTrailingZeros());
            default: return Optional.empty();
        }
    }

    private boolean joinCompare(JoinValue actual, JoinValue expected, JoinAssertion assertion) {
        if (actual.decimal.isPresent() && expected.decimal.isPresent()) {
            int compared = actual.decimal.get().compareTo(expected.decimal.get());
            BigDecimal diff = actual.decimal.get().subtract(expected.decimal.get()).abs();
            switch (assertion.operator) {
                case "==":
                case "=":
                    return diff.compareTo(assertion.tolerance) <= 0;
                case "!=":
                    return diff.compareTo(assertion.tolerance) > 0;
                case ">": return compared > 0;
                case ">=": return compared >= 0;
                case "<": return compared < 0;
                case "<=": return compared <= 0;
                default: return false;
            }
        }
        int compared = actual.rawValue.compareTo(expected.rawValue);
        switch (assertion.operator) {
            case "==":
            case "=":
                return actual.rawValue.equals(expected.rawValue);
            case "!=":
                return !actual.rawValue.equals(expected.rawValue);
            case ">": return compared > 0;
            case ">=": return compared >= 0;
            case "<": return compared < 0;
            case "<=": return compared <= 0;
            default: return false;
        }
    }

    private List<ValidationFinding> aggregationEquals(RuleDefinition rule, Map<String, DataTable> tables,
                                                      Map<String, Object> params) {
        DataTable source = tables.get(asString(params.get("source")));
        DataTable target = tables.get(asString(params.get("target")));
        String groupBy = asString(params.get("groupBy"));
        String sumField = asString(params.get("sum"));
        String targetField = asString(params.get("targetField"));
        String targetKey = asString(params.get("targetKey"));
        if (ValueParsers.isBlank(targetKey)) {
            targetKey = groupBy;
        }
        if (source == null || target == null || ValueParsers.isBlank(groupBy)
                || ValueParsers.isBlank(sumField) || ValueParsers.isBlank(targetField)) {
            return Collections.emptyList();
        }
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, DataRow> sourceRowsByGroup = new HashMap<>();
        for (DataRow row : source.getRows()) {
            Optional<BigDecimal> value = ValueParsers.decimal(row.value(sumField));
            if (value.isPresent()) {
                String groupValue = row.value(groupBy);
                totals.merge(groupValue, value.get(), BigDecimal::add);
                sourceRowsByGroup.putIfAbsent(groupValue, row);
            }
        }
        List<ValidationFinding> findings = new ArrayList<>();
        List<String> matchedGroups = new ArrayList<>();
        for (DataRow row : target.getRows()) {
            String groupValue = row.value(targetKey);
            BigDecimal expected = totals.get(groupValue);
            Optional<BigDecimal> actual = ValueParsers.decimal(row.value(targetField));
            if (expected != null && (!actual.isPresent() || actual.get().compareTo(expected) != 0)) {
                findings.add(finding(rule, target, row, targetField,
                        targetField + "=" + row.value(targetField),
                        source.getLogicalName() + "." + sumField + " 汇总=" + formatDecimal(expected),
                        "聚合结果不一致",
                        "CALCULATION"));
            }
            if (expected != null) {
                matchedGroups.add(groupValue);
            }
        }
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            if (!matchedGroups.contains(entry.getKey())) {
                DataRow sourceRow = sourceRowsByGroup.get(entry.getKey());
                findings.add(finding(rule, source, sourceRow, groupBy,
                        source.getLogicalName() + "." + groupBy + "=" + entry.getKey()
                                + "；" + sumField + " 汇总=" + formatDecimal(entry.getValue()),
                        target.getLogicalName() + "." + targetKey + " 中存在聚合目标记录",
                        "聚合目标记录不存在", "RELATION"));
            }
        }
        return findings;
    }

    private List<ValidationFinding> aggregateAssert(RuleDefinition rule, Map<String, DataTable> tables,
                                                    Map<String, Object> params) {
        DataTable source = tables.get(asString(params.get("source")));
        DataTable target = tables.get(asString(params.get("target")));
        List<RelationKey> keys = aggregateGroupKeys(params);
        AggregateSpec sourceAggregate = aggregateSpec(params.get("aggregate"), asString(params.get("sum")));
        AggregateAssertion assertion = aggregateAssertion(params.get("assert"), params);
        if (source == null || target == null || keys.isEmpty()
                || sourceAggregate == null || assertion == null) {
            return Collections.emptyList();
        }
        if (!ValueParsers.isBlank(assertion.targetField)) {
            return aggregateAssertTargetField(rule, source, target, keys, sourceAggregate, assertion, params);
        }
        return aggregateAssertTargetAggregate(rule, source, target, keys, sourceAggregate, assertion, params);
    }

    private List<ValidationFinding> aggregateAssertTargetField(RuleDefinition rule, DataTable source,
                                                               DataTable target, List<RelationKey> keys,
                                                               AggregateSpec sourceAggregate,
                                                               AggregateAssertion assertion,
                                                               Map<String, Object> params) {
        Map<String, AggregateBucket> sourceBuckets =
                aggregateBuckets(source, keys, true, sourceAggregate, params.get("sourceWhere"));
        List<ValidationFinding> findings = new ArrayList<>();
        List<String> matchedGroups = new ArrayList<>();
        for (DataRow row : target.getRows()) {
            if (!matchesWhere(params.get("targetWhere"), row)) {
                continue;
            }
            String key = groupKey(row, targetFields(keys));
            AggregateBucket expected = sourceBuckets.get(key);
            Optional<BigDecimal> actual = ValueParsers.decimal(row.value(assertion.targetField));
            if (expected != null && (!actual.isPresent()
                    || !compare(actual.get(), expected.value, assertion))) {
                findings.add(targetFieldAggregateFinding(rule, source, target, row, sourceAggregate, assertion,
                        expected.value));
            }
            if (expected != null) {
                matchedGroups.add(key);
            }
        }
        addMissingAggregateTargets(rule, source, target, keys, sourceAggregate, sourceBuckets, matchedGroups, findings);
        return findings;
    }

    private List<ValidationFinding> aggregateAssertTargetAggregate(RuleDefinition rule, DataTable source,
                                                                   DataTable target, List<RelationKey> keys,
                                                                   AggregateSpec sourceAggregate,
                                                                   AggregateAssertion assertion,
                                                                   Map<String, Object> params) {
        Map<String, AggregateBucket> sourceBuckets =
                aggregateBuckets(source, keys, true, sourceAggregate, params.get("sourceWhere"));
        Map<String, AggregateBucket> targetBuckets =
                aggregateBuckets(target, keys, false, assertion.targetAggregate, params.get("targetWhere"));
        List<ValidationFinding> findings = new ArrayList<>();
        for (Map.Entry<String, AggregateBucket> entry : sourceBuckets.entrySet()) {
            AggregateBucket targetBucket = targetBuckets.get(entry.getKey());
            if (targetBucket == null) {
                findings.add(missingTargetAggregateFinding(rule, source, target, keys, sourceAggregate, entry.getValue()));
            } else if (!compare(targetBucket.value, entry.getValue().value, assertion)) {
                findings.add(targetAggregateFinding(rule, source, target, targetBucket, sourceAggregate, assertion,
                        entry.getValue().value));
            }
        }
        for (Map.Entry<String, AggregateBucket> entry : targetBuckets.entrySet()) {
            if (!sourceBuckets.containsKey(entry.getKey())) {
                findings.add(missingSourceAggregateFinding(rule, source, target, keys, assertion, entry.getValue()));
            }
        }
        return findings;
    }

    private Map<String, AggregateBucket> aggregateBuckets(DataTable table, List<RelationKey> keys, boolean sourceSide,
                                                          AggregateSpec aggregate, Object where) {
        Map<String, AggregateBucket> result = new LinkedHashMap<>();
        List<String> groupFields = sourceSide ? sourceFields(keys) : targetFields(keys);
        for (DataRow row : table.getRows()) {
            if (!matchesWhere(where, row)) {
                continue;
            }
            Optional<BigDecimal> value = aggregateValue(row, aggregate);
            if (!value.isPresent()) {
                continue;
            }
            String key = groupKey(row, groupFields);
            result.computeIfAbsent(key, ignored -> new AggregateBucket(row)).add(value.get());
        }
        return result;
    }

    private Optional<BigDecimal> aggregateValue(DataRow row, AggregateSpec aggregate) {
        if ("COUNT".equals(aggregate.fn)) {
            return Optional.of(BigDecimal.ONE);
        }
        return ValueParsers.decimal(row.value(aggregate.field));
    }

    private List<RelationKey> aggregateGroupKeys(Map<String, Object> params) {
        return relationKeys(params.get("groupBy"), asString(params.get("groupBy")), asString(params.get("targetKey")));
    }

    private List<RelationKey> joinKeys(Map<String, Object> params) {
        List<RelationKey> keys = relationKeys(params.get("keys"), asString(params.get("key")),
                asString(params.get("targetKey")));
        if (keys.isEmpty()) {
            keys = relationKeys(params.get("join"), "", "");
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private AggregateSpec aggregateSpec(Object rawAggregate, String fallbackSumField) {
        if (rawAggregate instanceof Map) {
            Map<String, Object> aggregate = (Map<String, Object>) rawAggregate;
            String fn = aggregateFn(asString(aggregate.get("fn")));
            String field = asString(aggregate.get("field"));
            if (("COUNT".equals(fn) || !ValueParsers.isBlank(field)) && isSupportedAggregateFn(fn)) {
                return new AggregateSpec(fn, field);
            }
        }
        if (!ValueParsers.isBlank(fallbackSumField)) {
            return new AggregateSpec("SUM", fallbackSumField);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private AggregateAssertion aggregateAssertion(Object rawAssert, Map<String, Object> params) {
        Map<String, Object> assertion = rawAssert instanceof Map
                ? (Map<String, Object>) rawAssert : Collections.emptyMap();
        String operator = asString(assertion.get("op"));
        if (ValueParsers.isBlank(operator)) {
            operator = "==";
        }
        if (!isSupportedAggregateOperator(operator)) {
            return null;
        }
        String targetField = asString(assertion.get("targetField"));
        if (ValueParsers.isBlank(targetField)) {
            targetField = asString(params.get("targetField"));
        }
        AggregateSpec targetAggregate = aggregateSpec(assertion.get("aggregate"), "");
        if (targetAggregate == null) {
            targetAggregate = aggregateSpec(params.get("targetAggregate"), "");
        }
        if (ValueParsers.isBlank(targetField) && targetAggregate == null) {
            return null;
        }
        return new AggregateAssertion(operator, targetField, targetAggregate, decimal(assertion.get("tolerance")));
    }

    private ValidationFinding targetFieldAggregateFinding(RuleDefinition rule, DataTable source, DataTable target,
                                                          DataRow row, AggregateSpec sourceAggregate,
                                                          AggregateAssertion assertion, BigDecimal expected) {
        return finding(rule, target, row, assertion.targetField,
                assertion.targetField + "=" + row.value(assertion.targetField),
                aggregateLabel(source, sourceAggregate) + "=" + formatDecimal(expected),
                "聚合结果不一致", "CALCULATION");
    }

    private ValidationFinding targetAggregateFinding(RuleDefinition rule, DataTable source, DataTable target,
                                                     AggregateBucket targetBucket, AggregateSpec sourceAggregate,
                                                     AggregateAssertion assertion, BigDecimal expected) {
        return finding(rule, target, targetBucket.firstRow, assertion.targetAggregate.field,
                aggregateLabel(target, assertion.targetAggregate) + "=" + formatDecimal(targetBucket.value),
                aggregateLabel(source, sourceAggregate) + "=" + formatDecimal(expected),
                "聚合结果不一致", "CALCULATION");
    }

    private void addMissingAggregateTargets(RuleDefinition rule, DataTable source, DataTable target,
                                            List<RelationKey> keys, AggregateSpec sourceAggregate,
                                            Map<String, AggregateBucket> sourceBuckets, List<String> matchedGroups,
                                            List<ValidationFinding> findings) {
        for (Map.Entry<String, AggregateBucket> entry : sourceBuckets.entrySet()) {
            if (!matchedGroups.contains(entry.getKey())) {
                findings.add(missingTargetAggregateFinding(rule, source, target, keys, sourceAggregate, entry.getValue()));
            }
        }
    }

    private ValidationFinding missingTargetAggregateFinding(RuleDefinition rule, DataTable source, DataTable target,
                                                            List<RelationKey> keys, AggregateSpec sourceAggregate,
                                                            AggregateBucket sourceBucket) {
        return finding(rule, source, sourceBucket.firstRow, keys.get(0).sourceField,
                source.getLogicalName() + "." + keySummary(sourceBucket.firstRow, keys, true)
                        + "；" + sourceAggregate.field + " 汇总=" + formatDecimal(sourceBucket.value),
                target.getLogicalName() + "." + keys.get(0).targetField + " 中存在聚合目标记录",
                "聚合目标记录不存在", "RELATION");
    }

    private ValidationFinding missingSourceAggregateFinding(RuleDefinition rule, DataTable source, DataTable target,
                                                            List<RelationKey> keys, AggregateAssertion assertion,
                                                            AggregateBucket targetBucket) {
        return finding(rule, target, targetBucket.firstRow, keys.get(0).targetField,
                target.getLogicalName() + "." + keySummary(targetBucket.firstRow, keys, false)
                        + "；" + assertion.targetAggregate.field + " 汇总=" + formatDecimal(targetBucket.value),
                source.getLogicalName() + "." + keys.get(0).sourceField + " 中存在聚合来源记录",
                "聚合来源记录不存在", "RELATION");
    }

    private boolean compare(BigDecimal actual, BigDecimal expected, AggregateAssertion assertion) {
        BigDecimal diff = actual.subtract(expected).abs();
        switch (assertion.operator) {
            case "==":
            case "=":
                return diff.compareTo(assertion.tolerance) <= 0;
            case "!=":
                return diff.compareTo(assertion.tolerance) > 0;
            case ">":
                return actual.compareTo(expected) > 0;
            case ">=":
                return actual.compareTo(expected) >= 0;
            case "<":
                return actual.compareTo(expected) < 0;
            case "<=":
                return actual.compareTo(expected) <= 0;
            default:
                return false;
        }
    }

    private List<ValidationFinding> duplicateCheck(RuleDefinition rule, DataTable table, List<String> groupBy,
                                                   Object where) {
        if (table == null || groupBy.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<DataRow>> groups = new HashMap<>();
        for (DataRow row : table.getRows()) {
            if (where instanceof Map && !RowExpressionEvaluator.matches(where, row)) {
                continue;
            }
            groups.computeIfAbsent(groupKey(row, groupBy), ignored -> new ArrayList<>()).add(row);
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (List<DataRow> rows : groups.values()) {
            if (rows.size() > 1) {
                for (DataRow row : rows) {
                    findings.add(finding(rule, table, row, groupBy.get(0),
                            groupBy.stream().map(field -> field + "=" + row.value(field))
                                    .collect(Collectors.joining("；")),
                            "唯一组合", "存在重复记录", "DUPLICATE"));
                }
            }
        }
        return findings;
    }

    private List<ValidationFinding> duplicateAssert(RuleDefinition rule, DataTable table, List<String> groupBy,
                                                    Object where, Object rawAssert) {
        DuplicateAssertion assertion = duplicateAssertion(rawAssert);
        if (table == null || groupBy.isEmpty() || assertion == null) {
            return Collections.emptyList();
        }
        Map<String, List<DataRow>> groups = new HashMap<>();
        for (DataRow row : table.getRows()) {
            if (where instanceof Map && !RowExpressionEvaluator.matches(where, row)) {
                continue;
            }
            groups.computeIfAbsent(groupKey(row, groupBy), ignored -> new ArrayList<>()).add(row);
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (List<DataRow> rows : groups.values()) {
            int count = rows.size();
            if (compareCount(count, assertion)) {
                continue;
            }
            for (DataRow row : rows) {
                findings.add(finding(rule, table, row, groupBy.get(0),
                        groupSummary(row, groupBy) + "；count=" + count,
                        "count " + assertion.operator + " " + assertion.count,
                        "分组次数断言不成立", "DUPLICATE"));
            }
        }
        return findings;
    }

    @SuppressWarnings("unchecked")
    private DuplicateAssertion duplicateAssertion(Object rawAssert) {
        if (!(rawAssert instanceof Map)) {
            return null;
        }
        Map<String, Object> assertion = (Map<String, Object>) rawAssert;
        String operator = asString(assertion.get("op"));
        if (ValueParsers.isBlank(operator)) {
            operator = asString(assertion.get("operator"));
        }
        Object count = assertion.containsKey("count") ? assertion.get("count") : assertion.get("value");
        DuplicateAssertion parsed = duplicateAssertion(operator, count);
        if (parsed != null) {
            return parsed;
        }
        if (Boolean.TRUE.equals(assertion.get("unique"))) {
            return new DuplicateAssertion("<=", 1);
        }
        return null;
    }

    private DuplicateAssertion duplicateAssertion(String operator, Object rawCount) {
        String text = asString(rawCount).trim();
        String parsedOperator = operator;
        String parsedCount = text;
        for (String candidate : Arrays.asList(">=", "<=", "==", "!=", ">", "<", "=")) {
            if (text.startsWith(candidate)) {
                parsedOperator = candidate;
                parsedCount = text.substring(candidate.length()).trim();
                break;
            }
        }
        if (ValueParsers.isBlank(parsedOperator)) {
            parsedOperator = "<=";
        }
        if (!isSupportedAggregateOperator(parsedOperator)) {
            return null;
        }
        Optional<BigDecimal> count = ValueParsers.decimal(parsedCount);
        if (!count.isPresent()) {
            return null;
        }
        return new DuplicateAssertion(parsedOperator, count.get().intValue());
    }

    private boolean compareCount(int actual, DuplicateAssertion assertion) {
        int compared = Integer.compare(actual, assertion.count);
        switch (assertion.operator) {
            case "==":
            case "=":
                return compared == 0;
            case "!=":
                return compared != 0;
            case ">": return compared > 0;
            case ">=": return compared >= 0;
            case "<": return compared < 0;
            case "<=": return compared <= 0;
            default: return false;
        }
    }

    private DataTable table(Map<String, DataTable> tables, Map<String, Object> params) {
        String tableName = asString(params.get("tableName"));
        if (ValueParsers.isBlank(tableName)) {
            tableName = asString(params.get("table"));
        }
        return tables.get(tableName);
    }

    private Map<String, DataRow> indexBy(DataTable table, String key) {
        Map<String, DataRow> index = new HashMap<>();
        for (DataRow row : table.getRows()) {
            index.putIfAbsent(row.value(key), row);
        }
        return index;
    }

    private String groupKey(DataRow row, List<String> fields) {
        return fields.stream().map(row::value).collect(Collectors.joining("\u001F"));
    }

    private String groupSummary(DataRow row, List<String> fields) {
        return fields.stream().map(field -> field + "=" + row.value(field))
                .collect(Collectors.joining("；"));
    }

    private List<String> sourceFields(List<RelationKey> keys) {
        return keys.stream().map(key -> key.sourceField).collect(Collectors.toList());
    }

    private List<String> targetFields(List<RelationKey> keys) {
        return keys.stream().map(key -> key.targetField).collect(Collectors.toList());
    }

    private String keySummary(DataRow row, List<RelationKey> keys, boolean sourceSide) {
        return keys.stream()
                .map(key -> {
                    String field = sourceSide ? key.sourceField : key.targetField;
                    return field + "=" + row.value(field);
                })
                .collect(Collectors.joining("；"));
    }

    private String aggregateLabel(DataTable table, AggregateSpec aggregate) {
        if ("COUNT".equals(aggregate.fn)) {
            return table.getLogicalName() + ".记录数 汇总";
        }
        return table.getLogicalName() + "." + aggregate.field + " 汇总";
    }

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private BigDecimal decimal(Object value) {
        return ValueParsers.decimal(asString(value)).orElse(BigDecimal.ZERO);
    }

    private String aggregateFn(String fn) {
        if (ValueParsers.isBlank(fn)) {
            return "SUM";
        }
        return fn.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isSupportedAggregateFn(String fn) {
        return "SUM".equals(fn) || "COUNT".equals(fn);
    }

    private boolean isSupportedAggregateOperator(String operator) {
        return "==".equals(operator) || "=".equals(operator) || "!=".equals(operator)
                || ">".equals(operator) || ">=".equals(operator)
                || "<".equals(operator) || "<=".equals(operator);
    }

    private boolean isSupportedJoinOperator(String operator) {
        return isSupportedAggregateOperator(operator);
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private List<String> fields(Object value) {
        if (value instanceof List) {
            List<?> raw = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                result.add(asString(item));
            }
            return result;
        }
        String single = asString(value);
        if (!ValueParsers.isBlank(single)) {
            return Collections.singletonList(single);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<RelationKey> relationKeys(Object rawKeys, String fallbackKey, String fallbackTargetKey) {
        List<RelationKey> result = new ArrayList<>();
        if (rawKeys instanceof List) {
            for (Object rawKey : (List<?>) rawKeys) {
                if (rawKey instanceof Map) {
                    Map<String, Object> key = (Map<String, Object>) rawKey;
                    String sourceField = asString(key.get("sourceField"));
                    String targetField = asString(key.get("targetField"));
                    if (!ValueParsers.isBlank(sourceField) && !ValueParsers.isBlank(targetField)) {
                        result.add(new RelationKey(sourceField, targetField));
                    }
                } else if (!ValueParsers.isBlank(asString(rawKey))) {
                    String field = asString(rawKey);
                    result.add(new RelationKey(field, field));
                }
            }
        }
        if (result.isEmpty() && !ValueParsers.isBlank(fallbackKey)) {
            result.add(new RelationKey(fallbackKey,
                    ValueParsers.isBlank(fallbackTargetKey) ? fallbackKey : fallbackTargetKey));
        }
        return result;
    }

    private static class RelationKey {
        private final String sourceField;
        private final String targetField;

        RelationKey(String sourceField, String targetField) {
            this.sourceField = sourceField;
            this.targetField = targetField;
        }
    }

    private static class AggregateSpec {
        private final String fn;
        private final String field;

        AggregateSpec(String fn, String field) {
            this.fn = fn;
            this.field = field;
        }
    }

    private static class AggregateAssertion {
        private final String operator;
        private final String targetField;
        private final AggregateSpec targetAggregate;
        private final BigDecimal tolerance;

        AggregateAssertion(String operator, String targetField, AggregateSpec targetAggregate,
                           BigDecimal tolerance) {
            this.operator = operator;
            this.targetField = targetField;
            this.targetAggregate = targetAggregate;
            this.tolerance = tolerance;
        }
    }

    private static class AggregateBucket {
        private final DataRow firstRow;
        private BigDecimal value = BigDecimal.ZERO;

        AggregateBucket(DataRow firstRow) {
            this.firstRow = firstRow;
        }

        void add(BigDecimal amount) {
            value = value.add(amount);
        }
    }

    private static class DuplicateAssertion {
        private final String operator;
        private final int count;

        DuplicateAssertion(String operator, int count) {
            this.operator = operator;
            this.count = count;
        }
    }

    private static class JoinAssertion {
        private final Object left;
        private final String operator;
        private final Object right;
        private final BigDecimal tolerance;

        JoinAssertion(Object left, String operator, Object right, BigDecimal tolerance) {
            this.left = left;
            this.operator = operator;
            this.right = right;
            this.tolerance = tolerance;
        }
    }

    private static class JoinValue {
        private final String text;
        private final String summary;
        private final String rawValue;
        private final Optional<BigDecimal> decimal;
        private final String fieldName;

        JoinValue(String text, String summary, String rawValue, Optional<BigDecimal> decimal, String fieldName) {
            this.text = text;
            this.summary = summary;
            this.rawValue = rawValue;
            this.decimal = decimal;
            this.fieldName = fieldName;
        }

        static JoinValue unavailable() {
            return new JoinValue("未知表达式", "未知表达式=无法计算", "无法计算", Optional.empty(), "");
        }
    }

}
