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

    private static class Table {
        private final String name;
        private final List<String> headers;

        Table(String name, List<String> headers) {
            this.name = name;
            this.headers = headers;
        }
    }
}
