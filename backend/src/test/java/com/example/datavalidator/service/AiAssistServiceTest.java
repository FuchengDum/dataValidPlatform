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
                Optional.of("{\"templateCode\":\"FIELD_EQUALS\","
                        + "\"templateParams\":{\"source\":\"t_payment\",\"target\":\"t_order\","
                        + "\"key\":\"订单ID\",\"sourceField\":\"用户ID\",\"targetField\":\"用户ID\"},"
                        + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐跨表字段一致\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R011"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getTemplateCode()).isEqualTo("FIELD_EQUALS");
        assertThat(result.getTemplateParams()).containsEntry("key", "订单ID");
        assertThat(result.getTemplateParams()).containsEntry("sourceField", "用户ID");
        assertThat(result.getTemplateParams()).containsEntry("targetField", "用户ID");
    }

    @Test
    void recommendRuleBindingAcceptsValidAggregationModelRecommendation() {
        AiAssistService service = multiTableRecommendationService("R012", "订单金额汇总一致",
                "订单金额应等于订单明细小计金额之和",
                "sum(t_order_item.小计金额) by 订单ID = t_order.订单金额",
                Optional.of("{\"templateCode\":\"AGGREGATION_EQUALS\","
                        + "\"templateParams\":{\"source\":\"t_order_item\",\"target\":\"t_order\","
                        + "\"groupBy\":\"订单ID\",\"sum\":\"小计金额\",\"targetField\":\"订单金额\"},"
                        + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐聚合一致\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R012"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getTemplateCode()).isEqualTo("AGGREGATION_EQUALS");
        assertThat(result.getTemplateParams()).containsEntry("sum", "小计金额");
        assertThat(result.getTemplateParams()).containsEntry("targetField", "订单金额");
    }

    @Test
    void recommendRuleBindingAcceptsValidDuplicateCheckModelRecommendation() {
        AiAssistService service = multiTableRecommendationService("R013", "重复支付检查",
                "同一订单ID和支付状态不得重复",
                "unique(订单ID, 支付状态)",
                Optional.of("{\"templateCode\":\"DUPLICATE_CHECK\","
                        + "\"templateParams\":{\"tableName\":\"t_payment\","
                        + "\"groupBy\":[\"订单ID\",\"支付状态\"]},"
                        + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐重复检查\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R013"));

        assertThat(result.isGeneratedByAi()).isTrue();
        assertThat(result.getTemplateCode()).isEqualTo("DUPLICATE_CHECK");
        assertThat(result.getTemplateParams().get("groupBy")).asList().containsExactly("订单ID", "支付状态");
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
        assertThat(result.getConfidence()).isEqualTo("MEDIUM");
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
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("R002", "ds-1"))).thenReturn(Optional.of(rule));
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("R006", "ds-1"))).thenReturn(Optional.of(r006));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_order", "订单ID", "用户ID", "订单状态", "下单时间",
                        "收货地址", "订单金额", "优惠金额", "实付金额")));
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
        rule.setApplicableTables("t_order_item,t_product,t_payment,t_order");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key(ruleId, "ds-1"))).thenReturn(Optional.of(rule));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_order_item", "明细ID", "订单ID", "商品ID", "小计金额"),
                table("ds-1", "t_product", "商品ID", "商品名称"),
                table("ds-1", "t_payment", "支付ID", "订单ID", "用户ID", "支付状态"),
                table("ds-1", "t_order", "订单ID", "用户ID", "订单金额")));
        return new AiAssistService((systemPrompt, userPrompt) -> modelResponse,
                new ObjectMapper(), ruleRepository, tableRepository);
    }

    private AiAssistService inventoryRecommendationService(Optional<String> modelResponse) {
        RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
        DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
        RuleDefinitionEntity rule = rule("ds-1", "R015", "库存变动连续性校验", "");
        rule.setCategory("SINGLE_TABLE_BUSINESS_RULE");
        rule.setApplicableTables("t_inventory_log");
        rule.setDescription("变动后库存 = 变动前库存 + 变动数量(入库为正/出库为负)");
        rule.setPseudoLogic("SELECT * FROM t_inventory_log WHERE 变动后库存 != 变动前库存 + "
                + "CASE WHEN 变动类型='入库' THEN 变动数量 ELSE -变动数量 END");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("R015", "ds-1"))).thenReturn(Optional.of(rule));
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
