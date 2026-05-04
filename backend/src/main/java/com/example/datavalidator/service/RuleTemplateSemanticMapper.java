package com.example.datavalidator.service;

import com.example.datavalidator.persistence.RuleDefinitionEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class RuleTemplateSemanticMapper {
    private static final List<String> FIELD_TEMPLATES = Arrays.asList("NOT_NULL", "NON_NEGATIVE", "NUMERIC_TYPE");

    RuleTemplateSemanticMatch recommend(RuleDefinitionEntity rule, Map<String, List<String>> tableFields) {
        String text = searchableText(rule);
        RuleTemplateSemanticMatch match = amountExpression(rule, tableFields, text);
        if (match.isApplicable()) {
            return match;
        }
        match = signedChangeExpression(rule, tableFields, text);
        if (match.isApplicable()) {
            return match;
        }
        match = relationExists(rule, tableFields, text);
        if (match.isApplicable()) {
            return match;
        }
        match = genericRowExpression(rule, tableFields, text);
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
        for (String tableName : applicableTables(rule, tableFields)) {
            RowRelationship relationship = rowRelationship(tableFields.getOrDefault(tableName, Collections.emptyList()), text);
            if (relationship != null) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("tableName", tableName);
                params.put("conditions", Arrays.asList(
                        condition(field(relationship.left), "==",
                                op("-", field(relationship.base), field(relationship.deduction))),
                        condition(field(relationship.left), "<=", field(relationship.base))));
                return applicable("ROW_EXPRESSION", params, "基于字段间计算关系推荐行表达式模板。", "HIGH");
            }
        }
        return RuleTemplateSemanticMatch.unavailable("规则文本未匹配完整字段计算关系。");
    }

    private RuleTemplateSemanticMatch signedChangeExpression(RuleDefinitionEntity rule,
                                                             Map<String, List<String>> tableFields,
                                                             String text) {
        if (!containsAny(text, "case when", "入库", "出库", "为正", "为负")) {
            return RuleTemplateSemanticMatch.unavailable("规则文本未匹配条件符号计算关系。");
        }
        for (String tableName : applicableTables(rule, tableFields)) {
            List<String> headers = tableFields.getOrDefault(tableName, Collections.emptyList());
            String after = fieldContainingAll(headers, text, "后", "库存");
            String before = fieldContainingAll(headers, text, "前", "库存");
            String quantity = fieldContaining(headers, text, "数量");
            String type = fieldContaining(headers, text, "类型");
            if (ValueParsers.isBlank(after) || ValueParsers.isBlank(before)
                    || ValueParsers.isBlank(quantity) || ValueParsers.isBlank(type)) {
                continue;
            }
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("tableName", tableName);
            params.put("conditions", Collections.singletonList(
                    condition(field(after), "==",
                            op("+", field(before),
                                    ifNode(condition(field(type), "==", literal("入库")),
                                            field(quantity), op("-", literal(0), field(quantity)))))));
            return applicable("ROW_EXPRESSION", params, "基于条件符号计算关系推荐行表达式模板。", "HIGH");
        }
        return RuleTemplateSemanticMatch.unavailable("条件符号计算规则缺少可映射字段。");
    }

    private RuleTemplateSemanticMatch genericRowExpression(RuleDefinitionEntity rule,
                                                           Map<String, List<String>> tableFields,
                                                           String text) {
        for (String tableName : applicableTables(rule, tableFields)) {
            List<String> headers = tableFields.getOrDefault(tableName, Collections.emptyList());
            List<Map<String, Object>> conditions = new ArrayList<>();
            addRatioCondition(conditions, headers, text);
            addMultiplicationCondition(conditions, headers, text);
            addStatusComparisonConditions(conditions, headers, text);
            addPositiveQuantityCondition(conditions, headers, text);
            addStatusTimeConditions(conditions, headers, text);
            if (!conditions.isEmpty()) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("tableName", tableName);
                params.put("conditions", conditions);
                return applicable("ROW_EXPRESSION", params, "基于条件行断言语义推荐结构化行表达式模板。", "HIGH");
            }
        }
        return RuleTemplateSemanticMatch.unavailable("规则文本未匹配通用行断言。");
    }

    private void addRatioCondition(List<Map<String, Object>> conditions, List<String> headers, String text) {
        String normalized = normalize(text);
        for (String left : fieldsInText(headers, text)) {
            for (String base : fieldsInText(headers, text)) {
                if (left.equals(base)) {
                    continue;
                }
                if (normalized.contains(left + ">" + base + "*0.5")
                        || normalized.contains(left + "不得超过" + base + "的50%")) {
                    conditions.add(condition(field(left), "<=", op("*", field(base), literal("0.5"))));
                    return;
                }
            }
        }
    }

    private void addMultiplicationCondition(List<Map<String, Object>> conditions, List<String> headers, String text) {
        String normalized = normalize(text).replace("×", "*");
        for (String left : fieldsInText(headers, text)) {
            for (String first : fieldsInText(headers, text)) {
                for (String second : fieldsInText(headers, text)) {
                    if (left.equals(first) || left.equals(second) || first.equals(second)) {
                        continue;
                    }
                    if (normalized.contains(left + "=" + first + "*" + second)
                            || normalized.contains(left + "-" + first + "*" + second)
                            || normalized.contains(left + "!=" + first + "*" + second)) {
                        conditions.add(condition(field(left), "==", op("*", field(first), field(second))));
                        return;
                    }
                }
            }
        }
    }

    private void addStatusComparisonConditions(List<Map<String, Object>> conditions, List<String> headers, String text) {
        String stateField = fieldContaining(headers, text, "状态", "类型");
        String stateValue = quotedValueAfterField(text, stateField);
        Object statePredicate = ValueParsers.isBlank(stateField) || ValueParsers.isBlank(stateValue)
                ? null : condition(field(stateField), "==", literal(stateValue));
        String normalized = normalize(text);
        for (String left : fieldsInText(headers, text)) {
            if (left.equals(stateField) || left.endsWith("ID")) {
                continue;
            }
            for (String right : fieldsInText(headers, text)) {
                if (left.equals(right) || right.equals(stateField) || right.endsWith("ID")) {
                    continue;
                }
                if (normalized.contains(left + "<" + right)) {
                    conditions.add(withWhen(condition(field(left), ">=", field(right)), statePredicate));
                    return;
                }
            }
            if (statePredicate != null && (normalized.contains(left + "<=0") || normalized.contains(left + "<0")
                    || text.contains(left + "必须为正") || text.contains(left + "应大于0"))) {
                conditions.add(withWhen(condition(field(left), ">", literal(0)), statePredicate));
                return;
            }
            if (normalized.contains(left + "=0") || text.contains(left + "不得为0")) {
                conditions.add(withWhen(condition(field(left), "!=", literal(0)), statePredicate));
            }
        }
    }

    private void addPositiveQuantityCondition(List<Map<String, Object>> conditions, List<String> headers, String text) {
        if (!containsAny(text, "正整数", "必须为正数", "数量<=0", "数量<0")) {
            return;
        }
        String quantity = fieldContaining(headers, text, "数量");
        if (ValueParsers.isBlank(quantity)) {
            return;
        }
        String type = fieldContaining(headers, text, "类型");
        if (!ValueParsers.isBlank(type) && containsAny(text, "入库", "出库")) {
            conditions.add(when(condition(field(quantity), ">", literal(0)),
                    condition(field(type), "in", Arrays.asList("入库", "出库"))));
            return;
        }
        conditions.add(condition(field(quantity), ">", literal(0)));
    }

    private void addStatusTimeConditions(List<Map<String, Object>> conditions, List<String> headers, String text) {
        if (!(text.contains("支付时间") && text.contains("下单时间") && text.contains("待支付"))) {
            return;
        }
        if (!headers.contains("订单状态") || !headers.contains("支付时间") || !headers.contains("下单时间")) {
            return;
        }
        conditions.add(when(condition(field("支付时间"), "isNull", null),
                condition(field("订单状态"), "==", literal("待支付"))));
        conditions.add(when(condition(field("支付时间"), ">=", field("下单时间")),
                condition(field("订单状态"), "in", Arrays.asList("已支付", "已发货", "已完成"))));
    }

    private RowRelationship rowRelationship(List<String> headers, String text) {
        List<String> fields = fieldsInText(headers, text);
        for (String left : fields) {
            for (String base : fields) {
                if (left.equals(base)) {
                    continue;
                }
                for (String deduction : fields) {
                    if (left.equals(deduction) || base.equals(deduction)) {
                        continue;
                    }
                    if (hasSubtractionRelation(text, left, base, deduction) && hasUpperBound(text, left, base)) {
                        return new RowRelationship(left, base, deduction);
                    }
                }
            }
        }
        return null;
    }

    private boolean hasSubtractionRelation(String text, String left, String base, String deduction) {
        String normalized = normalize(text);
        return normalized.contains(left + "=" + base + "-" + deduction)
                || normalized.contains(left + "==" + base + "-" + deduction)
                || normalized.contains(left + "=" + base + "减" + deduction)
                || normalized.contains(left + "==" + base + "减" + deduction)
                || normalized.contains(left + "等于" + base + "减" + deduction)
                || normalized.contains(left + "应等于" + base + "减" + deduction);
    }

    private boolean hasUpperBound(String text, String left, String base) {
        String normalized = normalize(text);
        return normalized.contains(left + "<=" + base)
                || normalized.contains(left + "≤" + base)
                || normalized.contains(left + ">" + base)
                || normalized.contains(left + "不得大于" + base)
                || normalized.contains(left + "不大于" + base)
                || normalized.contains(left + "不超过" + base);
    }

    private String normalize(String text) {
        return safe(text).replaceAll("\\s+", "");
    }

    private RuleTemplateSemanticMatch existsInTable(RuleDefinitionEntity rule,
                                                    Map<String, List<String>> tableFields,
                                                    String text) {
        if (!containsAny(text, "存在于", "中存在", "必须存在", "关联记录", "exists")) {
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

    private RuleTemplateSemanticMatch relationExists(RuleDefinitionEntity rule,
                                                     Map<String, List<String>> tableFields,
                                                     String text) {
        RuleTemplateSemanticMatch inventoryDeduction = inventoryDeductionRelation(tableFields, text);
        if (inventoryDeduction.isApplicable()) {
            return inventoryDeduction;
        }
        RuleTemplateSemanticMatch canceledPayment = canceledPaymentAntiRelation(tableFields, text);
        if (canceledPayment.isApplicable()) {
            return canceledPayment;
        }
        RuleTemplateSemanticMatch delistedOutbound = delistedOutboundAntiRelation(tableFields, text);
        if (delistedOutbound.isApplicable()) {
            return delistedOutbound;
        }
        RuleTemplateSemanticMatch paymentStatus = paymentStatusRelation(tableFields, text);
        if (paymentStatus.isApplicable()) {
            return paymentStatus;
        }
        return RuleTemplateSemanticMatch.unavailable("规则文本未匹配通用关系存在 DSL。");
    }

    private RuleTemplateSemanticMatch paymentStatusRelation(Map<String, List<String>> tableFields, String text) {
        if (!(containsAny(text, "支付成功记录", "支付状态='支付成功'", "支付状态=支付成功")
                && containsAny(text, "必须有", "not exists", "notexists"))) {
            return RuleTemplateSemanticMatch.unavailable("未匹配支付成功存在性。");
        }
        String source = tableContainingFields(tableFields, "订单ID", "订单状态");
        String target = tableContainingFields(tableFields, "订单ID", "支付状态");
        if (ValueParsers.isBlank(source) || ValueParsers.isBlank(target)) {
            return RuleTemplateSemanticMatch.unavailable("支付成功存在性缺少订单表或支付表。");
        }
        Map<String, Object> params = relationParams(source, target,
                Collections.singletonList(relationKey("订单ID", "订单ID")), true);
        params.put("sourceWhere", condition(field("订单状态"), "in", Arrays.asList("已支付", "已发货", "已完成")));
        params.put("targetWhere", condition(field("支付状态"), "==", literal("支付成功")));
        return applicable("RELATION_EXISTS", params, "基于状态过滤和支付记录存在性推荐关系存在模板。", "HIGH");
    }

    private RuleTemplateSemanticMatch inventoryDeductionRelation(Map<String, List<String>> tableFields, String text) {
        if (!(containsAny(text, "库存出库记录", "出库数量")
                && containsAny(text, "not exists", "notexists", "对应"))) {
            return RuleTemplateSemanticMatch.unavailable("未匹配库存出库存在性。");
        }
        String source = tableContainingFields(tableFields, "订单ID", "商品ID", "数量");
        String target = tableContainingFields(tableFields, "关联订单ID", "商品ID", "变动数量", "变动类型");
        String statusTable = tableContainingFields(tableFields, "订单ID", "订单状态");
        if (ValueParsers.isBlank(source) || ValueParsers.isBlank(target) || ValueParsers.isBlank(statusTable)) {
            return RuleTemplateSemanticMatch.unavailable("库存扣减存在性缺少明细表、库存流水表或订单表。");
        }
        Map<String, Object> params = relationParams(source, target,
                Arrays.asList(
                        relationKey("订单ID", "关联订单ID"),
                        relationKey("商品ID", "商品ID"),
                        relationKey("数量", "变动数量")),
                true);
        params.put("targetWhere", condition(field("变动类型"), "==", literal("出库")));
        params.put("sourceExists", sourceExists(statusTable,
                Collections.singletonList(relationKey("订单ID", "订单ID")),
                condition(field("订单状态"), "in", Arrays.asList("已支付", "已发货", "已完成"))));
        return applicable("RELATION_EXISTS", params, "基于前置订单状态和复合 key 推荐库存扣减存在模板。", "HIGH");
    }

    private RuleTemplateSemanticMatch canceledPaymentAntiRelation(Map<String, List<String>> tableFields, String text) {
        if (!containsAll(text, "已取消", "退款金额", "支付成功")) {
            return RuleTemplateSemanticMatch.unavailable("未匹配取消订单支付反向存在性。");
        }
        String source = tableContainingFields(tableFields, "订单ID", "支付状态", "退款金额");
        String target = tableContainingFields(tableFields, "订单ID", "订单状态");
        if (ValueParsers.isBlank(source) || ValueParsers.isBlank(target)) {
            return RuleTemplateSemanticMatch.unavailable("取消订单支付反向存在性缺少支付表或订单表。");
        }
        Map<String, Object> params = relationParams(source, target,
                Collections.singletonList(relationKey("订单ID", "订单ID")), false);
        params.put("sourceWhere", and(
                condition(field("支付状态"), "==", literal("支付成功")),
                condition(field("退款金额"), "==", literal(0))));
        params.put("targetWhere", condition(field("订单状态"), "==", literal("已取消")));
        return applicable("RELATION_EXISTS", params, "基于取消状态和支付退款条件推荐反向关系存在模板。", "HIGH");
    }

    private RuleTemplateSemanticMatch delistedOutboundAntiRelation(Map<String, List<String>> tableFields, String text) {
        if (!containsAll(text, "下架", "出库")) {
            return RuleTemplateSemanticMatch.unavailable("未匹配下架商品出库反向存在性。");
        }
        String source = tableContainingFields(tableFields, "商品ID", "变动类型");
        String target = tableContainingFields(tableFields, "商品ID", "上架状态");
        if (ValueParsers.isBlank(source) || ValueParsers.isBlank(target)) {
            return RuleTemplateSemanticMatch.unavailable("下架商品出库反向存在性缺少库存流水表或商品表。");
        }
        Map<String, Object> params = relationParams(source, target,
                Collections.singletonList(relationKey("商品ID", "商品ID")), false);
        params.put("sourceWhere", condition(field("变动类型"), "==", literal("出库")));
        params.put("targetWhere", condition(field("上架状态"), "==", literal("已下架")));
        return applicable("RELATION_EXISTS", params, "基于出库流水和下架商品状态推荐反向关系存在模板。", "HIGH");
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
        FieldPair pair = fieldPair(tableFields.get(tables.get(0)), tableFields.get(tables.get(1)), text, key);
        if (ValueParsers.isBlank(key) || pair == null) {
            return RuleTemplateSemanticMatch.unavailable("跨表字段一致规则缺少 key 或比较字段。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", tables.get(0));
        params.put("target", tables.get(1));
        params.put("key", key);
        params.put("sourceField", pair.sourceField);
        params.put("targetField", pair.targetField);
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
        String sumField = summedField(tableFields.get(source), text);
        if (ValueParsers.isBlank(sumField) && tables.size() > 1) {
            source = tables.get(1);
            target = tables.get(0);
            sumField = summedField(tableFields.get(source), text);
        }
        String groupBy = commonFieldInText(tableFields.get(source), tableFields.get(target), text, "");
        String targetField = targetAggregationField(tableFields.get(target), text, sumField);
        if (ValueParsers.isBlank(groupBy) || ValueParsers.isBlank(sumField) || ValueParsers.isBlank(targetField)) {
            return RuleTemplateSemanticMatch.unavailable("聚合一致规则缺少分组字段、汇总字段或目标字段。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", source);
        params.put("target", target);
        params.put("groupBy", Collections.singletonList(relationKey(groupBy, groupBy)));
        params.put("aggregate", aggregate("SUM", sumField));
        params.put("assert", aggregateAssertion(targetField, text));
        return applicable("AGGREGATE_ASSERT", params, "基于分组聚合断言语义推荐模板。", "HIGH");
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
        Object where = duplicateWhere(tableFields.getOrDefault(tableName, Collections.emptyList()), text);
        if (where != null) {
            params.put("where", where);
        }
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

    private Map<String, Object> condition(Object left, String operator, Object right) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("left", left);
        condition.put("operator", operator);
        condition.put("right", right);
        return condition;
    }

    private Map<String, Object> and(Object... predicates) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("and", Arrays.asList(predicates));
        return condition;
    }

    private Map<String, Object> relationKey(String sourceField, String targetField) {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("sourceField", sourceField);
        key.put("targetField", targetField);
        return key;
    }

    private Map<String, Object> relationParams(String source, String target,
                                               List<Map<String, Object>> keys,
                                               boolean expectExists) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", source);
        params.put("target", target);
        params.put("keys", keys);
        params.put("expectExists", expectExists);
        return params;
    }

    private Map<String, Object> aggregate(String fn, String field) {
        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("fn", fn);
        aggregate.put("field", field);
        return aggregate;
    }

    private Map<String, Object> aggregateAssertion(String targetField, String text) {
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("op", "==");
        assertion.put("tolerance", 0.01);
        if (containsAny(text, "分别汇总", "按日期", "按日", "指标")) {
            assertion.put("aggregate", aggregate("SUM", targetField));
        } else {
            assertion.put("targetField", targetField);
        }
        return assertion;
    }

    private Map<String, Object> sourceExists(String target, List<Map<String, Object>> keys, Object targetWhere) {
        Map<String, Object> sourceExists = new LinkedHashMap<>();
        sourceExists.put("target", target);
        sourceExists.put("keys", keys);
        sourceExists.put("targetWhere", targetWhere);
        return sourceExists;
    }

    private Map<String, Object> when(Map<String, Object> condition, Object predicate) {
        condition.put("when", predicate);
        return condition;
    }

    private Map<String, Object> withWhen(Map<String, Object> condition, Object predicate) {
        if (predicate != null) {
            condition.put("when", predicate);
        }
        return condition;
    }

    private Map<String, Object> field(String fieldName) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("field", fieldName);
        return expression;
    }

    private Map<String, Object> literal(Object value) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("literal", value);
        return expression;
    }

    private Map<String, Object> op(String operator, Object left, Object right) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("op", operator);
        expression.put("left", left);
        expression.put("right", right);
        return expression;
    }

    private Map<String, Object> ifNode(Object predicate, Object thenNode, Object elseNode) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("if", predicate);
        expression.put("then", thenNode);
        expression.put("else", elseNode);
        return expression;
    }

    private String searchableText(RuleDefinitionEntity rule) {
        return safe(rule.getRuleName()) + " " + safe(rule.getDescription()) + " " + safe(rule.getPseudoLogic());
    }

    private List<String> applicableTables(RuleDefinitionEntity rule, Map<String, List<String>> tableFields) {
        List<String> result = new ArrayList<>();
        for (String item : safe(rule.getApplicableTables()).split("[,，/、↔\\s]+")) {
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

    private FieldPair fieldPair(List<String> sourceFields, List<String> targetFields, String text, String key) {
        String common = commonFieldInText(sourceFields, targetFields, text, key);
        if (!ValueParsers.isBlank(common)) {
            return new FieldPair(common, common);
        }
        String source = firstComparableField(sourceFields, text, key);
        String target = firstComparableField(targetFields, text, key);
        if (ValueParsers.isBlank(source) || ValueParsers.isBlank(target)) {
            return null;
        }
        return new FieldPair(source, target);
    }

    private String firstComparableField(List<String> fields, String text, String key) {
        for (String field : fields) {
            if (!field.equals(key) && !field.endsWith("ID") && text.contains(field)) {
                return field;
            }
        }
        return "";
    }

    private String quotedValueAfterField(String text, String field) {
        if (ValueParsers.isBlank(field)) {
            return "";
        }
        Matcher matcher = Pattern.compile(Pattern.quote(field) + "\\s*=\\s*'([^']+)'").matcher(text);
        return matcher.find() ? matcher.group(1) : "";
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

    private String targetAggregationField(List<String> fields, String text, String sumField) {
        for (String field : fields) {
            if (!field.equals(sumField) && text.contains(field) && containsAny(field, "实付金额", "订单金额", "金额", "数量")) {
                return field;
            }
        }
        return "";
    }

    private String summedField(List<String> fields, String text) {
        for (String field : fields) {
            if (Pattern.compile("(?i)sum\\([^)]*" + Pattern.quote(field)).matcher(text).find()) {
                return field;
            }
        }
        String field = fieldContaining(fields, text, "小计");
        if (!ValueParsers.isBlank(field)) {
            return field;
        }
        field = fieldContaining(fields, text, "支付金额");
        if (!ValueParsers.isBlank(field)) {
            return field;
        }
        return "";
    }

    private Object duplicateWhere(List<String> fields, String text) {
        for (String field : fields) {
            String value = quotedValueAfterField(text, field);
            if (!ValueParsers.isBlank(value)) {
                return condition(field(field), "==", literal(value));
            }
        }
        return null;
    }

    private String fieldContainingAll(List<String> fields, String text, String... keywords) {
        for (String field : fields) {
            if (!text.contains(field)) {
                continue;
            }
            boolean matched = true;
            for (String keyword : keywords) {
                if (!field.contains(keyword)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return field;
            }
        }
        return "";
    }

    private String tableContainingFields(Map<String, List<String>> tableFields, String... fields) {
        for (Map.Entry<String, List<String>> entry : tableFields.entrySet()) {
            if (entry.getValue().containsAll(Arrays.asList(fields))) {
                return entry.getKey();
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

    private boolean containsAll(String text, String... tokens) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (!lower.contains(token.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class RowRelationship {
        private final String left;
        private final String base;
        private final String deduction;

        RowRelationship(String left, String base, String deduction) {
            this.left = left;
            this.base = base;
            this.deduction = deduction;
        }
    }

    private static class FieldPair {
        private final String sourceField;
        private final String targetField;

        FieldPair(String sourceField, String targetField) {
            this.sourceField = sourceField;
            this.targetField = targetField;
        }
    }
}
