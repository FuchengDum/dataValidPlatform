package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.Evidence;
import com.example.datavalidator.domain.RuleDefinition;
import com.example.datavalidator.domain.Severity;
import com.example.datavalidator.domain.ValidationFinding;

class TemplateFindingFactory {
    private TemplateFindingFactory() {
    }

    static ValidationFinding finding(RuleDefinition rule, DataTable table, DataRow row, String field,
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
        finding.setImpact(rule.getSeverity() == Severity.CRITICAL
                ? "可能影响业务数据准确性。"
                : "建议人工复核，避免后续统计口径偏差。");
        finding.setSuggestion("请核查 " + table.getLogicalName() + " 表记录 " + row.getPrimaryKey()
                + " 的字段 " + field + "，参考期望值修正或回溯上游数据逻辑。");
        finding.getEvidences().add(evidence(table, row, field, actual, expected, description, evidenceType));
        return finding;
    }

    private static Evidence evidence(DataTable table, DataRow row, String field, String actual,
                                     String expected, String description, String evidenceType) {
        Evidence evidence = new Evidence();
        evidence.setEvidenceType(evidenceType);
        evidence.setTableName(table.getLogicalName());
        evidence.setRecordKey(row.getPrimaryKey());
        evidence.setFieldName(field);
        evidence.setActualValue(actual);
        evidence.setExpectedValue(expected);
        evidence.setCalculation(description);
        return evidence;
    }
}
