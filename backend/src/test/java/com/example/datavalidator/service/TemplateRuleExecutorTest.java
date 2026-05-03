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
    void rowExpressionTemplateSupportsR006AmountRelationship() {
        DataTable orders = table("t_order", "订单ID",
                row("ORD001", "订单ID", "ORD001", "订单金额", "299", "优惠金额", "30", "实付金额", "269"),
                row("ORD006", "订单ID", "ORD006", "订单金额", "500", "优惠金额", "30", "实付金额", "520"));
        RuleBinding binding = template("R006", "ROW_EXPRESSION")
                .param("tableName", "t_order")
                .param("conditions", Arrays.asList(
                        condition(field("实付金额"), "==", op("-", field("订单金额"), field("优惠金额"))),
                        condition(field("实付金额"), "<=", field("订单金额"))))
                .build();

        List<ValidationFinding> findings = executor.execute(rule("R006", "实付金额与订单金额关系校验"),
                tables(orders), binding);

        assertThat(findings).hasSize(1);
        ValidationFinding finding = findings.get(0);
        assertThat(finding.getRecordKey()).isEqualTo("ORD006");
        assertThat(finding.getFieldName()).isEqualTo("实付金额");
        assertThat(finding.getActualValue()).contains("实付金额=520", "订单金额 - 优惠金额=470");
        assertThat(finding.getExpectedValue()).isEqualTo("实付金额 == 订单金额 - 优惠金额");
        assertThat(finding.getDescription()).isEqualTo("行表达式条件不成立");
    }

    @Test
    void rowExpressionTemplateCanValidateArbitraryAmountFields() {
        DataTable contracts = table("contract_bill", "账单ID",
                row("B001", "账单ID", "B001", "合同金额", "1000", "减免金额", "80", "应收金额", "920"),
                row("B002", "账单ID", "B002", "合同金额", "1000", "减免金额", "80", "应收金额", "950"));
        RuleBinding binding = template("C900", "ROW_EXPRESSION")
                .param("tableName", "contract_bill")
                .param("conditions", Arrays.asList(
                        condition(field("应收金额"), "==", op("-", field("合同金额"), field("减免金额"))),
                        condition(field("应收金额"), "<=", field("合同金额"))))
                .build();

        List<ValidationFinding> findings = executor.execute(rule("C900", "应收金额关系校验"),
                tables(contracts), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("B002");
        assertThat(findings.get(0).getActualValue()).contains("应收金额=950", "合同金额 - 减免金额=920");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("应收金额 == 合同金额 - 减免金额");
    }

    @Test
    void rowExpressionTemplateSupportsConditionalSignedQuantity() {
        DataTable logs = table("t_inventory_log", "流水ID",
                row("LOG001", "流水ID", "LOG001", "变动类型", "出库", "变动数量", "1",
                        "变动前库存", "500", "变动后库存", "499"),
                row("LOG002", "流水ID", "LOG002", "变动类型", "入库", "变动数量", "100",
                        "变动前库存", "800", "变动后库存", "900"),
                row("LOG006", "流水ID", "LOG006", "变动类型", "出库", "变动数量", "1",
                        "变动前库存", "5000", "变动后库存", "4998"));
        RuleBinding binding = template("R015", "ROW_EXPRESSION")
                .param("tableName", "t_inventory_log")
                .param("conditions", Arrays.asList(
                        condition(field("变动后库存"), "==",
                                op("+", field("变动前库存"),
                                        ifNode(condition(field("变动类型"), "==", literal("入库")),
                                                field("变动数量"),
                                                op("-", literal(0), field("变动数量")))))))
                .build();

        List<ValidationFinding> findings = executor.execute(rule("R015", "库存变动连续性校验"),
                tables(logs), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("LOG006");
        assertThat(findings.get(0).getFieldName()).isEqualTo("变动后库存");
        assertThat(findings.get(0).getActualValue()).contains("变动后库存=4998", "变动前库存 +");
        assertThat(findings.get(0).getExpectedValue()).contains("变动后库存 == 变动前库存 +");
    }

    @Test
    void rowExpressionTemplateSupportsWhenInAndNullPredicates() {
        DataTable orders = table("t_order", "订单ID",
                row("ORD001", "订单ID", "ORD001", "订单状态", "待支付", "下单时间", "2026-04-01 09:00:00", "支付时间", ""),
                row("ORD002", "订单ID", "ORD002", "订单状态", "待支付", "下单时间", "2026-04-01 09:00:00", "支付时间", "2026-04-01 09:01:00"),
                row("ORD003", "订单ID", "ORD003", "订单状态", "已支付", "下单时间", "2026-04-02 09:00:00", "支付时间", "2026-04-02 09:01:00"),
                row("ORD004", "订单ID", "ORD004", "订单状态", "已发货", "下单时间", "2026-04-03 09:00:00", "支付时间", "2026-04-03 08:59:00"));
        RuleBinding binding = template("R025", "ROW_EXPRESSION")
                .param("tableName", "t_order")
                .param("conditions", Arrays.asList(
                        when(condition(field("支付时间"), "isNull", null),
                                condition(field("订单状态"), "==", literal("待支付"))),
                        when(condition(field("支付时间"), ">=", field("下单时间")),
                                condition(field("订单状态"), "in", Arrays.asList("已支付", "已发货", "已完成")))))
                .build();

        List<ValidationFinding> findings = executor.execute(rule("R025", "订单状态时间逻辑校验"),
                tables(orders), binding);

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(ValidationFinding::getRecordKey).containsExactly("ORD002", "ORD004");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("支付时间 isNull");
        assertThat(findings.get(1).getExpectedValue()).isEqualTo("支付时间 >= 下单时间");
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
        assertThat(findings.get(0).getActualValue()).isEqualTo("order_item.商品ID=P404");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("product.商品ID 中存在对应记录");
        assertThat(findings.get(0).getDescription()).isEqualTo("关联记录不存在");
    }

    @Test
    void relationExistsTemplateSupportsFilteredExistence() {
        DataTable orders = table("t_order", "订单ID",
                row("ORD001", "订单ID", "ORD001", "订单状态", "已支付"),
                row("ORD002", "订单ID", "ORD002", "订单状态", "已发货"),
                row("ORD003", "订单ID", "ORD003", "订单状态", "待支付"));
        DataTable payments = table("t_payment", "支付ID",
                row("PAY001", "支付ID", "PAY001", "订单ID", "ORD001", "支付状态", "支付成功"),
                row("PAY002", "支付ID", "PAY002", "订单ID", "ORD002", "支付状态", "支付失败"));
        RuleBinding binding = template("R021", "RELATION_EXISTS")
                .param("source", "t_order")
                .param("target", "t_payment")
                .param("keys", Arrays.asList(relationKey("订单ID", "订单ID")))
                .param("sourceWhere", condition(field("订单状态"), "in",
                        Arrays.asList("已支付", "已发货", "已完成")))
                .param("targetWhere", condition(field("支付状态"), "==", literal("支付成功")))
                .param("expectExists", true)
                .build();

        List<ValidationFinding> findings = executor.execute(rule("R021", "订单支付状态一致性"),
                tables(orders, payments), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("ORD002");
        assertThat(findings.get(0).getActualValue()).isEqualTo("t_order.订单ID=ORD002");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("t_payment 中存在匹配记录");
    }

    @Test
    void relationExistsTemplateSupportsAntiExistence() {
        DataTable logs = table("t_inventory_log", "流水ID",
                row("LOG001", "流水ID", "LOG001", "商品ID", "P001", "变动类型", "出库"),
                row("LOG002", "流水ID", "LOG002", "商品ID", "P002", "变动类型", "出库"),
                row("LOG003", "流水ID", "LOG003", "商品ID", "P001", "变动类型", "入库"));
        DataTable products = table("t_product", "商品ID",
                row("P001", "商品ID", "P001", "上架状态", "已下架"),
                row("P002", "商品ID", "P002", "上架状态", "上架"));
        RuleBinding binding = template("R028", "RELATION_EXISTS")
                .param("source", "t_inventory_log")
                .param("target", "t_product")
                .param("keys", Arrays.asList(relationKey("商品ID", "商品ID")))
                .param("sourceWhere", condition(field("变动类型"), "==", literal("出库")))
                .param("targetWhere", condition(field("上架状态"), "==", literal("已下架")))
                .param("expectExists", false)
                .build();

        List<ValidationFinding> findings = executor.execute(rule("R028", "下架商品出库校验"),
                tables(logs, products), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("LOG001");
        assertThat(findings.get(0).getActualValue()).contains("t_inventory_log.商品ID=P001", "t_product.商品ID=P001");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("t_product 中不应存在匹配记录");
    }

    @Test
    void relationExistsTemplateSupportsSourceExistsAndCompositeKeys() {
        DataTable items = table("t_order_item", "明细ID",
                row("ITEM001", "明细ID", "ITEM001", "订单ID", "ORD001", "商品ID", "P001", "数量", "2"),
                row("ITEM002", "明细ID", "ITEM002", "订单ID", "ORD002", "商品ID", "P002", "数量", "1"));
        DataTable orders = table("t_order", "订单ID",
                row("ORD001", "订单ID", "ORD001", "订单状态", "已支付"),
                row("ORD002", "订单ID", "ORD002", "订单状态", "待支付"));
        DataTable logs = table("t_inventory_log", "流水ID",
                row("LOG001", "流水ID", "LOG001", "关联订单ID", "ORD002", "商品ID", "P002",
                        "变动数量", "1", "变动类型", "出库"));
        RuleBinding binding = template("R022", "RELATION_EXISTS")
                .param("source", "t_order_item")
                .param("target", "t_inventory_log")
                .param("keys", Arrays.asList(
                        relationKey("订单ID", "关联订单ID"),
                        relationKey("商品ID", "商品ID"),
                        relationKey("数量", "变动数量")))
                .param("targetWhere", condition(field("变动类型"), "==", literal("出库")))
                .param("sourceExists", sourceExists("t_order",
                        Arrays.asList(relationKey("订单ID", "订单ID")),
                        condition(field("订单状态"), "in", Arrays.asList("已支付", "已发货", "已完成"))))
                .param("expectExists", true)
                .build();

        List<ValidationFinding> findings = executor.execute(rule("R022", "订单库存扣减一致性"),
                tables(items, orders, logs), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("ITEM001");
        assertThat(findings.get(0).getActualValue()).contains("订单ID=ORD001", "商品ID=P001", "数量=2");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("t_inventory_log 中存在匹配记录");
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
        assertThat(findings.get(0).getActualValue()).isEqualTo("payment.用户ID=U999；order.用户ID=U002");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("用户ID == order.用户ID");
        assertThat(findings.get(0).getDescription()).isEqualTo("关联字段值不一致");
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
        assertThat(findings.get(0).getActualValue()).isEqualTo("订单金额=10；order_item.小计金额 汇总=8");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("订单金额 == order_item.小计金额 汇总值");
        assertThat(findings.get(0).getDescription()).isEqualTo("聚合结果不一致");
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
        assertThat(findings.get(0).getActualValue()).isEqualTo("order_item.订单ID=O404；小计金额 汇总=10");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("order.订单ID 中存在聚合目标记录");
        assertThat(findings.get(0).getDescription()).isEqualTo("聚合目标记录不存在");
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
        assertThat(findings.get(0).getActualValue()).isEqualTo("订单ID=O001；支付状态=支付成功");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("唯一组合");
        assertThat(findings.get(0).getDescription()).isEqualTo("存在重复记录");
    }

    @Test
    void duplicateCheckTemplateSupportsWhereFilter() {
        DataTable payments = table("payment", "支付ID",
                row("P001", "支付ID", "P001", "订单ID", "O001", "支付状态", "支付成功"),
                row("P002", "支付ID", "P002", "订单ID", "O001", "支付状态", "支付成功"),
                row("P003", "支付ID", "P003", "订单ID", "O001", "支付状态", "支付失败"));
        RuleBinding binding = template("T006", "DUPLICATE_CHECK")
                .param("tableName", "payment")
                .param("groupBy", Arrays.asList("订单ID"))
                .param("where", condition(field("支付状态"), "==", literal("支付成功")))
                .build();

        List<ValidationFinding> findings = executor.execute(rule("T006", "重复支付检查"), tables(payments), binding);

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(ValidationFinding::getRecordKey).containsExactly("P001", "P002");
        assertThat(findings).extracting(ValidationFinding::getActualValue)
                .allSatisfy(value -> assertThat(value).isEqualTo("订单ID=O001"));
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

    private Map<String, Object> condition(Object left, String operator, Object right) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("left", left);
        condition.put("operator", operator);
        condition.put("right", right);
        return condition;
    }

    private Map<String, Object> when(Map<String, Object> condition, Object predicate) {
        condition.put("when", predicate);
        return condition;
    }

    private Map<String, Object> sourceExists(String target, List<Map<String, Object>> keys, Object targetWhere) {
        Map<String, Object> sourceExists = new LinkedHashMap<>();
        sourceExists.put("target", target);
        sourceExists.put("keys", keys);
        sourceExists.put("targetWhere", targetWhere);
        return sourceExists;
    }

    private Map<String, Object> relationKey(String sourceField, String targetField) {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("sourceField", sourceField);
        key.put("targetField", targetField);
        return key;
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

    private Map<String, Object> ifNode(Object condition, Object thenNode, Object elseNode) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("if", condition);
        expression.put("then", thenNode);
        expression.put("else", elseNode);
        return expression;
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
