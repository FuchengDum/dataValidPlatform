package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.Evidence;
import com.example.datavalidator.domain.RuleBinding;
import com.example.datavalidator.domain.RuleDefinition;
import com.example.datavalidator.domain.Severity;
import com.example.datavalidator.domain.ValidationFinding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TemplateRuleExecutor {
    public List<ValidationFinding> execute(RuleDefinition rule, Map<String, DataTable> tables, RuleBinding binding) {
        if (binding == null || !binding.isEnabled() || !"TEMPLATE".equals(binding.getExecutorType())) {
            return Collections.emptyList();
        }
        DataTable table = tables.get(asString(binding.getTemplateParams().get("tableName")));
        if (table == null) {
            return Collections.emptyList();
        }
        List<String> fields = fields(binding.getTemplateParams().get("fields"));
        switch (binding.getTemplateCode()) {
            case "NOT_NULL":
                return notNull(rule, table, fields);
            case "NON_NEGATIVE":
                return nonNegative(rule, table, fields);
            case "NUMERIC_TYPE":
                return numericType(rule, table, fields);
            default:
                return Collections.emptyList();
        }
    }

    private List<ValidationFinding> notNull(RuleDefinition rule, DataTable table, List<String> fields) {
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

    private ValidationFinding finding(RuleDefinition rule, DataTable table, DataRow row, String field,
                                      String actual, String expected, String description, String evidenceType) {
        ValidationFinding finding = new ValidationFinding();
        finding.setFindingId(IdFactory.next("f"));
        finding.setRuleId(rule.getRuleId());
        finding.setRuleName(rule.getRuleName());
        finding.setRuleCategory(rule.getCategory());
        finding.setSeverity(rule.getSeverity());
        finding.setTableName(table.getLogicalName());
        finding.setRecordKey(row.getPrimaryKey());
        finding.setFieldName(field);
        finding.setActualValue(actual);
        finding.setExpectedValue(expected);
        finding.setDescription(description);
        finding.setScenarioIds(rule.getScenarioIds());
        finding.setReason(row.getPrimaryKey() + " 命中规则 " + rule.getRuleId() + "：" + description + "。");
        finding.setImpact(rule.getSeverity() == Severity.CRITICAL ? "可能影响业务数据准确性。" : "建议人工复核，避免后续统计口径偏差。");
        finding.setSuggestion("请核查 " + table.getLogicalName() + " 表记录 " + row.getPrimaryKey()
                + " 的字段 " + field + "，参考期望值修正或回溯上游数据逻辑。");
        Evidence evidence = new Evidence();
        evidence.setEvidenceType(evidenceType);
        evidence.setTableName(table.getLogicalName());
        evidence.setRecordKey(row.getPrimaryKey());
        evidence.setFieldName(field);
        evidence.setActualValue(actual);
        evidence.setExpectedValue(expected);
        evidence.setCalculation(description);
        finding.getEvidences().add(evidence);
        return finding;
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
        return Collections.emptyList();
    }
}
