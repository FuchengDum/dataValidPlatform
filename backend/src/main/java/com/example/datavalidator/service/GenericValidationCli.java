package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.RuleDefinitionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class GenericValidationCli {
    private static final String FALLBACK_VERSION = "0.1.0";
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
            if ("--version".equals(args[0])) {
                System.out.println("data-validator " + toolVersion());
                return 0;
            }
            if ("run".equals(args[0])) {
                return runConfig(args);
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
            if (hasFlag(args, "--json")) {
                printErrorJson(3, "CONFIG_ERROR", ex.getMessage());
                return 3;
            }
            System.err.println("配置错误: " + ex.getMessage());
            return 3;
        } catch (Exception ex) {
            if (hasFlag(args, "--json")) {
                printErrorJson(1, "EXECUTION_ERROR", ex.getMessage());
                return 1;
            }
            System.err.println("执行失败: " + ex.getMessage());
            return 1;
        }
    }

    private int runConfig(String[] args) throws Exception {
        String configPath = requiredOption(args, "--config");
        GenericValidationConfig config = assetLoader.loadConfig(Path.of(configPath));
        applyCiOptions(args, config);
        GenericValidationResult result = runner.run(config, Path.of(configPath).toAbsolutePath().getParent());
        int exitCode = runner.exitCode(result, config.getValidation().getFailOnSeverity());
        printRunOutput(args, result, exitCode);
        return exitCode;
    }

    private int runValidate(String[] args) throws Exception {
        String rulePath = requiredOption(args, "--rules");
        String sourcePath = requiredOption(args, "--source");
        GenericValidationConfig config = new GenericValidationConfig();
        config.setSource(assetLoader.loadSource(Path.of(sourcePath)));
        config.getRules().setFile(Path.of(rulePath).toAbsolutePath().toString());
        String outputDir = option(args, "--output");
        if (outputDir != null) {
            config.getValidation().setOutputDir(outputDir);
        }
        applyCiOptions(args, config);
        GenericValidationResult result = runner.run(config, Path.of(sourcePath).toAbsolutePath().getParent());
        int exitCode = runner.exitCode(result, config.getValidation().getFailOnSeverity());
        printRunOutput(args, result, exitCode);
        return exitCode;
    }

    private int runRecommend(String[] args) throws Exception {
        Path rulesPath = Path.of(requiredOption(args, "--rules"));
        Path candidateRules = optionPath(args, "--candidate-rules");
        if (candidateRules != null && samePath(rulesPath, candidateRules)) {
            throw new BadRequestException("--candidate-rules 不能与 --rules 指向同一文件");
        }
        GenericRulePackage rulePackage = assetLoader.loadRules(rulesPath);
        GenericValidationConfig.SourceConfig metadata = assetLoader.loadSource(Path.of(requiredOption(args, "--metadata")));
        Map<String, List<String>> tableFields = tableFields(metadata);
        List<Map<String, Object>> recommendations = new ArrayList<>();
        GenericRulePackage candidatePackage = new GenericRulePackage();
        candidatePackage.setSchemaVersion(firstText(rulePackage.getSchemaVersion(), "1"));
        Path debugAiDir = optionPath(args, "--debug-ai");
        for (GenericRulePackage.GenericRule rule : rulePackage.getRules()) {
            AiAssistService.RuleBindingRecommendationTrace trace =
                    aiAssistService.recommendRuleBindingWithTrace(toRuleEntity(rule), tableFields);
            AiAssistService.RuleBindingRecommendationResult result = trace.getResult();
            boolean candidateGenerated = canGenerateCandidate(result);
            recommendations.add(recommendation(rule, result, candidateGenerated));
            if (candidateGenerated) {
                candidatePackage.getRules().add(recommendedRule(rule, result));
            }
            writeAiDebugTrace(debugAiDir, rule.getRuleId(), trace);
        }
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(recommendations);
        String output = option(args, "--output");
        if (output == null) {
            System.out.println(json);
        } else {
            Path outputPath = Path.of(output).toAbsolutePath();
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.writeString(outputPath, json);
            System.out.println("推荐结果: " + outputPath);
        }
        if (candidateRules != null) {
            writeCandidateRulePackage(candidatePackage, candidateRules);
            System.err.println("候选规则包: " + candidateRules.toAbsolutePath());
        }
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

    private void printRunOutput(String[] args, GenericValidationResult result, int exitCode) throws Exception {
        if (hasFlag(args, "--json")) {
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(summary(result, exitCode)));
            return;
        }
        if (!hasFlag(args, "--quiet")) {
            printSummary(result);
        }
    }

    private Map<String, Object> summary(GenericValidationResult result, int exitCode) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportVersion", result.getReportVersion());
        summary.put("toolVersion", result.getToolVersion());
        summary.put("exitCode", exitCode);
        summary.put("totalRules", result.getTotalRules());
        summary.put("executedRules", result.getExecutedRules());
        summary.put("findingCount", result.getFindingCount());
        summary.put("criticalCount", result.getCriticalCount());
        summary.put("warningCount", result.getWarningCount());
        summary.put("durationMillis", result.getDurationMillis());
        summary.put("rulePackageHash", result.getRulePackageHash());
        summary.put("sourceSummary", result.getSourceSummary());
        summary.put("reports", result.getReports());
        return summary;
    }

    private void printErrorJson(int exitCode, String code, String message) {
        try {
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(errorSummary(exitCode, code, message)));
        } catch (Exception ex) {
            System.err.println("执行失败: " + message);
        }
    }

    private Map<String, Object> errorSummary(int exitCode, String code, String message) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportVersion", "1");
        summary.put("exitCode", exitCode);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message == null ? "" : message);
        summary.put("error", error);
        return summary;
    }

    private void applyCiOptions(String[] args, GenericValidationConfig config) {
        String outputDir = option(args, "--output");
        if (outputDir != null) {
            config.getValidation().setOutputDir(outputDir);
        }
        if (hasFlag(args, "--no-report")) {
            config.getValidation().setNoReport(true);
        }
        config.getValidation().setCommandSummary(commandSummary(args));
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

    private Map<String, Object> recommendation(GenericRulePackage.GenericRule rule,
                                               AiAssistService.RuleBindingRecommendationResult result,
                                               boolean candidateGenerated) {
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
        value.put("warningDetails", warningDetails(result));
        value.put("warningCategories", warningCategories(result));
        value.put("candidateGenerated", candidateGenerated);
        value.put("diff", recommendationDiff(rule, result));
        return value;
    }

    private boolean canGenerateCandidate(AiAssistService.RuleBindingRecommendationResult result) {
        if (result == null || isBlank(result.getTemplateCode()) || result.getTemplateParams().isEmpty()) {
            return false;
        }
        return !"LOW".equalsIgnoreCase(firstText(result.getConfidence(), ""));
    }

    private GenericRulePackage.GenericRule recommendedRule(
            GenericRulePackage.GenericRule source, AiAssistService.RuleBindingRecommendationResult result) {
        GenericRulePackage.GenericRule rule = new GenericRulePackage.GenericRule();
        rule.setRuleId(source.getRuleId());
        rule.setRuleName(source.getRuleName());
        rule.setCategory(source.getCategory());
        rule.setSeverity(source.getSeverity());
        rule.setDescription(source.getDescription());
        rule.setApplicableTables(copyList(source.getApplicableTables()));
        rule.setScenarioIds(copyList(source.getScenarioIds()));
        rule.setEnabled(source.isEnabled());
        rule.setTemplateCode(result.getTemplateCode());
        rule.setTemplateParams(new LinkedHashMap<>(result.getTemplateParams()));
        return rule;
    }

    private Map<String, Object> recommendationDiff(
            GenericRulePackage.GenericRule rule, AiAssistService.RuleBindingRecommendationResult result) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("originalRule", ruleMap(rule));
        diff.put("originalTemplate", rule.getTemplateCode());
        diff.put("originalParams", rule.getTemplateParams());
        diff.put("recommendedTemplate", result.getTemplateCode());
        diff.put("recommendedParams", result.getTemplateParams());
        diff.put("recommendedReason", result.getExplanation());
        return diff;
    }

    private List<Map<String, Object>> warningDetails(AiAssistService.RuleBindingRecommendationResult result) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (String warning : result.getWarnings()) {
            for (String category : warningCategories(warning, result)) {
                details.add(warningDetail(category, warning));
            }
        }
        if (result.isRequiresHumanReview()) {
            details.add(warningDetail("人工确认必需", "推荐规则需要人工确认后再采纳"));
        }
        return details;
    }

    private List<String> warningCategories(AiAssistService.RuleBindingRecommendationResult result) {
        Set<String> categories = new LinkedHashSet<>();
        for (Map<String, Object> detail : warningDetails(result)) {
            categories.add(String.valueOf(detail.get("category")));
        }
        return new ArrayList<>(categories);
    }

    private Map<String, Object> warningDetail(String category, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("category", category);
        detail.put("message", message);
        return detail;
    }

    private List<String> warningCategories(String message, AiAssistService.RuleBindingRecommendationResult result) {
        Set<String> categories = new LinkedHashSet<>();
        if ("LOW".equalsIgnoreCase(firstText(result.getConfidence(), ""))) {
            categories.add("人工确认必需");
        }
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (message != null && (message.contains("字段不存在") || message.contains("未知字段")
                || message.contains("缺少字段"))) {
            categories.add("字段缺失");
        }
        if (message != null && (message.contains("模板不在") || message.contains("模板弱化"))) {
            categories.add("模板不支持");
        }
        if (message != null && (message.contains("只读") || message.contains("安全") || message.contains("危险"))
                || text.contains("unsafe")) {
            categories.add("安全拒绝");
        }
        if (message != null && (message.contains("降级") || message.contains("校验失败"))) {
            categories.add("AI降级");
        }
        if (categories.isEmpty()) {
            categories.add("人工确认必需");
        }
        return new ArrayList<>(categories);
    }

    private void writeAiDebugTrace(Path debugDir, String ruleId,
                                   AiAssistService.RuleBindingRecommendationTrace trace) throws Exception {
        if (debugDir == null) {
            return;
        }
        Files.createDirectories(debugDir);
        String fileName = safeFileName(ruleId);
        String prompt = "SYSTEM\n" + trace.getSystemPrompt() + "\n\nUSER\n" + trace.getUserPrompt();
        Files.writeString(debugDir.resolve(fileName + "-prompt.txt"), prompt);
        Files.writeString(debugDir.resolve(fileName + "-response.json"), trace.getModelResponse().orElse(""));
    }

    private void writeCandidateRulePackage(GenericRulePackage rulePackage, Path outputPath) throws Exception {
        Path absolutePath = outputPath.toAbsolutePath();
        if (absolutePath.getParent() != null) {
            Files.createDirectories(absolutePath.getParent());
        }
        if (rulePackage.getRules().isEmpty()) {
            Files.writeString(absolutePath, "schemaVersion: '" + firstText(rulePackage.getSchemaVersion(), "1")
                    + "'\nrules: []\n");
            return;
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Files.writeString(absolutePath, new Yaml(options).dump(rulePackageMap(rulePackage)));
    }

    private Map<String, Object> rulePackageMap(GenericRulePackage rulePackage) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", firstText(rulePackage.getSchemaVersion(), "1"));
        List<Map<String, Object>> rules = new ArrayList<>();
        for (GenericRulePackage.GenericRule rule : rulePackage.getRules()) {
            rules.add(ruleMap(rule));
        }
        value.put("rules", rules);
        return value;
    }

    private Map<String, Object> ruleMap(GenericRulePackage.GenericRule rule) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("ruleId", rule.getRuleId());
        value.put("ruleName", rule.getRuleName());
        value.put("category", rule.getCategory());
        value.put("severity", rule.getSeverity());
        value.put("description", rule.getDescription());
        value.put("applicableTables", emptyListIfNull(rule.getApplicableTables()));
        value.put("scenarioIds", emptyListIfNull(rule.getScenarioIds()));
        value.put("enabled", rule.isEnabled());
        value.put("templateCode", rule.getTemplateCode());
        value.put("templateParams", emptyMapIfNull(rule.getTemplateParams()));
        return value;
    }

    private List<String> copyList(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private List<String> emptyListIfNull(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private Map<String, Object> emptyMapIfNull(Map<String, Object> values) {
        return values == null ? Collections.emptyMap() : values;
    }

    private Path optionPath(String[] args, String name) {
        String value = option(args, name);
        return isBlank(value) ? null : Path.of(value);
    }

    private boolean samePath(Path first, Path second) {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
    }

    private String safeFileName(String value) {
        String text = firstText(value, "rule");
        return text.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String toolVersion() {
        String version = GenericValidationCli.class.getPackage().getImplementationVersion();
        return isBlank(version) ? FALLBACK_VERSION : version;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    private boolean hasFlag(String[] args, String name) {
        for (String arg : args) {
            if (name.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private String commandSummary(String[] args) {
        List<String> values = new ArrayList<>();
        boolean maskNext = false;
        for (String arg : args) {
            if (maskNext) {
                values.add("***");
                maskNext = false;
                continue;
            }
            String lower = arg.toLowerCase();
            if (lower.contains("password") || lower.contains("token") || lower.contains("secret")) {
                if (arg.contains("=")) {
                    values.add(arg.substring(0, arg.indexOf('=') + 1) + "***");
                } else {
                    values.add(arg);
                    maskNext = true;
                }
                continue;
            }
            values.add(arg);
        }
        return String.join(" ", values);
    }

    private void printUsage() {
        System.out.println("用法:");
        System.out.println("  data-validator run --config validator.yml [--json] [--quiet] [--no-report]");
        System.out.println("  data-validator validate --rules rules.yml --source source.yml --output reports [--json] [--quiet] [--no-report]");
        System.out.println("  data-validator recommend --rules rules.yml --metadata source.yml --output recommendations.json [--candidate-rules rules.recommended.yml] [--debug-ai debug-dir]");
        System.out.println("  data-validator lint --config validator.yml");
        System.out.println("  data-validator lint --rules rules.yml --metadata source.yml");
    }
}
