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

    private DataTableSnapshotEntity table(String datasetId, String logicalName, String... headers) {
        DataTableSnapshotEntity entity = new DataTableSnapshotEntity();
        entity.setId("table-" + logicalName);
        entity.setDatasetId(datasetId);
        entity.setLogicalName(logicalName);
        entity.setSourceType("EXCEL");
        entity.setHeadersJson(new JsonService(new ObjectMapper()).write(Arrays.asList(headers)));
        return entity;
    }
}
