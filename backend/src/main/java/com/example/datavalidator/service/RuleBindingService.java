package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.RuleBindingEntity;
import com.example.datavalidator.persistence.RuleDefinitionEntity;
import com.example.datavalidator.repository.DataTableSnapshotRepository;
import com.example.datavalidator.repository.RuleBindingRepository;
import com.example.datavalidator.repository.RuleDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RuleBindingService {
    private static final List<String> SUPPORTED_TEMPLATE_CODES = Arrays.asList(
            "NOT_NULL", "NON_NEGATIVE", "NUMERIC_TYPE", "FIELD_EXPRESSION",
            "ROW_EXPRESSION", "EXISTS_IN_TABLE", "RELATION_EXISTS",
            "FIELD_EQUALS", "AGGREGATION_EQUALS", "DUPLICATE_CHECK");

    private final RuleDefinitionRepository ruleRepository;
    private final RuleBindingRepository bindingRepository;
    private final DataTableSnapshotRepository tableRepository;
    private final JsonService jsonService;

    public RuleBindingService(RuleDefinitionRepository ruleRepository,
                              RuleBindingRepository bindingRepository,
                              DataTableSnapshotRepository tableRepository,
                              JsonService jsonService) {
        this.ruleRepository = ruleRepository;
        this.bindingRepository = bindingRepository;
        this.tableRepository = tableRepository;
        this.jsonService = jsonService;
    }

    public List<RuleView> listRules(String datasetId) {
        Map<String, RuleBindingEntity> bindings = bindingRepository.findByDatasetId(datasetId).stream()
                .collect(Collectors.toMap(RuleBindingEntity::getRuleId, item -> item, (left, right) -> left));
        List<RuleView> views = new ArrayList<>();
        for (RuleDefinitionEntity rule : ruleRepository.findByDatasetIdOrderByRuleId(datasetId)) {
            views.add(toRuleView(rule, bindings.get(rule.getRuleId())));
        }
        return views;
    }

    public BindingView getBinding(String datasetId, String ruleId) {
        RuleBindingEntity entity = bindingRepository.findByDatasetIdAndRuleId(datasetId, ruleId)
                .orElseThrow(() -> new BadRequestException("规则绑定不存在: " + ruleId));
        return toBindingView(entity);
    }

    @Transactional
    public BindingView updateBinding(String datasetId, String ruleId, BindingRequest request) {
        if (request == null) {
            throw new BadRequestException("规则绑定请求不能为空");
        }
        RuleDefinitionEntity rule = ruleRepository.findById(new RuleDefinitionEntity.Key(ruleId, datasetId))
                .orElseThrow(() -> new BadRequestException("规则不存在: " + ruleId));
        RuleBindingEntity entity = bindingRepository.findByDatasetIdAndRuleId(datasetId, ruleId)
                .orElseGet(() -> newBinding(datasetId, ruleId));
        String executorType = normalizeExecutorType(request.getExecutorType());
        String templateCode = resolveTemplateCode(rule, entity, request);
        Map<String, Object> templateParams = resolveTemplateParams(entity, request);
        if ("TEMPLATE".equals(executorType)) {
            validateTemplateBinding(datasetId, templateCode, templateParams);
        }
        entity.setExecutorType(executorType);
        entity.setTemplateCode(templateCode);
        entity.setTemplateParamsJson(jsonService.write(templateParams));
        bindingRepository.save(entity);
        return toBindingView(entity);
    }

    private RuleBindingEntity newBinding(String datasetId, String ruleId) {
        RuleBindingEntity entity = new RuleBindingEntity();
        entity.setId(IdFactory.next("bind"));
        entity.setDatasetId(datasetId);
        entity.setRuleId(ruleId);
        entity.setExecutorType("BUILTIN");
        entity.setBuiltinExecutorName(ruleId);
        entity.setTemplateParamsJson(jsonService.write(new LinkedHashMap<>()));
        return entity;
    }

    private String normalizeExecutorType(String executorType) {
        if (executorType == null || executorType.trim().isEmpty()) {
            return "BUILTIN";
        }
        String normalized = executorType.trim().toUpperCase();
        if (!"BUILTIN".equals(normalized) && !"TEMPLATE".equals(normalized)) {
            throw new BadRequestException("不支持的规则执行方式: " + executorType);
        }
        return normalized;
    }

    private Map<String, Object> safeParams(Map<String, Object> params) {
        return params == null ? new LinkedHashMap<>() : params;
    }

    private String resolveTemplateCode(RuleDefinitionEntity rule, RuleBindingEntity entity, BindingRequest request) {
        if (!isBlank(request.getTemplateCode())) {
            return request.getTemplateCode().trim();
        }
        if (!isBlank(entity.getTemplateCode())) {
            return entity.getTemplateCode();
        }
        return rule.getTemplateCode();
    }

    private Map<String, Object> resolveTemplateParams(RuleBindingEntity entity, BindingRequest request) {
        Map<String, Object> requestParams = safeParams(request.getTemplateParams());
        if (!requestParams.isEmpty()) {
            return new LinkedHashMap<>(requestParams);
        }
        return new LinkedHashMap<>(jsonService.readObjectMap(entity.getTemplateParamsJson()));
    }

    private void validateTemplateBinding(String datasetId, String templateCode, Map<String, Object> params) {
        if (isBlank(templateCode)) {
            throw new BadRequestException("模板编码不能为空");
        }
        if (!SUPPORTED_TEMPLATE_CODES.contains(templateCode)) {
            throw new BadRequestException("暂不支持的规则模板: " + templateCode);
        }
        TemplateBindingValidator.validate(templateCode, params, tableRepository.findByDatasetId(datasetId), jsonService);
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private RuleView toRuleView(RuleDefinitionEntity rule, RuleBindingEntity binding) {
        RuleView view = new RuleView();
        view.setDatasetId(rule.getDatasetId());
        view.setRuleId(rule.getRuleId());
        view.setRuleName(rule.getRuleName());
        view.setCategory(rule.getCategory());
        view.setSeverity(rule.getSeverity());
        view.setApplicableTables(rule.getApplicableTables());
        view.setDescription(rule.getDescription());
        view.setExecutorType(binding == null ? "BUILTIN" : binding.getExecutorType());
        view.setTemplateCode(binding == null ? rule.getTemplateCode() : binding.getTemplateCode());
        view.setTemplateParams(binding == null ? new LinkedHashMap<>() : jsonService.readObjectMap(binding.getTemplateParamsJson()));
        view.setTemplateParamSummary(summarize(view.getTemplateParams()));
        return view;
    }

    private BindingView toBindingView(RuleBindingEntity entity) {
        BindingView view = new BindingView();
        view.setDatasetId(entity.getDatasetId());
        view.setRuleId(entity.getRuleId());
        view.setExecutorType(entity.getExecutorType());
        view.setTemplateCode(entity.getTemplateCode());
        view.setTemplateParams(jsonService.readObjectMap(entity.getTemplateParamsJson()));
        view.setTemplateParamSummary(summarize(view.getTemplateParams()));
        return view;
    }

    private String summarize(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    public static class BindingRequest {
        private String executorType;
        private String templateCode;
        private Map<String, Object> templateParams = new LinkedHashMap<>();

        public String getExecutorType() { return executorType; }
        public void setExecutorType(String executorType) { this.executorType = executorType; }
        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
        public Map<String, Object> getTemplateParams() { return templateParams; }
        public void setTemplateParams(Map<String, Object> templateParams) { this.templateParams = templateParams; }
    }

    public static class BindingView {
        private String datasetId;
        private String ruleId;
        private String executorType;
        private String templateCode;
        private Map<String, Object> templateParams = new LinkedHashMap<>();
        private String templateParamSummary;

        public String getDatasetId() { return datasetId; }
        public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getExecutorType() { return executorType; }
        public void setExecutorType(String executorType) { this.executorType = executorType; }
        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
        public Map<String, Object> getTemplateParams() { return templateParams; }
        public void setTemplateParams(Map<String, Object> templateParams) { this.templateParams = templateParams; }
        public String getTemplateParamSummary() { return templateParamSummary; }
        public void setTemplateParamSummary(String templateParamSummary) { this.templateParamSummary = templateParamSummary; }
    }

    public static class RuleView extends BindingView {
        private String ruleName;
        private String category;
        private String severity;
        private String applicableTables;
        private String description;

        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getApplicableTables() { return applicableTables; }
        public void setApplicableTables(String applicableTables) { this.applicableTables = applicableTables; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
