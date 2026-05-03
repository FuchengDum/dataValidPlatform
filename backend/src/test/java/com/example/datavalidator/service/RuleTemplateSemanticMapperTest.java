package com.example.datavalidator.service;

import com.example.datavalidator.persistence.RuleDefinitionEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleTemplateSemanticMapperTest {
    private final RuleTemplateSemanticMapper mapper = new RuleTemplateSemanticMapper();

    @Test
    void mapsRequiredFieldsToNotNullTemplate() {
        RuleTemplateSemanticMatch match = mapper.recommend(rule("R001", "订单必填字段",
                "用户ID、订单状态不能为空", "用户ID IS NOT NULL AND 订单状态 IS NOT NULL", "t_order"),
                tables(table("t_order", "订单ID", "用户ID", "订单状态")));

        assertThat(match.isApplicable()).isTrue();
        assertThat(match.getTemplateCode()).isEqualTo("NOT_NULL");
        assertThat(match.getTemplateParams()).containsEntry("tableName", "t_order");
        assertThat(match.getTemplateParams().get("fields")).asList().containsExactly("用户ID", "订单状态");
    }

    @Test
    void mapsAmountRelationshipToRowExpressionWithoutRuleIdSpecialCase() {
        RuleTemplateSemanticMatch match = mapper.recommend(rule("C900", "实付金额与订单金额关系校验",
                "实付金额应等于订单金额减优惠金额，且不得大于订单金额",
                "实付金额 = 订单金额 - 优惠金额 AND 实付金额 <= 订单金额", "t_order"),
                tables(table("t_order", "订单ID", "订单金额", "优惠金额", "实付金额")));

        assertThat(match.isApplicable()).isTrue();
        assertThat(match.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(match.getConfidence()).isEqualTo("HIGH");
        assertThat(match.getTemplateParams()).containsEntry("tableName", "t_order");
        assertThat(match.getTemplateParams().get("conditions")).asList().hasSize(2);
    }

    @Test
    void mapsArbitraryFieldRelationshipToRowExpression() {
        RuleTemplateSemanticMatch match = mapper.recommend(rule("C901", "应收金额关系校验",
                "应收金额应等于合同金额减减免金额，且应收金额不大于合同金额",
                "应收金额 = 合同金额 - 减免金额 AND 应收金额 <= 合同金额", "contract_bill"),
                tables(table("contract_bill", "账单ID", "合同金额", "减免金额", "应收金额")));

        assertThat(match.isApplicable()).isTrue();
        assertThat(match.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(match.getTemplateParams()).containsEntry("tableName", "contract_bill");
        assertThat(match.getTemplateParams().get("conditions")).asList().hasSize(2);
    }

    @Test
    void mapsSignedInventoryContinuityToRowExpression() {
        RuleTemplateSemanticMatch match = mapper.recommend(rule("R015", "库存变动连续性校验",
                "变动后库存 = 变动前库存 + 变动数量(入库为正/出库为负)",
                "SELECT * FROM t_inventory_log WHERE 变动后库存 != 变动前库存 + "
                        + "CASE WHEN 变动类型='入库' THEN 变动数量 ELSE -变动数量 END",
                "t_inventory_log"),
                tables(table("t_inventory_log", "流水ID", "变动类型", "变动数量", "变动前库存", "变动后库存")));

        assertThat(match.isApplicable()).isTrue();
        assertThat(match.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(match.getConfidence()).isEqualTo("HIGH");
        assertThat(match.getTemplateParams()).containsEntry("tableName", "t_inventory_log");
        assertThat(match.getTemplateParams().get("conditions")).asList().hasSize(1);
    }

    @Test
    void mapsCrossTableExistenceToExistsInTableTemplate() {
        RuleTemplateSemanticMatch match = mapper.recommend(rule("R010", "明细商品存在性校验",
                "订单明细商品ID必须存在于商品表", "t_order_item.商品ID exists in t_product.商品ID",
                "t_order_item,t_product"),
                tables(
                        table("t_order_item", "明细ID", "商品ID"),
                        table("t_product", "商品ID", "商品名称")));

        assertThat(match.isApplicable()).isTrue();
        assertThat(match.getTemplateCode()).isEqualTo("EXISTS_IN_TABLE");
        assertThat(match.getTemplateParams()).containsEntry("source", "t_order_item");
        assertThat(match.getTemplateParams()).containsEntry("target", "t_product");
        assertThat(match.getTemplateParams()).containsEntry("key", "商品ID");
    }

    @Test
    void mapsCrossTableFieldEqualityToFieldEqualsTemplate() {
        RuleTemplateSemanticMatch match = mapper.recommend(rule("R011", "支付用户与订单用户一致",
                "支付表用户ID应与订单表用户ID一致", "t_payment.用户ID = t_order.用户ID by 订单ID",
                "t_payment,t_order"),
                tables(
                        table("t_payment", "支付ID", "订单ID", "用户ID"),
                        table("t_order", "订单ID", "用户ID")));

        assertThat(match.isApplicable()).isTrue();
        assertThat(match.getTemplateCode()).isEqualTo("FIELD_EQUALS");
        assertThat(match.getTemplateParams()).containsEntry("source", "t_payment");
        assertThat(match.getTemplateParams()).containsEntry("target", "t_order");
        assertThat(match.getTemplateParams()).containsEntry("key", "订单ID");
        assertThat(match.getTemplateParams()).containsEntry("sourceField", "用户ID");
        assertThat(match.getTemplateParams()).containsEntry("targetField", "用户ID");
    }

    @Test
    void mapsGroupedSumToAggregationEqualsTemplate() {
        RuleTemplateSemanticMatch match = mapper.recommend(rule("R012", "订单金额汇总一致",
                "订单金额应等于订单明细小计金额之和", "sum(t_order_item.小计金额) by 订单ID = t_order.订单金额",
                "t_order_item,t_order"),
                tables(
                        table("t_order_item", "明细ID", "订单ID", "小计金额"),
                        table("t_order", "订单ID", "订单金额")));

        assertThat(match.isApplicable()).isTrue();
        assertThat(match.getTemplateCode()).isEqualTo("AGGREGATION_EQUALS");
        assertThat(match.getTemplateParams()).containsEntry("source", "t_order_item");
        assertThat(match.getTemplateParams()).containsEntry("target", "t_order");
        assertThat(match.getTemplateParams()).containsEntry("groupBy", "订单ID");
        assertThat(match.getTemplateParams()).containsEntry("sum", "小计金额");
        assertThat(match.getTemplateParams()).containsEntry("targetField", "订单金额");
    }

    @Test
    void mapsUniqueCombinationToDuplicateCheckTemplate() {
        RuleTemplateSemanticMatch match = mapper.recommend(rule("R013", "重复支付检查",
                "同一订单ID和支付状态不得重复", "unique(订单ID, 支付状态)", "t_payment"),
                tables(table("t_payment", "支付ID", "订单ID", "支付状态")));

        assertThat(match.isApplicable()).isTrue();
        assertThat(match.getTemplateCode()).isEqualTo("DUPLICATE_CHECK");
        assertThat(match.getTemplateParams()).containsEntry("tableName", "t_payment");
        assertThat(match.getTemplateParams().get("groupBy")).asList().containsExactly("订单ID", "支付状态");
    }

    @Test
    void mapsStageOneRuleSetToExecutableGenericTemplates() {
        Map<String, List<String>> tableFields = case5Tables();
        for (ExpectedMapping expected : stageOneMappings()) {
            RuleTemplateSemanticMatch match = mapper.recommend(expected.rule, tableFields);

            assertThat(match.isApplicable()).as(expected.rule.getRuleId()).isTrue();
            assertThat(match.getTemplateCode()).as(expected.rule.getRuleId()).isEqualTo(expected.templateCode);
        }
    }

    private RuleDefinitionEntity rule(String ruleId, String ruleName, String description,
                                      String pseudoLogic, String applicableTables) {
        RuleDefinitionEntity entity = new RuleDefinitionEntity();
        entity.setDatasetId("ds-1");
        entity.setRuleId(ruleId);
        entity.setRuleName(ruleName);
        entity.setCategory("BUSINESS_RULE");
        entity.setSeverity("CRITICAL");
        entity.setDescription(description);
        entity.setPseudoLogic(pseudoLogic);
        entity.setApplicableTables(applicableTables);
        return entity;
    }

    private Map<String, List<String>> tables(Table table, Table... more) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put(table.name, table.headers);
        for (Table item : more) {
            result.put(item.name, item.headers);
        }
        return result;
    }

    private Table table(String name, String... headers) {
        return new Table(name, Arrays.asList(headers));
    }

    private Map<String, List<String>> case5Tables() {
        return tables(
                table("t_order", "订单ID", "用户ID", "订单状态", "订单金额", "实付金额", "优惠金额",
                        "下单时间", "支付时间", "收货地址"),
                table("t_order_item", "明细ID", "订单ID", "商品ID", "商品名称", "单价", "数量", "小计金额"),
                table("t_product", "商品ID", "商品名称", "商品分类", "成本价", "售价", "库存数量", "上架状态"),
                table("t_payment", "支付ID", "订单ID", "用户ID", "支付方式", "支付金额", "支付状态", "支付时间", "退款金额"),
                table("t_inventory_log", "流水ID", "商品ID", "变动类型", "变动数量", "变动前库存",
                        "变动后库存", "关联订单ID", "操作时间"));
    }

    private List<ExpectedMapping> stageOneMappings() {
        return Arrays.asList(
                expected("R001", "金额非负校验", "订单金额、实付金额、优惠金额均不得为负数",
                        "SELECT * FROM t_order WHERE 订单金额<0 OR 实付金额<0 OR 优惠金额<0", "t_order", "NON_NEGATIVE"),
                expected("R002", "必填字段非空校验", "用户ID、订单状态、下单时间、收货地址为必填字段",
                        "SELECT * FROM t_order WHERE 用户ID IS NULL OR 订单状态 IS NULL OR 下单时间 IS NULL OR 收货地址 IS NULL OR 收货地址=''",
                        "t_order", "NOT_NULL"),
                expected("R003", "金额类型校验", "金额字段必须为数值类型",
                        "SELECT * FROM t_order WHERE ISNUMERIC(订单金额)=0 OR ISNUMERIC(实付金额)=0", "t_order", "NUMERIC_TYPE"),
                expected("R004", "优惠金额合理性校验", "优惠金额不得超过订单金额的50%",
                        "SELECT * FROM t_order WHERE 优惠金额 > 订单金额 * 0.5", "t_order", "ROW_EXPRESSION"),
                expected("R005", "订单金额非零校验", "已支付订单的订单金额不得为0",
                        "SELECT * FROM t_order WHERE 订单金额=0 AND 订单状态='已支付'", "t_order", "ROW_EXPRESSION"),
                expected("R006", "实付金额与订单金额关系校验", "实付金额 = 订单金额 - 优惠金额，且实付金额≤订单金额",
                        "SELECT * FROM t_order WHERE ABS(实付金额 - (订单金额 - 优惠金额)) > 0.01 OR 实付金额 > 订单金额",
                        "t_order", "ROW_EXPRESSION"),
                expected("R007", "商品售价成本价关系校验", "商品售价不得低于成本价",
                        "SELECT * FROM t_product WHERE 售价 < 成本价 AND 上架状态='上架'", "t_product", "ROW_EXPRESSION"),
                expected("R008", "库存非负校验", "商品库存数量不得为负数",
                        "SELECT * FROM t_product WHERE 库存数量 < 0", "t_product", "NON_NEGATIVE"),
                expected("R009", "上架商品库存校验", "上架状态商品库存数量应大于0",
                        "SELECT * FROM t_product WHERE 上架状态='上架' AND 库存数量<=0", "t_product", "ROW_EXPRESSION"),
                expected("R010", "商品价格非零校验", "上架商品的成本价和售价均不得为0",
                        "SELECT * FROM t_product WHERE (成本价=0 OR 售价=0) AND 上架状态='上架'", "t_product", "ROW_EXPRESSION"),
                expected("R011", "明细小计金额校验", "小计金额 = 单价 × 数量",
                        "SELECT * FROM t_order_item WHERE ABS(小计金额 - 单价 * 数量) > 0.01", "t_order_item", "ROW_EXPRESSION"),
                expected("R012", "明细数量校验", "商品数量必须为正整数",
                        "SELECT * FROM t_order_item WHERE 数量<=0 OR 数量!=FLOOR(数量)", "t_order_item", "ROW_EXPRESSION"),
                expected("R013", "支付金额非负校验", "支付金额和退款金额不得为负数",
                        "SELECT * FROM t_payment WHERE 支付金额<0 OR 退款金额<0", "t_payment", "NON_NEGATIVE"),
                expected("R014", "支付金额零值校验", "支付成功的记录支付金额不得为0",
                        "SELECT * FROM t_payment WHERE 支付金额=0 AND 支付状态='支付成功'", "t_payment", "ROW_EXPRESSION"),
                expected("R015", "库存变动连续性校验", "变动后库存 = 变动前库存 + 变动数量(入库为正/出库为负)",
                        "SELECT * FROM t_inventory_log WHERE 变动后库存 != 变动前库存 + CASE WHEN 变动类型='入库' THEN 变动数量 ELSE -变动数量 END",
                        "t_inventory_log", "ROW_EXPRESSION"),
                expected("R016", "入库数量正数校验", "入库变动数量必须为正数，出库变动数量必须为正数",
                        "SELECT * FROM t_inventory_log WHERE (变动类型='入库' AND 变动数量<0) OR (变动类型='出库' AND 变动数量<0)",
                        "t_inventory_log", "ROW_EXPRESSION"),
                expected("R017", "订单-明细金额一致性", "订单金额应等于其所有明细小计金额之和",
                        "SELECT o.订单ID FROM t_order o LEFT JOIN t_order_item i ON o.订单ID=i.订单ID GROUP BY o.订单ID, o.订单金额 HAVING ABS(o.订单金额 - SUM(i.小计金额)) > 0.01",
                        "t_order,t_order_item", "AGGREGATION_EQUALS"),
                expected("R018", "明细-商品关联校验", "明细中的商品ID必须在商品表中存在",
                        "SELECT i.* FROM t_order_item i LEFT JOIN t_product p ON i.商品ID=p.商品ID WHERE p.商品ID IS NULL",
                        "t_order_item,t_product", "EXISTS_IN_TABLE"),
                expected("R019", "明细-商品价格一致性", "明细中的单价应与商品表中的售价一致",
                        "SELECT i.* FROM t_order_item i JOIN t_product p ON i.商品ID=p.商品ID WHERE ABS(i.单价 - p.售价) > 0.01",
                        "t_order_item,t_product", "FIELD_EQUALS"),
                expected("R020", "订单-支付金额一致性", "订单实付金额应等于支付表中对应支付金额之和",
                        "SELECT o.订单ID, o.实付金额, SUM(p.支付金额) AS 已支付 FROM t_order o LEFT JOIN t_payment p ON o.订单ID=p.订单ID GROUP BY o.订单ID, o.实付金额 HAVING ABS(o.实付金额 - SUM(p.支付金额)) > 0.01",
                        "t_order,t_payment", "AGGREGATION_EQUALS"),
                expected("R023", "支付-订单关联存在性", "支付记录中的订单ID必须在订单表中存在",
                        "SELECT p.* FROM t_payment p LEFT JOIN t_order o ON p.订单ID=o.订单ID WHERE o.订单ID IS NULL",
                        "t_payment,t_order", "EXISTS_IN_TABLE"),
                expected("R024", "支付-订单用户一致性", "支付记录中的用户ID应与订单中的用户ID一致",
                        "SELECT p.* FROM t_payment p JOIN t_order o ON p.订单ID=o.订单ID WHERE p.用户ID != o.用户ID",
                        "t_payment,t_order", "FIELD_EQUALS"),
                expected("R025", "订单状态时间逻辑校验", "待支付订单不应有支付时间；已支付/已发货/已完成订单支付时间不得早于下单时间",
                        "SELECT * FROM t_order WHERE (订单状态='待支付' AND 支付时间 IS NOT NULL) OR (订单状态 IN ('已支付','已发货','已完成') AND 支付时间<下单时间)",
                        "t_order", "ROW_EXPRESSION"),
                expected("R027", "库存变动后非负校验", "出库后库存不得为负数",
                        "SELECT * FROM t_inventory_log WHERE 变动后库存 < 0", "t_inventory_log", "NON_NEGATIVE"),
                expected("R029", "同一订单重复支付校验", "同一订单不应有多条支付成功记录(防重复支付)",
                        "SELECT 订单ID, COUNT(*) AS 支付次数 FROM t_payment WHERE 支付状态='支付成功' GROUP BY 订单ID HAVING COUNT(*)>1",
                        "t_payment", "DUPLICATE_CHECK")
        );
    }

    private ExpectedMapping expected(String ruleId, String ruleName, String description,
                                     String pseudoLogic, String applicableTables, String templateCode) {
        return new ExpectedMapping(rule(ruleId, ruleName, description, pseudoLogic, applicableTables), templateCode);
    }

    private static class Table {
        private final String name;
        private final List<String> headers;

        Table(String name, List<String> headers) {
            this.name = name;
            this.headers = headers;
        }
    }

    private static class ExpectedMapping {
        private final RuleDefinitionEntity rule;
        private final String templateCode;

        ExpectedMapping(RuleDefinitionEntity rule, String templateCode) {
            this.rule = rule;
            this.templateCode = templateCode;
        }
    }
}
