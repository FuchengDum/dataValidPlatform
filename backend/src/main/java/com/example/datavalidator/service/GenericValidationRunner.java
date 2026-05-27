package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.RuleBinding;
import com.example.datavalidator.domain.RuleCategory;
import com.example.datavalidator.domain.RuleDefinition;
import com.example.datavalidator.domain.Severity;
import com.example.datavalidator.domain.ValidationFinding;
import com.example.datavalidator.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GenericValidationRunner {
    private final GenericRuleAssetLoader assetLoader;
    private final GenericDataSourceProvider dataSourceProvider;
    private final GenericValidationReportWriter reportWriter;
    private final TemplateRuleExecutor templateRuleExecutor;

    public GenericValidationRunner(GenericRuleAssetLoader assetLoader,
                                   GenericDataSourceProvider dataSourceProvider,
                                   GenericValidationReportWriter reportWriter,
                                   TemplateRuleExecutor templateRuleExecutor) {
        this.assetLoader = assetLoader;
        this.dataSourceProvider = dataSourceProvider;
        this.reportWriter = reportWriter;
        this.templateRuleExecutor = templateRuleExecutor;
    }

    public GenericValidationResult run(Path configPath) {
        GenericValidationConfig config = assetLoader.loadConfig(configPath);
        Path baseDir = configPath.toAbsolutePath().getParent();
        return run(config, baseDir);
    }

    public GenericValidationResult run(GenericValidationConfig config, Path baseDir) {
        LocalDateTime startedAt = LocalDateTime.now();
        Map<String, DataTable> tables = dataSourceProvider.load(config.getSource(), baseDir);
        GenericRulePackage rulePackage = assetLoader.loadRules(resolve(baseDir, config.getRules().getFile()));
        Map<String, List<String>> headersByTable = tables.values().stream().collect(Collectors.toMap(
                DataTable::getLogicalName, DataTable::getHeaders, (left, right) -> left, LinkedHashMap::new));
        List<ValidationFinding> findings = new ArrayList<>();
        int executedRules = 0;
        for (GenericRulePackage.GenericRule genericRule : rulePackage.getRules()) {
            if (!genericRule.isEnabled()) {
                continue;
            }
            RuleDefinition rule = toRuleDefinition(genericRule);
            RuleBinding binding = toRuleBinding(genericRule);
            TemplateBindingValidator.validate(binding.getTemplateCode(), binding.getTemplateParams(), headersByTable);
            findings.addAll(templateRuleExecutor.execute(rule, tables, binding));
            executedRules++;
        }
        GenericValidationResult result = new GenericValidationResult();
        result.setTotalRules(rulePackage.getRules().size());
        result.setExecutedRules(executedRules);
        result.setFindings(findings);
        result.setFindingCount(findings.size());
        result.setCriticalCount(count(findings, Severity.CRITICAL));
        result.setWarningCount(count(findings, Severity.WARNING));
        result.setDurationMillis(Duration.between(startedAt, LocalDateTime.now()).toMillis());
        reportWriter.writeReports(result, config.getValidation(), baseDir);
        return result;
    }

    public int exitCode(GenericValidationResult result, String failOnSeverity) {
        Severity severity = parseSeverity(failOnSeverity);
        if (severity == Severity.CRITICAL && result.getCriticalCount() > 0) {
            return 2;
        }
        if (severity == Severity.WARNING && result.getFindingCount() > 0) {
            return 2;
        }
        return 0;
    }

    private RuleDefinition toRuleDefinition(GenericRulePackage.GenericRule genericRule) {
        requireText(genericRule.getRuleId(), "ruleId");
        requireText(genericRule.getTemplateCode(), "templateCode");
        RuleDefinition rule = new RuleDefinition();
        rule.setRuleId(genericRule.getRuleId());
        rule.setRuleName(isBlank(genericRule.getRuleName()) ? genericRule.getRuleId() : genericRule.getRuleName());
        rule.setCategory(parseCategory(genericRule.getCategory()));
        rule.setSeverity(parseSeverity(genericRule.getSeverity()));
        rule.setDescription(genericRule.getDescription());
        rule.setApplicableTables(genericRule.getApplicableTables());
        rule.setScenarioIds(genericRule.getScenarioIds());
        rule.setExecutorType("TEMPLATE");
        rule.setTemplateCode(genericRule.getTemplateCode());
        return rule;
    }

    private RuleBinding toRuleBinding(GenericRulePackage.GenericRule genericRule) {
        RuleBinding binding = new RuleBinding();
        binding.setRuleId(genericRule.getRuleId());
        binding.setExecutorType("TEMPLATE");
        binding.setEnabled(genericRule.isEnabled());
        binding.setTemplateCode(genericRule.getTemplateCode());
        binding.setTemplateParams(genericRule.getTemplateParams());
        return binding;
    }

    private RuleCategory parseCategory(String value) {
        try {
            return RuleCategory.valueOf(isBlank(value) ? "SINGLE_BUSINESS_RULE" : value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BadRequestException("规则分类不支持: " + value);
        }
    }

    private Severity parseSeverity(String value) {
        try {
            return Severity.valueOf(isBlank(value) ? "CRITICAL" : value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BadRequestException("严重等级不支持: " + value);
        }
    }

    private int count(List<ValidationFinding> findings, Severity severity) {
        return (int) findings.stream().filter(item -> item.getSeverity() == severity).count();
    }

    private Path resolve(Path baseDir, String path) {
        if (isBlank(path)) {
            throw new BadRequestException("规则文件不能为空");
        }
        Path candidate = Path.of(path);
        return candidate.isAbsolute() ? candidate : baseDir.resolve(candidate).normalize();
    }

    private void requireText(String value, String field) {
        if (isBlank(value)) {
            throw new BadRequestException("规则缺少必填字段: " + field);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
