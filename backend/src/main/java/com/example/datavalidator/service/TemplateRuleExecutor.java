package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.RuleBinding;
import com.example.datavalidator.domain.RuleDefinition;
import com.example.datavalidator.domain.ValidationFinding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
                return numericType(rule, table(tables, params), fields(params.get("fields")));
            case "FIELD_EXPRESSION":
                return fieldExpression(rule, table(tables, params), asString(params.get("expression")));
            case "ROW_EXPRESSION":
                return rowExpression(rule, table(tables, params), params.get("conditions"));
            case "EXISTS_IN_TABLE":
                return existsInTable(rule, tables, params);
            case "FIELD_EQUALS":
                return fieldEquals(rule, tables, params);
            case "AGGREGATION_EQUALS":
                return aggregationEquals(rule, tables, params);
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

    private List<ValidationFinding> numericType(RuleDefinition rule, DataTable table, List<String> fields) {
        if (table == null) {
            return Collections.emptyList();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        for (DataRow row : table.getRows()) {
            for (String field : fields) {
                String value = row.value(field);
                if (!ValueParsers.isBlank(value) && !ValueParsers.decimal(value).isPresent()) {
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
                        result.get().getActualSummary(), result.get().getFailedCondition(),
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
                        failed.getActualSummary(), failed.getFailedCondition(),
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
                        source.getLogicalName() + "." + sourceField + "=" + actual
                                + "；" + target.getLogicalName() + "." + targetField + "=" + expected,
                        sourceField + " == " + target.getLogicalName() + "." + targetField,
                        "关联字段值不一致", "RELATION"));
            }
        }
        return findings;
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
                        targetField + "=" + row.value(targetField) + "；"
                                + source.getLogicalName() + "." + sumField + " 汇总=" + formatDecimal(expected),
                        targetField + " == " + source.getLogicalName() + "." + sumField + " 汇总值",
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

    private DataTable table(Map<String, DataTable> tables, Map<String, Object> params) {
        return tables.get(asString(params.get("tableName")));
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

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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

}
