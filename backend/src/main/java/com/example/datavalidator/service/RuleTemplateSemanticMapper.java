package com.example.datavalidator.service;

import com.example.datavalidator.persistence.RuleDefinitionEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class RuleTemplateSemanticMapper {
    private static final List<String> FIELD_TEMPLATES = Arrays.asList("NOT_NULL", "NON_NEGATIVE", "NUMERIC_TYPE");
    private static final String AMOUNT_EXPRESSION = "实付金额 == 订单金额 - 优惠金额 && 实付金额 <= 订单金额";

    RuleTemplateSemanticMatch recommend(RuleDefinitionEntity rule, Map<String, List<String>> tableFields) {
        String text = searchableText(rule);
        RuleTemplateSemanticMatch match = amountExpression(rule, tableFields, text);
        if (match.isApplicable()) {
            return match;
        }
        match = aggregation(rule, tableFields, text);
        if (match.isApplicable()) {
            return match;
        }
        match = fieldEquals(rule, tableFields, text);
        if (match.isApplicable()) {
            return match;
        }
        match = existsInTable(rule, tableFields, text);
        if (match.isApplicable()) {
            return match;
        }
        match = duplicateCheck(rule, tableFields, text);
        if (match.isApplicable()) {
            return match;
        }
        match = fieldList(rule, tableFields, text);
        if (match.isApplicable()) {
            return match;
        }
        return RuleTemplateSemanticMatch.unavailable("当前规则无法映射到已支持模板，建议继续使用内置执行器。");
    }

    private RuleTemplateSemanticMatch fieldList(RuleDefinitionEntity rule,
                                                Map<String, List<String>> tableFields,
                                                String text) {
        String tableName = firstApplicableTable(rule, tableFields);
        List<String> headers = tableFields.getOrDefault(tableName, Collections.emptyList());
        if (headers.isEmpty()) {
            return RuleTemplateSemanticMatch.unavailable("规则未匹配到可用业务表。");
        }
        String templateCode = resolveFieldTemplate(rule, text);
        List<String> fields = fieldsInText(headers, text);
        if (fields.isEmpty()) {
            fields.add(headers.get(0));
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", tableName);
        params.put("fields", fields);
        return applicable(templateCode, params, "基于规则文本和字段快照推荐字段级模板。", "MEDIUM");
    }

    private String resolveFieldTemplate(RuleDefinitionEntity rule, String text) {
        if (FIELD_TEMPLATES.contains(rule.getTemplateCode())) {
            return rule.getTemplateCode();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (text.contains("非空") || text.contains("不能为空") || text.contains("必填")
                || lower.contains("not null")) {
            return "NOT_NULL";
        }
        if (text.contains("数值") || text.contains("类型") || lower.contains("numeric")) {
            return "NUMERIC_TYPE";
        }
        return "NON_NEGATIVE";
    }

    private RuleTemplateSemanticMatch amountExpression(RuleDefinitionEntity rule,
                                                       Map<String, List<String>> tableFields,
                                                       String text) {
        String tableName = firstApplicableTable(rule, tableFields);
        List<String> headers = tableFields.getOrDefault(tableName, Collections.emptyList());
        if (!hasFields(headers, "订单金额", "优惠金额", "实付金额")) {
            return RuleTemplateSemanticMatch.unavailable("缺少金额关系字段。");
        }
        boolean hasBalance = text.contains("实付金额") && text.contains("订单金额")
                && text.contains("优惠金额") && (text.contains("减") || text.contains("-"));
        boolean hasLimit = text.contains("实付金额") && text.contains("订单金额")
                && (text.contains("<=") || text.contains("不得大于") || text.contains("不大于"));
        if (!hasBalance || !hasLimit) {
            return RuleTemplateSemanticMatch.unavailable("规则文本未匹配完整金额关系。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", tableName);
        params.put("expression", AMOUNT_EXPRESSION);
        return applicable("FIELD_EXPRESSION", params, "基于金额字段关系推荐字段表达式模板。", "HIGH");
    }

    private RuleTemplateSemanticMatch existsInTable(RuleDefinitionEntity rule,
                                                    Map<String, List<String>> tableFields,
                                                    String text) {
        if (!containsAny(text, "存在于", "必须存在", "关联记录", "exists")) {
            return RuleTemplateSemanticMatch.unavailable("规则文本未匹配跨表存在性。");
        }
        List<String> tables = applicableTables(rule, tableFields);
        if (tables.size() < 2) {
            return RuleTemplateSemanticMatch.unavailable("跨表存在性规则缺少两张表。");
        }
        String key = commonFieldInText(tableFields.get(tables.get(0)), tableFields.get(tables.get(1)), text, "");
        if (ValueParsers.isBlank(key)) {
            return RuleTemplateSemanticMatch.unavailable("跨表存在性规则缺少公共 key。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", tables.get(0));
        params.put("target", tables.get(1));
        params.put("key", key);
        return applicable("EXISTS_IN_TABLE", params, "基于跨表存在性语义推荐模板。", "HIGH");
    }

    private RuleTemplateSemanticMatch fieldEquals(RuleDefinitionEntity rule,
                                                  Map<String, List<String>> tableFields,
                                                  String text) {
        if (!containsAny(text, "一致", "应与", "必须等于", "=")) {
            return RuleTemplateSemanticMatch.unavailable("规则文本未匹配跨表字段一致。");
        }
        List<String> tables = applicableTables(rule, tableFields);
        if (tables.size() < 2) {
            return RuleTemplateSemanticMatch.unavailable("跨表字段一致规则缺少两张表。");
        }
        String key = keyField(tableFields.get(tables.get(0)), tableFields.get(tables.get(1)), text);
        String field = commonFieldInText(tableFields.get(tables.get(0)), tableFields.get(tables.get(1)), text, key);
        if (ValueParsers.isBlank(key) || ValueParsers.isBlank(field)) {
            return RuleTemplateSemanticMatch.unavailable("跨表字段一致规则缺少 key 或比较字段。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", tables.get(0));
        params.put("target", tables.get(1));
        params.put("key", key);
        params.put("sourceField", field);
        params.put("targetField", field);
        return applicable("FIELD_EQUALS", params, "基于跨表字段一致语义推荐模板。", "HIGH");
    }

    private RuleTemplateSemanticMatch aggregation(RuleDefinitionEntity rule,
                                                  Map<String, List<String>> tableFields,
                                                  String text) {
        if (!containsAny(text, "之和", "汇总", "合计", "sum")) {
            return RuleTemplateSemanticMatch.unavailable("规则文本未匹配聚合一致。");
        }
        List<String> tables = applicableTables(rule, tableFields);
        if (tables.size() < 2) {
            return RuleTemplateSemanticMatch.unavailable("聚合一致规则缺少明细表和目标表。");
        }
        String source = tables.get(0);
        String target = tables.get(1);
        String groupBy = commonFieldInText(tableFields.get(source), tableFields.get(target), text, "");
        String sumField = fieldContaining(tableFields.get(source), text, "小计", "金额", "数量");
        String targetField = fieldContaining(tableFields.get(target), text, "金额", "数量");
        if (ValueParsers.isBlank(groupBy) || ValueParsers.isBlank(sumField) || ValueParsers.isBlank(targetField)) {
            return RuleTemplateSemanticMatch.unavailable("聚合一致规则缺少分组字段、汇总字段或目标字段。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", source);
        params.put("target", target);
        params.put("groupBy", groupBy);
        params.put("sum", sumField);
        params.put("targetField", targetField);
        return applicable("AGGREGATION_EQUALS", params, "基于明细汇总到主表语义推荐模板。", "HIGH");
    }

    private RuleTemplateSemanticMatch duplicateCheck(RuleDefinitionEntity rule,
                                                     Map<String, List<String>> tableFields,
                                                     String text) {
        if (!containsAny(text, "唯一", "重复", "不得重复", "unique")) {
            return RuleTemplateSemanticMatch.unavailable("规则文本未匹配重复检查。");
        }
        String tableName = firstApplicableTable(rule, tableFields);
        List<String> fields = fieldsInText(tableFields.getOrDefault(tableName, Collections.emptyList()), text);
        fields.removeIf(field -> field.endsWith("ID") && fields.size() > 1 && field.equals(tableFields.get(tableName).get(0)));
        if (fields.isEmpty()) {
            return RuleTemplateSemanticMatch.unavailable("重复检查规则缺少分组字段。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", tableName);
        params.put("groupBy", fields);
        return applicable("DUPLICATE_CHECK", params, "基于唯一组合语义推荐重复检查模板。", "HIGH");
    }

    private RuleTemplateSemanticMatch applicable(String templateCode, Map<String, Object> params,
                                                 String reason, String confidence) {
        RuleTemplateSemanticMatch match = new RuleTemplateSemanticMatch();
        match.setApplicable(true);
        match.setTemplateCode(templateCode);
        match.setTemplateParams(params);
        match.setMatchedReason(reason);
        match.setConfidence(confidence);
        return match;
    }

    private String searchableText(RuleDefinitionEntity rule) {
        return safe(rule.getRuleName()) + " " + safe(rule.getDescription()) + " " + safe(rule.getPseudoLogic());
    }

    private List<String> applicableTables(RuleDefinitionEntity rule, Map<String, List<String>> tableFields) {
        List<String> result = new ArrayList<>();
        for (String item : safe(rule.getApplicableTables()).split("[,，/、\\s]+")) {
            if (tableFields.containsKey(item) && !result.contains(item)) {
                result.add(item);
            }
        }
        for (String table : tableFields.keySet()) {
            if (result.size() >= 2) {
                break;
            }
            if (!result.contains(table)) {
                result.add(table);
            }
        }
        return result;
    }

    private String firstApplicableTable(RuleDefinitionEntity rule, Map<String, List<String>> tableFields) {
        List<String> tables = applicableTables(rule, tableFields);
        return tables.isEmpty() ? "" : tables.get(0);
    }

    private List<String> fieldsInText(List<String> headers, String text) {
        List<String> result = new ArrayList<>();
        for (String header : headers) {
            if (!ValueParsers.isBlank(header) && text.contains(header)) {
                result.add(header);
            }
        }
        return result;
    }

    private String commonFieldInText(List<String> left, List<String> right, String text, String excluded) {
        for (String field : left) {
            if (!field.equals(excluded) && right.contains(field) && text.contains(field)) {
                return field;
            }
        }
        return "";
    }

    private String keyField(List<String> left, List<String> right, String text) {
        for (String field : left) {
            if (right.contains(field) && text.contains("by " + field)) {
                return field;
            }
        }
        for (String field : left) {
            if (right.contains(field) && field.endsWith("ID") && text.contains(field)) {
                return field;
            }
        }
        return commonFieldInText(left, right, text, "");
    }

    private String fieldContaining(List<String> fields, String text, String... keywords) {
        for (String field : fields) {
            if (!text.contains(field)) {
                continue;
            }
            for (String keyword : keywords) {
                if (field.contains(keyword)) {
                    return field;
                }
            }
        }
        return "";
    }

    private boolean containsAny(String text, String... tokens) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFields(List<String> headers, String... fields) {
        if (headers == null) {
            return false;
        }
        for (String field : fields) {
            if (!headers.contains(field)) {
                return false;
            }
        }
        return true;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
