package com.example.datavalidator.service;

import com.example.datavalidator.persistence.FindingEvidenceEntity;
import com.example.datavalidator.persistence.DataTableSnapshotEntity;
import com.example.datavalidator.persistence.RuleDefinitionEntity;
import com.example.datavalidator.persistence.ValidationFindingEntity;
import com.example.datavalidator.repository.DataTableSnapshotRepository;
import com.example.datavalidator.repository.RuleDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAssistServiceTest {
    private final AiAssistService service = serviceWith(Optional.empty());

    @Test
    void analyzeFindingBuildsBusinessReadableAdvice() {
        ValidationFindingEntity finding = finding();
        FindingEvidenceEntity evidence = new FindingEvidenceEntity();
        evidence.setEvidenceType("FIELD_VALUE");
        evidence.setFieldName("支付金额");
        evidence.setActualValue("-12.50");
        evidence.setExpectedValue(">= 0");

        AiAssistService.AnalysisResult result = service.analyzeFinding(finding, Collections.singletonList(evidence));

        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getReason()).contains("R013").contains("支付金额");
        assertThat(result.getImpact()).contains("资金");
        assertThat(result.getSuggestion()).contains("t_payment").contains("P001");
        assertThat(result.getEvidenceSummary()).contains("FIELD_VALUE").contains("-12.50");
    }

    @Test
    void analyzeFindingUsesModelJsonWhenAvailable() {
        AiAssistService service = serviceWith(Optional.of("{\"reason\":\"模型原因\","
                + "\"impact\":\"模型影响\",\"suggestion\":\"模型建议\",\"evidenceSummary\":\"模型证据\"}"));

        AiAssistService.AnalysisResult result = service.analyzeFinding(finding(), Collections.emptyList());

        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getReason()).isEqualTo("模型原因");
        assertThat(result.getImpact()).isEqualTo("模型影响");
        assertThat(result.getSuggestion()).isEqualTo("模型建议");
        assertThat(result.getEvidenceSummary()).isEqualTo("模型证据");
    }

    @Test
    void draftValidationSqlProducesReadonlySelect() {
        AiAssistService.SqlDraftRequest request = new AiAssistService.SqlDraftRequest();
        request.setTableName("t_payment");
        request.setFieldName("支付金额");
        request.setExpectedValue(">= 0");
        request.setRecordKey("P001");

        AiAssistService.SqlDraftResult result = service.draftValidationSql(request);

        assertThat(result.isExecutable()).isFalse();
        assertThat(result.getSql()).startsWith("SELECT");
        assertThat(result.getSql()).contains("\"t_payment\"").contains("\"支付金额\" < 0");
        assertThat(result.getSql()).doesNotContain("UPDATE").doesNotContain("DELETE");
        assertThat(result.getWarnings()).contains("SQL 草案仅用于人工核查");
    }

    @Test
    void draftValidationSqlUsesModelSelectForManualReview() {
        AiAssistService service = serviceWith(Optional.of("{\"sql\":\"SELECT * FROM t_payment WHERE 订单ID = 'O001'\"}"));
        AiAssistService.SqlDraftRequest request = sqlRequest();
        request.setDraftType("MANUAL_REVIEW");

        AiAssistService.SqlDraftResult result = service.draftValidationSql(request);

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getDraftType()).isEqualTo("MANUAL_REVIEW");
        assertThat(result.getSql()).isEqualTo("SELECT * FROM t_payment WHERE 订单ID = 'O001'");
    }

    @Test
    void draftValidationSqlFallsBackWhenModelReturnsDangerousSql() {
        AiAssistService service = serviceWith(Optional.of("{\"sql\":\"UPDATE t_payment SET 支付金额 = 0\"}"));
        AiAssistService.SqlDraftRequest request = sqlRequest();

        AiAssistService.SqlDraftResult result = service.draftValidationSql(request);

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getSql()).startsWith("SELECT");
        assertThat(result.getWarnings()).contains("模型返回 SQL 未通过只读安全校验，已降级为本地 SQL 草案");
    }

    @Test
    void draftValidationSqlFallsBackWhenModelReturnsMultipleStatements() {
        AiAssistService service = serviceWith(Optional.of(
                "{\"sql\":\"SELECT * FROM t_payment WHERE 订单ID = 'O001'; CALL unsafe_proc()\"}"));
        AiAssistService.SqlDraftRequest request = sqlRequest();

        AiAssistService.SqlDraftResult result = service.draftValidationSql(request);

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getWarnings()).contains("模型返回 SQL 未通过只读安全校验，已降级为本地 SQL 草案");
    }

    @Test
    void draftValidationSqlRejectsDangerousIntent() {
        AiAssistService.SqlDraftRequest request = new AiAssistService.SqlDraftRequest();
        request.setTableName("t_payment");
        request.setFieldName("支付金额");
        request.setExpectedValue(">= 0");
        request.setUserIntent("生成 update 修复 SQL");

        assertThatThrownBy(() -> service.draftValidationSql(request))
                .isInstanceOf(com.example.datavalidator.exception.BadRequestException.class)
                .hasMessageContaining("只允许生成只读 SELECT");
    }

    @Test
    void recommendRuleBindingUsesValidModelRecommendation() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"NOT_NULL\","
                + "\"templateParams\":{\"tableName\":\"t_order\",\"fields\":[\"用户ID\",\"订单状态\"]},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐必填字段\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R002"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("NOT_NULL");
        assertThat(result.getConfidence()).isEqualTo("HIGH");
        assertThat(result.getTemplateParams()).containsEntry("tableName", "t_order");
        assertThat(result.getTemplateParams().get("fields")).asList().containsExactly("用户ID", "订单状态");
        assertThat(result.isRequiresHumanReview()).isTrue();
    }

    @Test
    void recommendRuleBindingFallsBackWhenModelUsesUnknownField() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"NOT_NULL\","
                + "\"templateParams\":{\"tableName\":\"t_order\",\"fields\":[\"不存在字段\"]},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R002"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("NOT_NULL");
        assertThat(result.getWarnings().get(0)).contains("模型推荐校验失败").contains("字段不存在");
    }

    @Test
    void recommendRuleBindingFallsBackWhenModelUsesInvalidCrossTableTemplate() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"FIELD_EQUALS\","
                + "\"templateParams\":{\"source\":\"t_order\",\"target\":\"t_payment\"},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐跨表一致\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R002"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("NOT_NULL");
        assertThat(result.getWarnings().get(0)).contains("模型推荐校验失败");
    }

    @Test
    void recommendRuleBindingKeepsFieldTemplateWhenModelUsesRowExpressionForR002() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"ROW_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_order\",\"conditions\":["
                + "{\"left\":{\"field\":\"用户ID\"},\"operator\":\"isNotNull\"}]},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型误推荐行表达式\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R002"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("NOT_NULL");
        assertThat(result.getConfidence()).isEqualTo("HIGH");
        assertThat(result.getWarnings().get(0)).contains("模型推荐校验失败")
                .contains("模型推荐模板弱化了本地高置信语义映射");
    }

    @Test
    void recommendRuleBindingAcceptsRowExpressionCoveringNotNullForR002() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"ROW_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_order\",\"conditions\":["
                + "{\"left\":{\"field\":\"用户ID\"},\"operator\":\"isNotNull\"},"
                + "{\"left\":{\"field\":\"订单状态\"},\"operator\":\"isNotNull\"},"
                + "{\"left\":{\"field\":\"下单时间\"},\"operator\":\"isNotNull\"},"
                + "{\"left\":{\"field\":\"收货地址\"},\"operator\":\"isNotNull\"},"
                + "{\"left\":{\"field\":\"收货地址\"},\"operator\":\"!=\",\"right\":{\"literal\":\"\"}}]},"
                + "\"confidence\":0.94,\"explanation\":\"模型推荐完整必填行表达式\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R002"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(5);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingAcceptsValidExistsInTableModelRecommendation() {
        AiAssistService service = multiTableRecommendationService("R010", "明细商品存在性校验",
                "订单明细商品ID必须存在于商品表",
                "t_order_item.商品ID exists in t_product.商品ID",
                Optional.of("{\"templateCode\":\"EXISTS_IN_TABLE\","
                        + "\"templateParams\":{\"source\":\"t_order_item\",\"target\":\"t_product\","
                        + "\"key\":\"商品ID\"},"
                        + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐跨表存在性\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R010"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getTemplateCode()).isEqualTo("EXISTS_IN_TABLE");
        assertThat(result.getTemplateParams()).containsEntry("source", "t_order_item");
        assertThat(result.getTemplateParams()).containsEntry("target", "t_product");
        assertThat(result.getTemplateParams()).containsEntry("key", "商品ID");
    }

    @Test
    void recommendRuleBindingAcceptsValidFieldEqualsModelRecommendation() {
        AiAssistService service = multiTableRecommendationService("R011", "支付用户与订单用户一致",
                "支付表用户ID应与订单表用户ID一致",
                "t_payment.用户ID = t_order.用户ID by 订单ID",
                Optional.of("{\"templateCode\":\"JOIN_ASSERT\","
                        + "\"templateParams\":{\"source\":\"t_payment\",\"target\":\"t_order\","
                        + "\"keys\":[{\"sourceField\":\"订单ID\",\"targetField\":\"订单ID\"}],"
                        + "\"assert\":{\"left\":{\"sourceField\":\"用户ID\"},\"op\":\"==\","
                        + "\"right\":{\"targetField\":\"用户ID\"}}},"
                        + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐跨表字段一致\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R011"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getTemplateCode()).isEqualTo("JOIN_ASSERT");
        assertThat(result.getTemplateParams().get("keys")).asList().hasSize(1);
        assertThat(result.getTemplateParams().get("assert")).asString().contains("用户ID");
    }

    @Test
    void recommendRuleBindingNormalizesFieldEqualsForR024() {
        AiAssistService service = paymentOrderRecommendationService("R024", "支付用户与订单用户一致性",
                "支付表用户ID应与订单表用户ID一致",
                "t_payment.用户ID = t_order.用户ID by 订单ID",
                Optional.of("{\"templateCode\":\"FIELD_EQUALS\","
                        + "\"templateParams\":{\"source\":\"t_payment\",\"target\":\"t_order\","
                        + "\"key\":\"订单ID\",\"sourceField\":\"用户ID\",\"targetField\":\"用户ID\"},"
                        + "\"confidence\":0.98,\"explanation\":\"模型推荐旧字段一致模板\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R024"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("JOIN_ASSERT");
        assertThat(result.getTemplateParams().get("keys")).asList().hasSize(1);
        assertThat(result.getTemplateParams().get("assert")).asString().contains("用户ID");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingAcceptsLiteralListRowExpressionForR025() {
        AiAssistService service = orderStatusRecommendationService("R025", "订单状态时间逻辑校验",
                "待支付订单不应有支付时间；已支付/已发货/已完成订单支付时间不得早于下单时间",
                "SELECT * FROM t_order WHERE (订单状态='待支付' AND 支付时间 IS NOT NULL) "
                        + "OR (订单状态 IN ('已支付','已发货','已完成') AND 支付时间<下单时间)",
                Optional.of("{\"templateCode\":\"ROW_EXPRESSION\","
                        + "\"templateParams\":{\"tableName\":\"t_order\",\"conditions\":["
                        + "{\"left\":{\"field\":\"支付时间\"},\"operator\":\"isNull\","
                        + "\"when\":{\"left\":{\"field\":\"订单状态\"},\"operator\":\"==\","
                        + "\"right\":{\"literal\":\"待支付\"}}},"
                        + "{\"left\":{\"field\":\"支付时间\"},\"operator\":\">=\","
                        + "\"right\":{\"field\":\"下单时间\"},"
                        + "\"when\":{\"left\":{\"field\":\"订单状态\"},\"operator\":\"in\","
                        + "\"right\":{\"literal\":[\"已支付\",\"已发货\",\"已完成\"]}}}]},"
                        + "\"confidence\":0.96,\"explanation\":\"模型推荐订单状态时间逻辑\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R025"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(2);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingNormalizesReversedRelationExistsForR026() {
        AiAssistService service = paymentOrderRecommendationService("R026", "订单状态流转校验",
                "已取消订单不应有支付成功记录，除非退款金额等于支付金额",
                "SELECT o.* FROM t_order o JOIN t_payment p ON o.订单ID=p.订单ID "
                        + "WHERE o.订单状态='已取消' AND p.退款金额=0 AND p.支付状态='支付成功'",
                Optional.of("{\"templateCode\":\"RELATION_EXISTS\","
                        + "\"templateParams\":{\"source\":\"t_order\",\"target\":\"t_payment\","
                        + "\"keys\":[{\"sourceField\":\"订单ID\",\"targetField\":\"订单ID\"}],"
                        + "\"expectExists\":false,"
                        + "\"sourceWhere\":{\"left\":{\"field\":\"订单状态\"},\"operator\":\"==\","
                        + "\"right\":{\"literal\":\"已取消\"}},"
                        + "\"targetWhere\":{\"and\":["
                        + "{\"left\":{\"field\":\"支付状态\"},\"operator\":\"==\","
                        + "\"right\":{\"literal\":\"支付成功\"}},"
                        + "{\"left\":{\"field\":\"退款金额\"},\"operator\":\"==\","
                        + "\"right\":{\"literal\":0}}]}},"
                        + "\"confidence\":0.96,\"explanation\":\"模型推荐取消订单支付反向存在性\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R026"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("RELATION_EXISTS");
        assertThat(result.getTemplateParams()).containsEntry("source", "t_payment");
        assertThat(result.getTemplateParams()).containsEntry("target", "t_order");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingAcceptsValidAggregationModelRecommendation() {
        AiAssistService service = multiTableRecommendationService("R012", "订单金额汇总一致",
                "订单金额应等于订单明细小计金额之和",
                "sum(t_order_item.小计金额) by 订单ID = t_order.订单金额",
                Optional.of("{\"templateCode\":\"AGGREGATE_ASSERT\","
                        + "\"templateParams\":{\"source\":\"t_order_item\",\"target\":\"t_order\","
                        + "\"groupBy\":[{\"sourceField\":\"订单ID\",\"targetField\":\"订单ID\"}],"
                        + "\"aggregate\":{\"fn\":\"SUM\",\"field\":\"小计金额\"},"
                        + "\"assert\":{\"op\":\"==\",\"targetField\":\"订单金额\",\"tolerance\":0.01}},"
                        + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐聚合一致\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R012"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getTemplateCode()).isEqualTo("AGGREGATE_ASSERT");
        assertThat(result.getTemplateParams().get("aggregate")).asString().contains("小计金额");
        assertThat(result.getTemplateParams().get("assert")).asString().contains("订单金额");
    }

    @Test
    void recommendRuleBindingNormalizesAggregationEqualsForR017() {
        AiAssistService service = multiTableRecommendationService("R017", "订单-明细金额一致性",
                "订单金额应等于其所有明细小计金额之和",
                "SELECT o.订单ID FROM t_order o LEFT JOIN t_order_item i ON o.订单ID=i.订单ID "
                        + "GROUP BY o.订单ID, o.订单金额 HAVING ABS(o.订单金额 - SUM(i.小计金额)) > 0.01",
                Optional.of("{\"templateCode\":\"AGGREGATION_EQUALS\","
                        + "\"templateParams\":{\"source\":\"t_order_item\",\"target\":\"t_order\","
                        + "\"groupBy\":\"订单ID\",\"sum\":\"小计金额\","
                        + "\"targetField\":\"订单金额\",\"targetKey\":\"订单ID\"},"
                        + "\"confidence\":0.98,\"explanation\":\"模型推荐旧聚合相等模板\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R017"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("AGGREGATE_ASSERT");
        assertThat(result.getTemplateParams().get("groupBy")).asList().hasSize(1);
        assertThat(result.getTemplateParams().get("aggregate")).asString().contains("SUM", "小计金额");
        assertThat(result.getTemplateParams().get("assert")).asString().contains("订单金额");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingNormalizesAggregationEqualsForR020() {
        AiAssistService service = multiTableRecommendationService("R020", "订单-支付金额一致性",
                "订单实付金额应等于支付表中对应支付金额之和",
                "SELECT o.订单ID, o.实付金额, SUM(p.支付金额) AS 已支付 FROM t_order o "
                        + "LEFT JOIN t_payment p ON o.订单ID=p.订单ID GROUP BY o.订单ID, o.实付金额 "
                        + "HAVING ABS(o.实付金额 - SUM(p.支付金额)) > 0.01",
                Optional.of("{\"templateCode\":\"AGGREGATION_EQUALS\","
                        + "\"templateParams\":{\"source\":\"t_payment\",\"target\":\"t_order\","
                        + "\"groupBy\":\"订单ID\",\"sum\":\"支付金额\","
                        + "\"targetField\":\"实付金额\",\"targetKey\":\"订单ID\"},"
                        + "\"confidence\":0.98,\"explanation\":\"模型推荐旧聚合相等模板\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R020"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("AGGREGATE_ASSERT");
        assertThat(result.getTemplateParams().get("groupBy")).asList().hasSize(1);
        assertThat(result.getTemplateParams().get("aggregate")).asString().contains("SUM", "支付金额");
        assertThat(result.getTemplateParams().get("assert")).asString().contains("实付金额");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingAcceptsValidDuplicateAssertModelRecommendation() {
        AiAssistService service = multiTableRecommendationService("R013", "重复支付检查",
                "同一订单ID和支付状态不得重复",
                "unique(订单ID, 支付状态)",
                Optional.of("{\"templateCode\":\"DUPLICATE_ASSERT\","
                        + "\"templateParams\":{\"table\":\"t_payment\","
                        + "\"groupBy\":[\"订单ID\",\"支付状态\"],"
                        + "\"assert\":{\"op\":\"<=\",\"count\":1}},"
                        + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐重复检查\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R013"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getTemplateCode()).isEqualTo("DUPLICATE_ASSERT");
        assertThat(result.getTemplateParams().get("groupBy")).asList().containsExactly("订单ID", "支付状态");
        assertThat(result.getTemplateParams().get("assert")).asString().contains("<=", "1");
    }

    @Test
    void recommendRuleBindingAcceptsRelationExistsValueNodesForR021() {
        AiAssistService service = multiTableRecommendationService("R021", "订单-支付状态一致性",
                "已支付/已发货/已完成订单必须有支付成功记录；已取消订单不应有支付成功记录(除非全额退款)",
                "SELECT o.* FROM t_order o WHERE o.订单状态 IN ('已支付','已发货','已完成') "
                        + "AND NOT EXISTS(SELECT 1 FROM t_payment p WHERE p.订单ID=o.订单ID "
                        + "AND p.支付状态='支付成功')",
                Optional.of("{\"templateCode\":\"RELATION_EXISTS\","
                        + "\"templateParams\":{\"source\":\"t_order\",\"target\":\"t_payment\","
                        + "\"keys\":[{\"sourceField\":\"订单ID\",\"targetField\":\"订单ID\"}],"
                        + "\"expectExists\":true,"
                        + "\"sourceWhere\":{\"left\":{\"field\":\"订单状态\"},\"operator\":\"in\","
                        + "\"right\":{\"value\":[\"已支付\",\"已发货\",\"已完成\"]}},"
                        + "\"targetWhere\":{\"left\":{\"field\":\"支付状态\"},\"operator\":\"==\","
                        + "\"right\":{\"value\":\"支付成功\"}}},"
                        + "\"confidence\":0.93,\"explanation\":\"模型推荐订单支付状态存在性\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R021"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("RELATION_EXISTS");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingNormalizesRelationExistsPredicateAndCompletesR022() {
        AiAssistService service = multiTableRecommendationService("R022", "订单-库存扣减一致性",
                "已支付订单的每条明细应有对应的库存出库记录，出库数量应与明细数量一致",
                "SELECT i.* FROM t_order_item i JOIN t_order o ON i.订单ID=o.订单ID "
                        + "WHERE o.订单状态 IN ('已支付','已发货','已完成') "
                        + "AND NOT EXISTS(SELECT 1 FROM t_inventory_log l WHERE l.关联订单ID=i.订单ID "
                        + "AND l.商品ID=i.商品ID AND l.变动数量=i.数量)",
                Optional.of("{\"templateCode\":\"RELATION_EXISTS\","
                        + "\"templateParams\":{\"source\":\"t_order_item\",\"target\":\"t_inventory_log\","
                        + "\"keys\":[{\"sourceField\":\"订单ID\",\"targetField\":\"关联订单ID\"},"
                        + "{\"sourceField\":\"商品ID\",\"targetField\":\"商品ID\"},"
                        + "{\"sourceField\":\"数量\",\"targetField\":\"变动数量\"}],"
                        + "\"expectExists\":true,"
                        + "\"sourceExists\":{\"target\":\"t_order\","
                        + "\"keys\":[{\"sourceField\":\"订单ID\",\"targetField\":\"订单ID\"}],"
                        + "\"targetWhere\":{\"field\":\"订单状态\",\"operator\":\"in\","
                        + "\"value\":[\"已支付\",\"已发货\",\"已完成\"]}}},"
                        + "\"confidence\":0.96,\"explanation\":\"模型推荐库存扣减存在性\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R022"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("RELATION_EXISTS");
        assertThat(result.getTemplateParams().get("targetWhere")).asString().contains("变动类型", "出库");
        assertThat(result.getTemplateParams().get("sourceExists")).asString().contains("订单状态", "已支付");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingFallsBackToFieldExpressionWhenModelWeakensR006() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"NUMERIC_TYPE\","
                + "\"templateParams\":{\"tableName\":\"t_order\",\"fields\":[\"订单金额\",\"实付金额\"]},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐金额类型\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R006"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams()).containsEntry("tableName", "t_order");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(2);
        assertThat(result.getConfidence()).isEqualTo("HIGH");
        assertThat(result.getWarnings().get(0)).contains("模型推荐校验失败")
                .contains("模型推荐模板弱化了本地高置信语义映射");
    }

    @Test
    void recommendRuleBindingUsesValidRowExpressionModelRecommendationForR006() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"ROW_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_order\","
                + "\"conditions\":["
                + "{\"left\":{\"field\":\"实付金额\"},\"operator\":\"==\","
                + "\"right\":{\"op\":\"-\",\"left\":{\"field\":\"订单金额\"},\"right\":{\"field\":\"优惠金额\"}}},"
                + "{\"left\":{\"field\":\"实付金额\"},\"operator\":\"<=\",\"right\":{\"field\":\"订单金额\"}}]},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐金额关系表达式\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R006"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(2);
    }

    @Test
    void recommendRuleBindingAcceptsSingleRowExpressionConditionObjectForR004() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"ROW_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_order\","
                + "\"conditions\":{\"left\":{\"field\":\"优惠金额\"},\"operator\":\"<=\","
                + "\"right\":{\"op\":\"*\",\"left\":{\"field\":\"订单金额\"},\"right\":{\"literal\":0.5}}}},"
                + "\"confidence\":0.98,\"explanation\":\"模型推荐优惠金额比例约束\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R004"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(1);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingAcceptsConditionalRowExpressionForR009() {
        AiAssistService service = productRecommendationService("R009", "上架商品库存校验",
                "上架状态商品库存数量应大于0",
                "SELECT * FROM t_product WHERE 上架状态='上架' AND 库存数量<=0",
                Optional.of("{\"templateCode\":\"ROW_EXPRESSION\","
                        + "\"templateParams\":{\"tableName\":\"t_product\","
                        + "\"conditions\":[{\"left\":{\"field\":\"库存数量\"},\"operator\":\">\","
                        + "\"right\":{\"literal\":0},\"when\":{\"left\":{\"field\":\"上架状态\"},"
                        + "\"operator\":\"==\",\"right\":{\"literal\":\"上架\"}}}]},"
                        + "\"confidence\":0.98,\"explanation\":\"模型推荐上架库存约束\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R009"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(1);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingNormalizesEquivalentFieldExpressionModelRecommendationForR006() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"FIELD_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_order\","
                + "\"expression\":\"实付金额 == 订单金额 - 优惠金额 && 实付金额 <= 订单金额\"},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐金额关系表达式\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R006"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(2);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingNormalizesAbsFieldExpressionModelRecommendationForR006() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"FIELD_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_order\","
                + "\"expression\":\"ABS(实付金额 - (订单金额 - 优惠金额)) <= 0.01 && 实付金额 <= 订单金额\"},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐金额关系表达式\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R006"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(2);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingNormalizesAbsFieldExpressionWithoutRightParenthesesForR011() {
        AiAssistService service = orderItemRecommendationService(Optional.of("{\"templateCode\":\"FIELD_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_order_item\","
                + "\"expression\":\"ABS(小计金额 - 单价 * 数量) <= 0.01\"},"
                + "\"confidence\":0.97,\"explanation\":\"模型推荐明细金额计算关系\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R011"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(1);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingFallsBackWhenR006ModelExpressionMissesRequiredCondition() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"ROW_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_order\","
                + "\"conditions\":[{\"left\":{\"field\":\"实付金额\"},\"operator\":\"<=\","
                + "\"right\":{\"field\":\"订单金额\"}}]},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐弱金额关系\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R006"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(2);
        assertThat(result.getWarnings().get(0)).contains("模型推荐校验失败")
                .contains("模型行表达式未覆盖本地语义映射条件");
    }

    @Test
    void recommendRuleBindingFallsBackToRowExpressionWhenModelWeakensR015() {
        AiAssistService service = inventoryRecommendationService(Optional.of("{\"templateCode\":\"NUMERIC_TYPE\","
                + "\"templateParams\":{\"tableName\":\"t_inventory_log\","
                + "\"fields\":[\"变动类型\",\"变动数量\",\"变动前库存\",\"变动后库存\"]},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐字段类型检查\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R015"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams()).containsEntry("tableName", "t_inventory_log");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(1);
        assertThat(result.getWarnings().get(0)).contains("模型推荐校验失败")
                .contains("模型推荐模板弱化了本地高置信语义映射");
    }

    @Test
    void recommendRuleBindingAcceptsConditionalRowExpressionForR015() {
        AiAssistService service = inventoryRecommendationService(Optional.of("{\"templateCode\":\"ROW_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_inventory_log\","
                + "\"conditions\":[{\"left\":{\"field\":\"变动后库存\"},\"operator\":\"==\","
                + "\"right\":{\"op\":\"+\",\"left\":{\"field\":\"变动前库存\"},"
                + "\"right\":{\"if\":{\"left\":{\"field\":\"变动类型\"},\"operator\":\"==\","
                + "\"right\":{\"literal\":\"入库\"}},\"then\":{\"field\":\"变动数量\"},"
                + "\"else\":{\"op\":\"-\",\"left\":{\"literal\":0},\"right\":{\"field\":\"变动数量\"}}}}}]},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐库存连续性行表达式\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R015"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(1);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingNormalizesFieldExpressionObjectForR015() {
        AiAssistService service = inventoryRecommendationService(Optional.of("{\"templateCode\":\"FIELD_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_inventory_log\","
                + "\"expression\":{\"left\":{\"field\":\"变动后库存\"},\"operator\":\"==\","
                + "\"right\":{\"op\":\"+\",\"left\":{\"field\":\"变动前库存\"},"
                + "\"right\":{\"if\":{\"left\":{\"field\":\"变动类型\"},\"operator\":\"==\","
                + "\"right\":{\"literal\":\"入库\"}},\"then\":{\"field\":\"变动数量\"},"
                + "\"else\":{\"op\":\"-\",\"left\":{\"literal\":0},\"right\":{\"field\":\"变动数量\"}}}}}},"
                + "\"confidence\":0.96,\"explanation\":\"模型用 FIELD_EXPRESSION 返回结构化条件对象\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R015"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(1);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingAcceptsSplitEqualityConditionsForR016() {
        AiAssistService service = inventoryRecommendationService("R016", "入库数量正数校验",
                "入库变动数量必须为正数，出库变动数量必须为正数",
                "SELECT * FROM t_inventory_log WHERE (变动类型='入库' AND 变动数量<0) "
                        + "OR (变动类型='出库' AND 变动数量<0)",
                Optional.of("{\"templateCode\":\"ROW_EXPRESSION\","
                        + "\"templateParams\":{\"tableName\":\"t_inventory_log\","
                        + "\"conditions\":["
                        + "{\"left\":{\"field\":\"变动数量\"},\"operator\":\">\",\"right\":{\"literal\":0},"
                        + "\"when\":{\"left\":{\"field\":\"变动类型\"},\"operator\":\"==\","
                        + "\"right\":{\"literal\":\"入库\"}}},"
                        + "{\"left\":{\"field\":\"变动数量\"},\"operator\":\">\",\"right\":{\"literal\":0},"
                        + "\"when\":{\"left\":{\"field\":\"变动类型\"},\"operator\":\"==\","
                        + "\"right\":{\"literal\":\"出库\"}}}]},"
                        + "\"confidence\":0.98,\"explanation\":\"模型推荐入库和出库数量正数校验\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R016"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getSource()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(2);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingFallsBackWhenModelExpressionIsNotExecutable() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"FIELD_EXPRESSION\","
                + "\"templateParams\":{\"tableName\":\"t_order\","
                + "\"expression\":\"用户ID 订单状态\"},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐非法表达式\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R002"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("NOT_NULL");
        assertThat(result.getWarnings().get(0)).contains("模型推荐校验失败");
    }

    @Test
    void recommendRuleBindingUsesLocalRecommendationWhenModelUnavailable() {
        AiAssistService service = recommendationService(Optional.empty());

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R002"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("NOT_NULL");
        assertThat(result.getConfidence()).isEqualTo("HIGH");
        assertThat(result.getTemplateParams()).containsEntry("tableName", "t_order");
        assertThat(result.getTemplateParams().get("fields")).asList().contains("用户ID", "订单状态");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void recommendRuleBindingUsesLocalFieldExpressionForR006WhenModelUnavailable() {
        AiAssistService service = recommendationService(Optional.empty());

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R006"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("ROW_EXPRESSION");
        assertThat(result.getConfidence()).isEqualTo("HIGH");
        assertThat(result.getTemplateParams()).containsEntry("tableName", "t_order");
        assertThat(result.getTemplateParams().get("conditions")).asList().hasSize(2);
    }

    @Test
    void recommendRuleBindingRejectsUnknownRule() {
        RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
        DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
        AiAssistService service = new AiAssistService((systemPrompt, userPrompt) -> Optional.empty(),
                new ObjectMapper(), ruleRepository, tableRepository);
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("missing", "ds-1"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recommendRuleBinding(recommendationRequest("ds-1", "missing")))
                .isInstanceOf(com.example.datavalidator.exception.BadRequestException.class)
                .hasMessageContaining("规则不存在");
    }

    private ValidationFindingEntity finding() {
        ValidationFindingEntity finding = new ValidationFindingEntity();
        finding.setFindingId("f001");
        finding.setRuleId("R013");
        finding.setRuleName("支付金额非负");
        finding.setRuleCategory("SINGLE_FIELD_CONSTRAINT");
        finding.setSeverity("CRITICAL");
        finding.setTableName("t_payment");
        finding.setRecordKey("P001");
        finding.setFieldName("支付金额");
        finding.setActualValue("-12.50");
        finding.setExpectedValue(">= 0");
        finding.setDescription("支付金额不得为负数");
        return finding;
    }

    private AiAssistService.SqlDraftRequest sqlRequest() {
        AiAssistService.SqlDraftRequest request = new AiAssistService.SqlDraftRequest();
        request.setTableName("t_payment");
        request.setFieldName("支付金额");
        request.setExpectedValue(">= 0");
        request.setRecordKey("P001");
        return request;
    }

    private AiAssistService serviceWith(Optional<String> modelResponse) {
        return new AiAssistService((systemPrompt, userPrompt) -> modelResponse, new ObjectMapper());
    }

    private AiAssistService recommendationService(Optional<String> modelResponse) {
        RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
        DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
        RuleDefinitionEntity rule = rule("ds-1", "R002", "订单必填字段", "NOT_NULL");
        RuleDefinitionEntity r006 = rule("ds-1", "R006", "实付金额与订单金额关系校验", "");
        r006.setCategory("SINGLE_TABLE_BUSINESS_RULE");
        r006.setDescription("实付金额应等于订单金额减优惠金额，且不得大于订单金额");
        r006.setPseudoLogic("实付金额 = 订单金额 - 优惠金额 AND 实付金额 <= 订单金额");
        RuleDefinitionEntity r004 = rule("ds-1", "R004", "优惠金额合理性校验", "");
        r004.setCategory("SINGLE_TABLE_BUSINESS_RULE");
        r004.setDescription("优惠金额不得超过订单金额的50%");
        r004.setPseudoLogic("优惠金额 <= 订单金额 * 0.5");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("R002", "ds-1"))).thenReturn(Optional.of(rule));
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("R004", "ds-1"))).thenReturn(Optional.of(r004));
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("R006", "ds-1"))).thenReturn(Optional.of(r006));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_order", "订单ID", "用户ID", "订单状态", "下单时间",
                        "收货地址", "订单金额", "优惠金额", "实付金额")));
        return new AiAssistService((systemPrompt, userPrompt) -> modelResponse,
                new ObjectMapper(), ruleRepository, tableRepository);
    }

    private AiAssistService orderItemRecommendationService(Optional<String> modelResponse) {
        RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
        DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
        RuleDefinitionEntity rule = rule("ds-1", "R011", "明细小计金额校验", "");
        rule.setCategory("SINGLE_TABLE_BUSINESS_RULE");
        rule.setApplicableTables("t_order_item");
        rule.setDescription("小计金额应等于单价乘以数量");
        rule.setPseudoLogic("SELECT * FROM t_order_item WHERE ABS(小计金额 - 单价 * 数量) > 0.01");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("R011", "ds-1"))).thenReturn(Optional.of(rule));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(List.of(
                table("ds-1", "t_order_item", "明细ID", "订单ID", "商品ID", "单价", "数量", "小计金额")));
        return new AiAssistService((systemPrompt, userPrompt) -> modelResponse,
                new ObjectMapper(), ruleRepository, tableRepository);
    }

    private AiAssistService orderStatusRecommendationService(String ruleId, String ruleName,
                                                             String description, String pseudoLogic,
                                                             Optional<String> modelResponse) {
        RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
        DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
        RuleDefinitionEntity rule = rule("ds-1", ruleId, ruleName, "");
        rule.setCategory("SINGLE_TABLE_BUSINESS_RULE");
        rule.setApplicableTables("t_order");
        rule.setDescription(description);
        rule.setPseudoLogic(pseudoLogic);
        when(ruleRepository.findById(new RuleDefinitionEntity.Key(ruleId, "ds-1"))).thenReturn(Optional.of(rule));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(List.of(
                table("ds-1", "t_order", "订单ID", "用户ID", "订单状态", "下单时间", "支付时间",
                        "订单金额", "实付金额", "优惠金额")));
        return new AiAssistService((systemPrompt, userPrompt) -> modelResponse,
                new ObjectMapper(), ruleRepository, tableRepository);
    }

    private AiAssistService paymentOrderRecommendationService(String ruleId, String ruleName,
                                                              String description, String pseudoLogic,
                                                              Optional<String> modelResponse) {
        RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
        DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
        RuleDefinitionEntity rule = rule("ds-1", ruleId, ruleName, "");
        rule.setCategory("CROSS_TABLE_BUSINESS_RULE");
        rule.setDescription(description);
        rule.setPseudoLogic(pseudoLogic);
        rule.setApplicableTables("t_payment,t_order");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key(ruleId, "ds-1"))).thenReturn(Optional.of(rule));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_payment", "支付ID", "订单ID", "用户ID", "支付状态", "支付金额", "退款金额"),
                table("ds-1", "t_order", "订单ID", "用户ID", "订单状态", "订单金额", "实付金额")));
        return new AiAssistService((systemPrompt, userPrompt) -> modelResponse,
                new ObjectMapper(), ruleRepository, tableRepository);
    }

    private AiAssistService multiTableRecommendationService(String ruleId, String ruleName,
                                                            String description, String pseudoLogic,
                                                            Optional<String> modelResponse) {
        RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
        DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
        RuleDefinitionEntity rule = rule("ds-1", ruleId, ruleName, "");
        rule.setCategory("CROSS_TABLE_BUSINESS_RULE");
        rule.setDescription(description);
        rule.setPseudoLogic(pseudoLogic);
        rule.setApplicableTables("t_order_item,t_product,t_payment,t_order,t_inventory_log");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key(ruleId, "ds-1"))).thenReturn(Optional.of(rule));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_order_item", "明细ID", "订单ID", "商品ID", "数量", "小计金额"),
                table("ds-1", "t_product", "商品ID", "商品名称"),
                table("ds-1", "t_payment", "支付ID", "订单ID", "用户ID", "支付状态", "支付金额"),
                table("ds-1", "t_order", "订单ID", "用户ID", "订单状态", "订单金额", "实付金额"),
                table("ds-1", "t_inventory_log", "流水ID", "关联订单ID", "商品ID", "变动类型", "变动数量")));
        return new AiAssistService((systemPrompt, userPrompt) -> modelResponse,
                new ObjectMapper(), ruleRepository, tableRepository);
    }

    private AiAssistService productRecommendationService(String ruleId, String ruleName,
                                                         String description, String pseudoLogic,
                                                         Optional<String> modelResponse) {
        RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
        DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
        RuleDefinitionEntity rule = rule("ds-1", ruleId, ruleName, "");
        rule.setCategory("SINGLE_TABLE_BUSINESS_RULE");
        rule.setApplicableTables("t_product");
        rule.setDescription(description);
        rule.setPseudoLogic(pseudoLogic);
        when(ruleRepository.findById(new RuleDefinitionEntity.Key(ruleId, "ds-1"))).thenReturn(Optional.of(rule));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_product", "商品ID", "商品名称", "商品分类", "成本价", "售价", "库存数量", "上架状态")));
        return new AiAssistService((systemPrompt, userPrompt) -> modelResponse,
                new ObjectMapper(), ruleRepository, tableRepository);
    }

    private AiAssistService inventoryRecommendationService(Optional<String> modelResponse) {
        return inventoryRecommendationService("R015", "库存变动连续性校验",
                "变动后库存 = 变动前库存 + 变动数量(入库为正/出库为负)",
                "SELECT * FROM t_inventory_log WHERE 变动后库存 != 变动前库存 + "
                        + "CASE WHEN 变动类型='入库' THEN 变动数量 ELSE -变动数量 END",
                modelResponse);
    }

    private AiAssistService inventoryRecommendationService(String ruleId, String ruleName,
                                                           String description, String pseudoLogic,
                                                           Optional<String> modelResponse) {
        RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
        DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
        RuleDefinitionEntity rule = rule("ds-1", ruleId, ruleName, "");
        rule.setCategory("SINGLE_TABLE_BUSINESS_RULE");
        rule.setApplicableTables("t_inventory_log");
        rule.setDescription(description);
        rule.setPseudoLogic(pseudoLogic);
        when(ruleRepository.findById(new RuleDefinitionEntity.Key(ruleId, "ds-1"))).thenReturn(Optional.of(rule));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_inventory_log", "流水ID", "变动类型", "变动数量", "变动前库存", "变动后库存")));
        return new AiAssistService((systemPrompt, userPrompt) -> modelResponse,
                new ObjectMapper(), ruleRepository, tableRepository);
    }

    private AiAssistService.RuleBindingRecommendationRequest recommendationRequest(String datasetId, String ruleId) {
        AiAssistService.RuleBindingRecommendationRequest request = new AiAssistService.RuleBindingRecommendationRequest();
        request.setDatasetId(datasetId);
        request.setRuleId(ruleId);
        return request;
    }

    private RuleDefinitionEntity rule(String datasetId, String ruleId, String ruleName, String templateCode) {
        RuleDefinitionEntity entity = new RuleDefinitionEntity();
        entity.setDatasetId(datasetId);
        entity.setRuleId(ruleId);
        entity.setRuleName(ruleName);
        entity.setCategory("SINGLE_FIELD_CONSTRAINT");
        entity.setSeverity("CRITICAL");
        entity.setApplicableTables("t_order");
        entity.setDescription("用户ID、订单状态、下单时间、收货地址不能为空");
        entity.setPseudoLogic("用户ID IS NOT NULL AND 订单状态 IS NOT NULL");
        entity.setTemplateCode(templateCode);
        return entity;
    }

    private DataTableSnapshotEntity table(String datasetId, String logicalName, String... headers) {
        DataTableSnapshotEntity entity = new DataTableSnapshotEntity();
        entity.setId("tbl-" + logicalName);
        entity.setDatasetId(datasetId);
        entity.setLogicalName(logicalName);
        entity.setSourceType("EXCEL");
        entity.setHeadersJson(writeJson(Arrays.asList(headers)));
        return entity;
    }

    private String writeJson(List<String> values) {
        try {
            return new ObjectMapper().writeValueAsString(values);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
