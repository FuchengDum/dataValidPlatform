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
        assertThat(result.getWarnings()).contains("模型推荐未通过模板白名单或字段校验，已降级为本地推荐");
    }

    @Test
    void recommendRuleBindingFallsBackWhenModelUsesUnsupportedTemplate() {
        AiAssistService service = recommendationService(Optional.of("{\"templateCode\":\"FIELD_EQUALS\","
                + "\"templateParams\":{\"source\":\"t_order\",\"target\":\"t_payment\"},"
                + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐跨表一致\"}"));

        AiAssistService.RuleBindingRecommendationResult result = service.recommendRuleBinding(
                recommendationRequest("ds-1", "R002"));

        assertThat(result.isGeneratedByAi()).isFalse();
        assertThat(result.getSource()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(result.getTemplateCode()).isEqualTo("NOT_NULL");
        assertThat(result.getWarnings()).contains("模型推荐未通过模板白名单或字段校验，已降级为本地推荐");
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
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("R002", "ds-1"))).thenReturn(Optional.of(rule));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_order", "订单ID", "用户ID", "订单状态", "下单时间", "收货地址")));
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
