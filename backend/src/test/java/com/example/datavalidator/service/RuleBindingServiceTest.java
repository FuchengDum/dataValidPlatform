package com.example.datavalidator.service;

import com.example.datavalidator.persistence.RuleBindingEntity;
import com.example.datavalidator.persistence.DataTableSnapshotEntity;
import com.example.datavalidator.persistence.RuleDefinitionEntity;
import com.example.datavalidator.repository.DataTableSnapshotRepository;
import com.example.datavalidator.repository.RuleBindingRepository;
import com.example.datavalidator.repository.RuleDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleBindingServiceTest {
    private final RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
    private final RuleBindingRepository bindingRepository = mock(RuleBindingRepository.class);
    private final DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
    private final RuleBindingService service = new RuleBindingService(
            ruleRepository, bindingRepository, tableRepository, new JsonService(new ObjectMapper()));

    @Test
    void listRulesIncludesBindingAndParameterSummary() {
        RuleDefinitionEntity rule = rule("ds-1", "C001", "客户资料必填字段");
        RuleBindingEntity binding = binding("ds-1", "C001", "TEMPLATE", "NOT_NULL",
                "{\"tableName\":\"customer_profile\",\"fields\":[\"客户编号\",\"证件号\"]}");
        when(ruleRepository.findByDatasetIdOrderByRuleId("ds-1")).thenReturn(Arrays.asList(rule));
        when(bindingRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(binding));

        List<RuleBindingService.RuleView> views = service.listRules("ds-1");

        assertThat(views).hasSize(1);
        RuleBindingService.RuleView view = views.get(0);
        assertThat(view.getRuleId()).isEqualTo("C001");
        assertThat(view.getExecutorType()).isEqualTo("TEMPLATE");
        assertThat(view.getTemplateCode()).isEqualTo("NOT_NULL");
        assertThat(view.getTemplateParamSummary()).contains("customer_profile");
        assertThat(view.getTemplateParamSummary()).contains("客户编号");
    }

    @Test
    void updateBindingStoresTemplateParamsAsJson() {
        RuleDefinitionEntity rule = rule("ds-1", "C001", "客户资料必填字段");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("C001", "ds-1"))).thenReturn(Optional.of(rule));
        when(bindingRepository.findByDatasetIdAndRuleId("ds-1", "C001")).thenReturn(Optional.empty());
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "customer_profile", "客户编号", "证件号")));
        when(bindingRepository.save(any(RuleBindingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RuleBindingService.BindingRequest request = new RuleBindingService.BindingRequest();
        request.setExecutorType("TEMPLATE");
        request.setTemplateCode("NOT_NULL");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", "customer_profile");
        params.put("fields", Arrays.asList("客户编号", "证件号"));
        request.setTemplateParams(params);

        RuleBindingService.BindingView saved = service.updateBinding("ds-1", "C001", request);

        assertThat(saved.getExecutorType()).isEqualTo("TEMPLATE");
        assertThat(saved.getTemplateCode()).isEqualTo("NOT_NULL");
        verify(bindingRepository).save(any(RuleBindingEntity.class));
    }

    @Test
    void switchBackToBuiltinKeepsExistingTemplateParamsForLaterReuse() {
        RuleDefinitionEntity rule = rule("ds-1", "C001", "客户资料必填字段");
        RuleBindingEntity existing = binding("ds-1", "C001", "TEMPLATE", "NOT_NULL",
                "{\"tableName\":\"customer_profile\",\"fields\":[\"客户编号\",\"证件号\"]}");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("C001", "ds-1"))).thenReturn(Optional.of(rule));
        when(bindingRepository.findByDatasetIdAndRuleId("ds-1", "C001")).thenReturn(Optional.of(existing));
        when(bindingRepository.save(any(RuleBindingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RuleBindingService.BindingRequest request = new RuleBindingService.BindingRequest();
        request.setExecutorType("BUILTIN");
        request.setTemplateCode("NOT_NULL");

        RuleBindingService.BindingView saved = service.updateBinding("ds-1", "C001", request);

        assertThat(saved.getExecutorType()).isEqualTo("BUILTIN");
        assertThat(saved.getTemplateParams()).containsEntry("tableName", "customer_profile");
        assertThat(saved.getTemplateParamSummary()).contains("证件号");
    }

    @Test
    void updateBindingRejectsTemplateWithUnknownTable() {
        RuleDefinitionEntity rule = rule("ds-1", "C001", "客户资料必填字段");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("C001", "ds-1"))).thenReturn(Optional.of(rule));
        when(bindingRepository.findByDatasetIdAndRuleId("ds-1", "C001")).thenReturn(Optional.empty());
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "customer_profile", "客户编号", "证件号")));
        RuleBindingService.BindingRequest request = templateRequest("NOT_NULL", "missing_table", "客户编号");

        assertThatThrownBy(() -> service.updateBinding("ds-1", "C001", request))
                .isInstanceOf(com.example.datavalidator.exception.BadRequestException.class)
                .hasMessageContaining("逻辑表不存在");
    }

    @Test
    void updateBindingRejectsTemplateWithUnknownField() {
        RuleDefinitionEntity rule = rule("ds-1", "C001", "客户资料必填字段");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("C001", "ds-1"))).thenReturn(Optional.of(rule));
        when(bindingRepository.findByDatasetIdAndRuleId("ds-1", "C001")).thenReturn(Optional.empty());
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "customer_profile", "客户编号", "证件号")));
        RuleBindingService.BindingRequest request = templateRequest("NOT_NULL", "customer_profile", "手机号");

        assertThatThrownBy(() -> service.updateBinding("ds-1", "C001", request))
                .isInstanceOf(com.example.datavalidator.exception.BadRequestException.class)
                .hasMessageContaining("字段不存在");
    }

    @Test
    void updateBindingAcceptsAggregationTemplateWithCrossTableFields() {
        RuleDefinitionEntity rule = rule("ds-1", "C002", "聚合金额一致");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("C002", "ds-1"))).thenReturn(Optional.of(rule));
        when(bindingRepository.findByDatasetIdAndRuleId("ds-1", "C002")).thenReturn(Optional.empty());
        when(bindingRepository.save(any(RuleBindingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "order_item", "订单ID", "小计金额"),
                table("ds-1", "order", "订单ID", "订单金额")));
        RuleBindingService.BindingRequest request = request("AGGREGATION_EQUALS")
                .param("source", "order_item")
                .param("target", "order")
                .param("groupBy", "订单ID")
                .param("sum", "小计金额")
                .param("targetField", "订单金额")
                .build();

        RuleBindingService.BindingView saved = service.updateBinding("ds-1", "C002", request);

        assertThat(saved.getExecutorType()).isEqualTo("TEMPLATE");
        assertThat(saved.getTemplateCode()).isEqualTo("AGGREGATION_EQUALS");
    }

    @Test
    void updateBindingRejectsCrossTableTemplateWithUnknownField() {
        RuleDefinitionEntity rule = rule("ds-1", "C003", "存在性校验");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("C003", "ds-1"))).thenReturn(Optional.of(rule));
        when(bindingRepository.findByDatasetIdAndRuleId("ds-1", "C003")).thenReturn(Optional.empty());
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "order_item", "订单ID", "商品ID"),
                table("ds-1", "product", "商品ID")));
        RuleBindingService.BindingRequest request = request("EXISTS_IN_TABLE")
                .param("source", "order_item")
                .param("target", "product")
                .param("key", "不存在字段")
                .build();

        assertThatThrownBy(() -> service.updateBinding("ds-1", "C003", request))
                .isInstanceOf(com.example.datavalidator.exception.BadRequestException.class)
                .hasMessageContaining("字段不存在");
    }

    @Test
    void updateBindingRejectsExpressionTemplateWithUnknownField() {
        RuleDefinitionEntity rule = rule("ds-1", "C004", "表达式校验");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("C004", "ds-1"))).thenReturn(Optional.of(rule));
        when(bindingRepository.findByDatasetIdAndRuleId("ds-1", "C004")).thenReturn(Optional.empty());
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "order_item", "单价", "数量", "小计金额")));
        RuleBindingService.BindingRequest request = request("FIELD_EXPRESSION")
                .param("tableName", "order_item")
                .param("expression", "小计金额 == 单价 * 不存在字段")
                .build();

        assertThatThrownBy(() -> service.updateBinding("ds-1", "C004", request))
                .isInstanceOf(com.example.datavalidator.exception.BadRequestException.class)
                .hasMessageContaining("字段不存在");
    }

    @Test
    void updateBindingAcceptsExpressionTemplateWithMultipleConditions() {
        RuleDefinitionEntity rule = rule("ds-1", "C005", "实付金额关系校验");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("C005", "ds-1"))).thenReturn(Optional.of(rule));
        when(bindingRepository.findByDatasetIdAndRuleId("ds-1", "C005")).thenReturn(Optional.empty());
        when(bindingRepository.save(any(RuleBindingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_order", "订单金额", "优惠金额", "实付金额")));
        RuleBindingService.BindingRequest request = request("FIELD_EXPRESSION")
                .param("tableName", "t_order")
                .param("expression", "实付金额 == 订单金额 - 优惠金额 && 实付金额 <= 订单金额")
                .build();

        RuleBindingService.BindingView saved = service.updateBinding("ds-1", "C005", request);

        assertThat(saved.getTemplateCode()).isEqualTo("FIELD_EXPRESSION");
        assertThat(saved.getTemplateParams()).containsEntry("expression",
                "实付金额 == 订单金额 - 优惠金额 && 实付金额 <= 订单金额");
    }

    @Test
    void updateBindingRejectsExpressionTemplateThatCannotBeParsed() {
        RuleDefinitionEntity rule = rule("ds-1", "C006", "非法表达式校验");
        when(ruleRepository.findById(new RuleDefinitionEntity.Key("C006", "ds-1"))).thenReturn(Optional.of(rule));
        when(bindingRepository.findByDatasetIdAndRuleId("ds-1", "C006")).thenReturn(Optional.empty());
        when(tableRepository.findByDatasetId("ds-1")).thenReturn(Arrays.asList(
                table("ds-1", "t_order", "订单金额", "优惠金额", "实付金额")));
        RuleBindingService.BindingRequest request = request("FIELD_EXPRESSION")
                .param("tableName", "t_order")
                .param("expression", "实付金额 订单金额 优惠金额")
                .build();

        assertThatThrownBy(() -> service.updateBinding("ds-1", "C006", request))
                .isInstanceOf(com.example.datavalidator.exception.BadRequestException.class)
                .hasMessageContaining("表达式格式不支持");
    }

    private RuleDefinitionEntity rule(String datasetId, String ruleId, String ruleName) {
        RuleDefinitionEntity entity = new RuleDefinitionEntity();
        entity.setDatasetId(datasetId);
        entity.setRuleId(ruleId);
        entity.setRuleName(ruleName);
        entity.setCategory("SINGLE_FIELD_CONSTRAINT");
        entity.setSeverity("CRITICAL");
        entity.setTemplateCode("NOT_NULL");
        return entity;
    }

    private RuleBindingEntity binding(String datasetId, String ruleId, String executorType,
                                      String templateCode, String paramsJson) {
        RuleBindingEntity entity = new RuleBindingEntity();
        entity.setId("bind-1");
        entity.setDatasetId(datasetId);
        entity.setRuleId(ruleId);
        entity.setExecutorType(executorType);
        entity.setTemplateCode(templateCode);
        entity.setTemplateParamsJson(paramsJson);
        return entity;
    }

    private RuleBindingService.BindingRequest templateRequest(String templateCode, String tableName, String... fields) {
        RuleBindingService.BindingRequest request = new RuleBindingService.BindingRequest();
        request.setExecutorType("TEMPLATE");
        request.setTemplateCode(templateCode);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", tableName);
        params.put("fields", Arrays.asList(fields));
        request.setTemplateParams(params);
        return request;
    }

    private RequestBuilder request(String templateCode) {
        return new RequestBuilder(templateCode);
    }

    private DataTableSnapshotEntity table(String datasetId, String logicalName, String... headers) {
        DataTableSnapshotEntity entity = new DataTableSnapshotEntity();
        entity.setId("table-" + logicalName);
        entity.setDatasetId(datasetId);
        entity.setLogicalName(logicalName);
        entity.setSourceType("EXCEL");
        entity.setHeadersJson(new JsonService(new ObjectMapper()).write(Arrays.asList(headers)));
        return entity;
    }

    private static class RequestBuilder {
        private final RuleBindingService.BindingRequest request = new RuleBindingService.BindingRequest();
        private final Map<String, Object> params = new LinkedHashMap<>();

        RequestBuilder(String templateCode) {
            request.setExecutorType("TEMPLATE");
            request.setTemplateCode(templateCode);
        }

        RequestBuilder param(String key, Object value) {
            params.put(key, value);
            return this;
        }

        RuleBindingService.BindingRequest build() {
            request.setTemplateParams(params);
            return request;
        }
    }
}
