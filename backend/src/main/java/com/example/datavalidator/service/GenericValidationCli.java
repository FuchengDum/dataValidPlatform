package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.RuleDefinitionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenericValidationCli {
    private final GenericRuleAssetLoader assetLoader;
    private final GenericValidationRunner runner;
    private final AiAssistService aiAssistService;
    private final GenericValidationLinter linter;
    private final ObjectMapper objectMapper;

    public GenericValidationCli(GenericRuleAssetLoader assetLoader,
                                GenericValidationRunner runner,
                                AiAssistService aiAssistService,
                                GenericValidationLinter linter,
                                ObjectMapper objectMapper) {
        this.assetLoader = assetLoader;
        this.runner = runner;
        this.aiAssistService = aiAssistService;
        this.linter = linter;
        this.objectMapper = objectMapper;
    }

    public int run(String[] args) {
        try {
            if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
                printUsage();
                return 0;
            }
            if ("run".equals(args[0])) {
                return runConfig(requiredOption(args, "--config"));
            }
            if ("validate".equals(args[0])) {
                return runValidate(args);
            }
            if ("recommend".equals(args[0])) {
                return runRecommend(args);
            }
            if ("lint".equals(args[0])) {
                return runLint(args);
            }
            printUsage();
            return 3;
        } catch (BadRequestException | IllegalArgumentException ex) {
            System.err.println("配置错误: " + ex.getMessage());
            return 3;
        } catch (Exception ex) {
            System.err.println("执行失败: " + ex.getMessage());
            return 1;
        }
    }

    private int runConfig(String configPath) {
        GenericValidationConfig config = assetLoader.loadConfig(Path.of(configPath));
        GenericValidationResult result = runner.run(config, Path.of(configPath).toAbsolutePath().getParent());
        printSummary(result);
        return runner.exitCode(result, config.getValidation().getFailOnSeverity());
    }

    private int runValidate(String[] args) {
        String rulePath = requiredOption(args, "--rules");
        String sourcePath = requiredOption(args, "--source");
        GenericValidationConfig config = new GenericValidationConfig();
        config.setSource(assetLoader.loadSource(Path.of(sourcePath)));
        config.getRules().setFile(Path.of(rulePath).toAbsolutePath().toString());
        String outputDir = option(args, "--output");
        if (outputDir != null) {
            config.getValidation().setOutputDir(outputDir);
        }
        GenericValidationResult result = runner.run(config, Path.of(sourcePath).toAbsolutePath().getParent());
        printSummary(result);
        return runner.exitCode(result, config.getValidation().getFailOnSeverity());
    }

    private int runRecommend(String[] args) throws Exception {
        GenericRulePackage rulePackage = assetLoader.loadRules(Path.of(requiredOption(args, "--rules")));
        GenericValidationConfig.SourceConfig metadata = assetLoader.loadSource(Path.of(requiredOption(args, "--metadata")));
        Map<String, List<String>> tableFields = tableFields(metadata);
        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (GenericRulePackage.GenericRule rule : rulePackage.getRules()) {
            AiAssistService.RuleBindingRecommendationResult result =
                    aiAssistService.recommendRuleBinding(toRuleEntity(rule), tableFields);
            recommendations.add(recommendation(rule, result));
        }
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(recommendations);
        String output = option(args, "--output");
        if (output == null) {
            System.out.println(json);
            return 0;
        }
        Path outputPath = Path.of(output).toAbsolutePath();
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, json);
        System.out.println("推荐结果: " + outputPath);
        return 0;
    }

    private int runLint(String[] args) throws Exception {
        GenericLintResult result;
        String configPath = option(args, "--config");
        if (configPath != null) {
            result = linter.lintConfig(Path.of(configPath));
        } else {
            result = linter.lintRules(Path.of(requiredOption(args, "--rules")),
                    Path.of(requiredOption(args, "--metadata")));
        }
        writeJsonResult(result, option(args, "--output"));
        return result.isValid() ? 0 : 3;
    }

    private void printSummary(GenericValidationResult result) {
        System.out.println("校验完成");
        System.out.println("规则: " + result.getExecutedRules() + "/" + result.getTotalRules());
        System.out.println("异常: " + result.getFindingCount()
                + "，严重: " + result.getCriticalCount() + "，警告: " + result.getWarningCount());
        System.out.println("耗时: " + result.getDurationMillis() + " ms");
        System.out.println("报告: " + result.getReports());
    }

    private Map<String, List<String>> tableFields(GenericValidationConfig.SourceConfig metadata) {
        Map<String, List<String>> tableFields = new LinkedHashMap<>();
        if (metadata == null || metadata.getTables() == null || metadata.getTables().isEmpty()) {
            throw new BadRequestException("元数据必须包含 tables 列表");
        }
        for (GenericValidationConfig.TableConfig table : metadata.getTables()) {
            String tableName = firstText(table.getLogicalName(), table.getPhysicalName());
            if (tableName == null) {
                throw new BadRequestException("元数据表缺少 logicalName 或 physicalName");
            }
            List<String> fields = table.getHeaders() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(table.getHeaders());
            if (fields.isEmpty() && !table.getRows().isEmpty()) {
                fields.addAll(table.getRows().get(0).keySet());
            }
            if (fields.isEmpty()) {
                throw new BadRequestException("元数据表缺少字段: " + tableName);
            }
            tableFields.put(tableName, fields);
        }
        return tableFields;
    }

    private RuleDefinitionEntity toRuleEntity(GenericRulePackage.GenericRule rule) {
        requireText(rule.getRuleId(), "ruleId");
        RuleDefinitionEntity entity = new RuleDefinitionEntity();
        entity.setDatasetId("generic-cli");
        entity.setRuleId(rule.getRuleId());
        entity.setRuleName(firstText(rule.getRuleName(), rule.getRuleId()));
        entity.setCategory(firstText(rule.getCategory(), "SINGLE_BUSINESS_RULE"));
        entity.setSeverity(firstText(rule.getSeverity(), "CRITICAL"));
        entity.setDescription(rule.getDescription());
        entity.setPseudoLogic(rule.getDescription());
        entity.setApplicableTables(join(rule.getApplicableTables()));
        entity.setScenarioIds(join(rule.getScenarioIds()));
        entity.setExecutorType("TEMPLATE");
        entity.setTemplateCode(rule.getTemplateCode());
        return entity;
    }

    private Map<String, Object> recommendation(
            GenericRulePackage.GenericRule rule, AiAssistService.RuleBindingRecommendationResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("ruleId", rule.getRuleId());
        value.put("ruleName", firstText(rule.getRuleName(), rule.getRuleId()));
        value.put("templateCode", result.getTemplateCode());
        value.put("templateParams", result.getTemplateParams());
        value.put("confidence", result.getConfidence());
        value.put("explanation", result.getExplanation());
        value.put("source", result.getSource());
        value.put("generatedByAi", result.isGeneratedByAi());
        value.put("requiresHumanReview", result.isRequiresHumanReview());
        value.put("warnings", result.getWarnings());
        return value;
    }

    private void writeJsonResult(Object value, String output) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        if (output == null) {
            System.out.println(json);
            return;
        }
        Path outputPath = Path.of(output).toAbsolutePath();
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, json);
        System.out.println("输出: " + outputPath);
    }

    private String firstText(String first, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback;
        }
        return null;
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(fieldName + " 不能为空");
        }
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    private String requiredOption(String[] args, String name) {
        String value = option(args, name);
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException("缺少参数 " + name);
        }
        return value;
    }

    private String option(String[] args, String name) {
        for (int index = 0; index < args.length; index++) {
            if (name.equals(args[index]) && index + 1 < args.length) {
                return args[index + 1];
            }
            if (args[index].startsWith(name + "=")) {
                return args[index].substring(name.length() + 1);
            }
        }
        return null;
    }

    private void printUsage() {
        System.out.println("用法:");
        System.out.println("  data-validator run --config validator.yml");
        System.out.println("  data-validator validate --rules rules.yml --source source.yml --output reports");
        System.out.println("  data-validator recommend --rules rules.yml --metadata source.yml --output recommendations.json");
        System.out.println("  data-validator lint --config validator.yml");
        System.out.println("  data-validator lint --rules rules.yml --metadata source.yml");
    }
}
