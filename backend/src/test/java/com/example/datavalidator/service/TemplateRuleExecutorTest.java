package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.RuleBinding;
import com.example.datavalidator.domain.RuleCategory;
import com.example.datavalidator.domain.RuleDefinition;
import com.example.datavalidator.domain.Severity;
import com.example.datavalidator.domain.ValidationFinding;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRuleExecutorTest {
    private final TemplateRuleExecutor executor = new TemplateRuleExecutor();

    @Test
    void notNullTemplateUsesConfiguredTableAndFields() {
        DataTable customerProfile = table("customer_profile", "客户编号",
                row("C001", "客户编号", "C001", "证件号", "110101199001010010", "手机号", ""),
                row("C002", "客户编号", "C002", "证件号", "110101199002020020", "手机号", "13800000000"));
        RuleDefinition rule = rule("C001", "客户资料必填字段");
        RuleBinding binding = binding("C001", "NOT_NULL", "customer_profile", "客户编号", "证件号", "手机号");

        List<ValidationFinding> findings = executor.execute(rule, tables(customerProfile), binding);

        assertThat(findings).hasSize(1);
        ValidationFinding finding = findings.get(0);
        assertThat(finding.getTableName()).isEqualTo("customer_profile");
        assertThat(finding.getRecordKey()).isEqualTo("C001");
        assertThat(finding.getFieldName()).isEqualTo("手机号");
        assertThat(finding.getExpectedValue()).isEqualTo("非空");
    }

    @Test
    void nonNegativeTemplateUsesConfiguredFieldsWithoutOrderTerms() {
        DataTable paymentRecord = table("payment_record", "支付流水号",
                row("P001", "支付流水号", "P001", "支付金额", "-12.50", "退款金额", "0"),
                row("P002", "支付流水号", "P002", "支付金额", "20", "退款金额", "1"));
        RuleDefinition rule = rule("P001", "支付金额非负");
        RuleBinding binding = binding("P001", "NON_NEGATIVE", "payment_record", "支付金额", "退款金额");

        List<ValidationFinding> findings = executor.execute(rule, tables(paymentRecord), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("P001");
        assertThat(findings.get(0).getFieldName()).isEqualTo("支付金额");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo(">= 0");
    }

    @Test
    void numericTypeTemplateReportsOnlyNonBlankInvalidNumbers() {
        DataTable paymentRecord = table("payment_record", "支付流水号",
                row("P001", "支付流水号", "P001", "支付金额", "not-a-number"),
                row("P002", "支付流水号", "P002", "支付金额", ""),
                row("P003", "支付流水号", "P003", "支付金额", "20.05"));
        RuleDefinition rule = rule("P002", "支付金额数值类型");
        RuleBinding binding = binding("P002", "NUMERIC_TYPE", "payment_record", "支付金额");

        List<ValidationFinding> findings = executor.execute(rule, tables(paymentRecord), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("P001");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("数值类型");
    }

    @Test
    void fieldExpressionTemplateComparesCalculatedFieldValue() {
        DataTable items = table("order_item", "明细ID",
                row("I001", "明细ID", "I001", "单价", "10", "数量", "2", "小计金额", "20"),
                row("I002", "明细ID", "I002", "单价", "8", "数量", "3", "小计金额", "20"));
        RuleBinding binding = template("T001", "FIELD_EXPRESSION")
                .param("tableName", "order_item")
                .param("expression", "小计金额 == 单价 * 数量")
                .build();

        List<ValidationFinding> findings = executor.execute(rule("T001", "明细金额表达式"), tables(items), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("I002");
        assertThat(findings.get(0).getFieldName()).isEqualTo("小计金额");
        assertThat(findings.get(0).getActualValue()).contains("小计金额=20", "单价 * 数量=24");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("小计金额 == 单价 * 数量");
    }

    @Test
    void fieldExpressionTemplateSupportsAllConditions() {
        DataTable orders = table("t_order", "订单ID",
                row("ORD001", "订单ID", "ORD001", "订单金额", "100", "优惠金额", "10", "实付金额", "90"),
                row("ORD002", "订单ID", "ORD002", "订单金额", "100", "优惠金额", "10", "实付金额", "95"),
                row("ORD003", "订单ID", "ORD003", "订单金额", "100", "优惠金额", "-10", "实付金额", "110"));
        RuleBinding binding = template("R006", "FIELD_EXPRESSION")
                .param("tableName", "t_order")
                .param("expression", "实付金额 == 订单金额 - 优惠金额 && 实付金额 <= 订单金额")
                .build();

        List<ValidationFinding> findings = executor.execute(rule("R006", "实付金额与订单金额关系校验"),
                tables(orders), binding);

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(ValidationFinding::getRecordKey).containsExactly("ORD002", "ORD003");
        assertThat(findings.get(0).getActualValue()).contains("实付金额=95", "订单金额 - 优惠金额=90");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("实付金额 == 订单金额 - 优惠金额");
        assertThat(findings.get(1).getActualValue()).contains("实付金额=110", "订单金额=100");
        assertThat(findings.get(1).getExpectedValue()).isEqualTo("实付金额 <= 订单金额");
    }

    @Test
    void existsInTableTemplateReportsMissingTargetKey() {
        DataTable items = table("order_item", "明细ID",
                row("I001", "明细ID", "I001", "商品ID", "P001"),
                row("I002", "明细ID", "I002", "商品ID", "P404"));
        DataTable products = table("product", "商品ID",
                row("P001", "商品ID", "P001"));
        RuleBinding binding = template("T002", "EXISTS_IN_TABLE")
                .param("source", "order_item")
                .param("target", "product")
                .param("key", "商品ID")
                .build();

        List<ValidationFinding> findings = executor.execute(rule("T002", "商品存在性"), tables(items, products), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getTableName()).isEqualTo("order_item");
        assertThat(findings.get(0).getRecordKey()).isEqualTo("I002");
        assertThat(findings.get(0).getExpectedValue()).contains("product");
    }

    @Test
    void fieldEqualsTemplateComparesRelatedRows() {
        DataTable payments = table("payment", "支付ID",
                row("P001", "支付ID", "P001", "订单ID", "O001", "用户ID", "U001"),
                row("P002", "支付ID", "P002", "订单ID", "O002", "用户ID", "U999"));
        DataTable orders = table("order", "订单ID",
                row("O001", "订单ID", "O001", "用户ID", "U001"),
                row("O002", "订单ID", "O002", "用户ID", "U002"));
        RuleBinding binding = template("T003", "FIELD_EQUALS")
                .param("source", "payment")
                .param("target", "order")
                .param("key", "订单ID")
                .param("sourceField", "用户ID")
                .param("targetField", "用户ID")
                .build();

        List<ValidationFinding> findings = executor.execute(rule("T003", "用户一致性"), tables(payments, orders), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("P002");
        assertThat(findings.get(0).getActualValue()).isEqualTo("U999");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("U002");
    }

    @Test
    void aggregationEqualsTemplateComparesGroupedSumToTargetField() {
        DataTable items = table("order_item", "明细ID",
                row("I001", "明细ID", "I001", "订单ID", "O001", "小计金额", "10"),
                row("I002", "明细ID", "I002", "订单ID", "O001", "小计金额", "15"),
                row("I003", "明细ID", "I003", "订单ID", "O002", "小计金额", "8"));
        DataTable orders = table("order", "订单ID",
                row("O001", "订单ID", "O001", "订单金额", "25"),
                row("O002", "订单ID", "O002", "订单金额", "10"));
        RuleBinding binding = template("T004", "AGGREGATION_EQUALS")
                .param("source", "order_item")
                .param("target", "order")
                .param("groupBy", "订单ID")
                .param("sum", "小计金额")
                .param("targetField", "订单金额")
                .build();

        List<ValidationFinding> findings = executor.execute(rule("T004", "聚合金额一致"), tables(items, orders), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("O002");
        assertThat(findings.get(0).getActualValue()).isEqualTo("10");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("8");
    }

    @Test
    void aggregationEqualsTemplateReportsMissingTargetGroup() {
        DataTable items = table("order_item", "明细ID",
                row("I001", "明细ID", "I001", "订单ID", "O404", "小计金额", "10"));
        DataTable orders = table("order", "订单ID",
                row("O001", "订单ID", "O001", "订单金额", "10"));
        RuleBinding binding = template("T004", "AGGREGATION_EQUALS")
                .param("source", "order_item")
                .param("target", "order")
                .param("groupBy", "订单ID")
                .param("sum", "小计金额")
                .param("targetField", "订单金额")
                .build();

        List<ValidationFinding> findings = executor.execute(rule("T004", "聚合金额一致"), tables(items, orders), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getTableName()).isEqualTo("order_item");
        assertThat(findings.get(0).getRecordKey()).isEqualTo("I001");
        assertThat(findings.get(0).getExpectedValue()).contains("order");
    }

    @Test
    void duplicateCheckTemplateReportsRepeatedGroups() {
        DataTable payments = table("payment", "支付ID",
                row("P001", "支付ID", "P001", "订单ID", "O001", "支付状态", "支付成功"),
                row("P002", "支付ID", "P002", "订单ID", "O001", "支付状态", "支付成功"),
                row("P003", "支付ID", "P003", "订单ID", "O002", "支付状态", "支付成功"));
        RuleBinding binding = template("T005", "DUPLICATE_CHECK")
                .param("tableName", "payment")
                .param("groupBy", Arrays.asList("订单ID", "支付状态"))
                .build();

        List<ValidationFinding> findings = executor.execute(rule("T005", "重复支付检查"), tables(payments), binding);

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(ValidationFinding::getRecordKey).containsExactly("P001", "P002");
    }

    private RuleDefinition rule(String ruleId, String ruleName) {
        RuleDefinition rule = new RuleDefinition();
        rule.setRuleId(ruleId);
        rule.setRuleName(ruleName);
        rule.setCategory(RuleCategory.SINGLE_FIELD_CONSTRAINT);
        rule.setSeverity(Severity.CRITICAL);
        return rule;
    }

    private RuleBinding binding(String ruleId, String templateCode, String tableName, String... fields) {
        RuleBinding binding = new RuleBinding();
        binding.setRuleId(ruleId);
        binding.setExecutorType("TEMPLATE");
        binding.setTemplateCode(templateCode);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", tableName);
        params.put("fields", Arrays.asList(fields));
        binding.setTemplateParams(params);
        return binding;
    }

    private BindingBuilder template(String ruleId, String templateCode) {
        return new BindingBuilder(ruleId, templateCode);
    }

    private Map<String, DataTable> tables(DataTable... dataTables) {
        Map<String, DataTable> tables = new LinkedHashMap<>();
        for (DataTable table : dataTables) {
            tables.put(table.getLogicalName(), table);
        }
        return tables;
    }

    private DataTable table(String logicalName, String primaryKeyField, DataRow... rows) {
        DataTable table = new DataTable();
        table.setLogicalName(logicalName);
        table.setHeaders(headers(primaryKeyField, rows));
        table.setRows(Arrays.asList(rows));
        return table;
    }

    private List<String> headers(String primaryKeyField, DataRow[] rows) {
        List<String> headers = new java.util.ArrayList<>();
        headers.add(primaryKeyField);
        for (DataRow row : rows) {
            for (String field : row.getValues().keySet()) {
                if (!headers.contains(field)) {
                    headers.add(field);
                }
            }
        }
        return headers;
    }

    private DataRow row(String primaryKey, String... values) {
        DataRow row = new DataRow();
        row.setPrimaryKey(primaryKey);
        Map<String, String> cells = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            cells.put(values[i], values[i + 1]);
        }
        row.setValues(cells);
        return row;
    }

    private static class BindingBuilder {
        private final RuleBinding binding = new RuleBinding();
        private final Map<String, Object> params = new LinkedHashMap<>();

        BindingBuilder(String ruleId, String templateCode) {
            binding.setRuleId(ruleId);
            binding.setExecutorType("TEMPLATE");
            binding.setTemplateCode(templateCode);
        }

        BindingBuilder param(String key, Object value) {
            params.put(key, value);
            return this;
        }

        RuleBinding build() {
            binding.setTemplateParams(params);
            return binding;
        }
    }
}
