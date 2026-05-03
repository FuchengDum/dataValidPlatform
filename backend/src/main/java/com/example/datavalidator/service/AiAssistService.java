package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.DataTableSnapshotEntity;
import com.example.datavalidator.persistence.FindingEvidenceEntity;
import com.example.datavalidator.persistence.RuleDefinitionEntity;
import com.example.datavalidator.persistence.ValidationFindingEntity;
import com.example.datavalidator.repository.DataTableSnapshotRepository;
import com.example.datavalidator.repository.RuleDefinitionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;

@Service
public class AiAssistService {
    private static final List<String> DANGEROUS_KEYWORDS = Arrays.asList(
            "update", "delete", "insert", "drop", "alter", "truncate", "merge", "create",
            "call", "exec", "execute", "replace", "grant", "revoke"
    );
    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
            "(?i)\\b(update|delete|insert|drop|alter|truncate|merge|create|call|exec|execute|replace|grant|revoke)\\b");
    private static final Pattern SELECT_PREFIX_PATTERN = Pattern.compile("(?is)^\\s*select\\b.*");
    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile("(?s)(--|/\\*|\\*/)");
    private static final String SOURCE_LOCAL = "LOCAL_RULE_BASED";
    private static final String SOURCE_AI = "OPENAI_COMPATIBLE";
    private static final List<String> SUPPORTED_RECOMMENDATION_TEMPLATES = Arrays.asList(
            "NOT_NULL", "NON_NEGATIVE", "NUMERIC_TYPE", "FIELD_EXPRESSION", "ROW_EXPRESSION",
            "EXISTS_IN_TABLE", "FIELD_EQUALS", "AGGREGATION_EQUALS", "DUPLICATE_CHECK");

    private final AiChatClient aiChatClient;
    private final ObjectMapper objectMapper;
    private final RuleDefinitionRepository ruleRepository;
    private final DataTableSnapshotRepository tableRepository;
    private final RuleTemplateSemanticMapper semanticMapper = new RuleTemplateSemanticMapper();

    @Autowired
    public AiAssistService(AiChatClient aiChatClient,
                           ObjectMapper objectMapper,
                           RuleDefinitionRepository ruleRepository,
                           DataTableSnapshotRepository tableRepository) {
        this.aiChatClient = aiChatClient;
        this.objectMapper = objectMapper;
        this.ruleRepository = ruleRepository;
        this.tableRepository = tableRepository;
    }

    public AiAssistService(AiChatClient aiChatClient, ObjectMapper objectMapper) {
        this(aiChatClient, objectMapper, null, null);
    }

    public AnalysisResult analyzeFinding(ValidationFindingEntity finding, List<FindingEvidenceEntity> evidences) {
        AnalysisResult local = localAnalysis(finding, evidences);
        Optional<AnalysisResult> generated = aiChatClient
                .complete(analysisSystemPrompt(), analysisUserPrompt(finding, evidences))
                .flatMap(this::parseAnalysisResult);
        return generated.orElse(local);
    }

    public SqlDraftResult draftValidationSql(SqlDraftRequest request) {
        rejectDangerousIntent(request.getUserIntent());
        requireText(request.getTableName(), "tableName");
        requireText(request.getFieldName(), "fieldName");
        SqlDraftResult local = localSqlDraft(request);
        Optional<String> modelResponse = aiChatClient.complete(sqlSystemPrompt(), sqlUserPrompt(request));
        if (modelResponse.isEmpty()) {
            return local;
        }
        Optional<String> sql = extractSql(modelResponse.get());
        if (sql.isPresent() && isReadonlySelect(sql.get())) {
            SqlDraftResult result = new SqlDraftResult();
            result.setSql(sql.get());
            result.setExecutable(false);
            result.setSource(SOURCE_AI);
            result.setGeneratedByAi(true);
            result.setDraftType(normalizeDraftType(request.getDraftType()));
            result.getWarnings().add("SQL 草案仅用于人工核查");
            return result;
        }
        local.getWarnings().add("模型返回 SQL 未通过只读安全校验，已降级为本地 SQL 草案");
        return local;
    }

    public RuleBindingRecommendationResult recommendRuleBinding(RuleBindingRecommendationRequest request) {
        if (request == null) {
            throw new BadRequestException("规则推荐请求不能为空");
        }
        requireText(request.getDatasetId(), "datasetId");
        requireText(request.getRuleId(), "ruleId");
        requireRecommendationRepositories();
        RuleDefinitionEntity rule = ruleRepository.findById(
                        new RuleDefinitionEntity.Key(request.getRuleId(), request.getDatasetId()))
                .orElseThrow(() -> new BadRequestException("规则不存在: " + request.getRuleId()));
        Map<String, List<String>> tableFields = loadTableFields(request.getDatasetId());
        RuleTemplateSemanticMatch localMatch = semanticMapper.recommend(rule, tableFields);
        RuleBindingRecommendationResult local = localRecommendation(localMatch);
        Optional<String> modelResponse = aiChatClient
                .complete(recommendationSystemPrompt(), recommendationUserPrompt(rule, tableFields));
        if (modelResponse.isEmpty()) {
            return local;
        }
        RecommendationParseResult generated = parseRecommendationResult(modelResponse.get(), tableFields, localMatch);
        if (generated.result.isPresent()) {
            return generated.result.get();
        }
        local.getWarnings().add(fallbackWarning(generated.failureReason));
        return local;
    }

    private AnalysisResult localAnalysis(ValidationFindingEntity finding, List<FindingEvidenceEntity> evidences) {
        AnalysisResult result = new AnalysisResult();
        result.setSource(SOURCE_LOCAL);
        result.setGeneratedByAi(false);
        result.setReason("规则 " + finding.getRuleId() + "（" + finding.getRuleName() + "）命中字段 "
                + safe(finding.getFieldName()) + "，实际值为 " + safe(finding.getActualValue())
                + "，期望值为 " + safe(finding.getExpectedValue()) + "。");
        result.setImpact(impactBy(finding));
        result.setSuggestion("建议先核查 " + safe(finding.getTableName()) + " 表记录 "
                + safe(finding.getRecordKey()) + " 的字段 " + safe(finding.getFieldName())
                + "，再回溯上游造数、同步或业务写入逻辑。");
        result.setEvidenceSummary(summarizeEvidences(evidences));
        return result;
    }

    private SqlDraftResult localSqlDraft(SqlDraftRequest request) {
        String condition = conditionFor(request.getFieldName(), request.getExpectedValue());
        SqlDraftResult result = new SqlDraftResult();
        result.setExecutable(false);
        result.setSource(SOURCE_LOCAL);
        result.setGeneratedByAi(false);
        result.setDraftType(normalizeDraftType(request.getDraftType()));
        result.setSql("SELECT *\nFROM " + quoteIdentifier(request.getTableName()) + "\nWHERE " + condition);
        result.getWarnings().add("SQL 草案仅用于人工核查");
        if (!isBlank(request.getRecordKey())) {
            result.getWarnings().add("记录主键 " + request.getRecordKey() + " 可作为人工复核线索。");
        }
        return result;
    }

    private RuleBindingRecommendationResult localRecommendation(RuleTemplateSemanticMatch match) {
        RuleBindingRecommendationResult result = new RuleBindingRecommendationResult();
        result.setSource(SOURCE_LOCAL);
        result.setGeneratedByAi(false);
        result.setRequiresHumanReview(true);
        result.setConfidence(match.getConfidence());
        result.setTemplateCode(match.getTemplateCode());
        result.setTemplateParams(match.getTemplateParams());
        result.setExplanation(match.getMatchedReason());
        result.getWarnings().addAll(match.getWarnings());
        if (!match.isApplicable()) {
            result.setExplanation("当前规则无法自动映射到可执行模板，建议继续使用内置执行器。");
        }
        return result;
    }

    private RecommendationParseResult parseRecommendationResult(
            String content, Map<String, List<String>> tableFields, RuleTemplateSemanticMatch localMatch) {
        try {
            Map<String, Object> values = objectMapper.readValue(stripCodeFence(content),
                    new TypeReference<Map<String, Object>>() {});
            RuleBindingRecommendationResult result = new RuleBindingRecommendationResult();
            result.setTemplateCode(stringValue(values, "templateCode"));
            result.setTemplateParams(objectMap(values.get("templateParams")));
            Optional<String> failure = validationFailure(result, tableFields, localMatch);
            if (failure.isPresent() && normalizeEquivalentFieldExpression(result, tableFields, localMatch)) {
                failure = validationFailure(result, tableFields, localMatch);
            }
            if (failure.isPresent()) {
                return RecommendationParseResult.failure(failure.get());
            }
            result.setSource(SOURCE_AI);
            result.setGeneratedByAi(true);
            result.setRequiresHumanReview(true);
            result.setConfidence(normalizeConfidence(stringValue(values, "confidence")));
            result.setExplanation(stringValue(values, "explanation"));
            if (isBlank(result.getExplanation())) {
                result.setExplanation("模型根据规则文本和字段元数据生成的模板绑定建议，请人工确认后应用。");
            }
            return RecommendationParseResult.success(result);
        } catch (Exception ex) {
            return RecommendationParseResult.failure("模型返回内容不是合法推荐 JSON");
        }
    }

    private Optional<AnalysisResult> parseAnalysisResult(String content) {
        try {
            Map<String, Object> values = objectMapper.readValue(stripCodeFence(content),
                    new TypeReference<Map<String, Object>>() {});
            AnalysisResult result = new AnalysisResult();
            result.setSource(SOURCE_AI);
            result.setGeneratedByAi(true);
            result.setReason(stringValue(values, "reason"));
            result.setImpact(stringValue(values, "impact"));
            result.setSuggestion(stringValue(values, "suggestion"));
            result.setEvidenceSummary(stringValue(values, "evidenceSummary"));
            if (isBlank(result.getReason()) || isBlank(result.getImpact()) || isBlank(result.getSuggestion())) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> extractSql(String content) {
        String text = stripCodeFence(content);
        try {
            Map<String, Object> values = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            String sql = stringValue(values, "sql");
            return isBlank(sql) ? Optional.empty() : Optional.of(stripCodeFence(sql).trim());
        } catch (Exception ex) {
            return isBlank(text) ? Optional.empty() : Optional.of(text.trim());
        }
    }

    private boolean isReadonlySelect(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        if (!SELECT_PREFIX_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        if (normalized.contains(";") || SQL_COMMENT_PATTERN.matcher(normalized).find()) {
            return false;
        }
        return !DANGEROUS_SQL_PATTERN.matcher(normalized).find();
    }

    private String analysisSystemPrompt() {
        return "你是业务数据准确性验证工具的 AI 助手。"
                + "请只输出 JSON，对异常给出原因、影响、建议和证据摘要。"
                + "JSON 字段必须是 reason、impact、suggestion、evidenceSummary。";
    }

    private String analysisUserPrompt(ValidationFindingEntity finding, List<FindingEvidenceEntity> evidences) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", finding.getRuleId());
        payload.put("ruleName", finding.getRuleName());
        payload.put("description", finding.getDescription());
        payload.put("severity", finding.getSeverity());
        payload.put("tableName", finding.getTableName());
        payload.put("recordKey", finding.getRecordKey());
        payload.put("fieldName", finding.getFieldName());
        payload.put("actualValue", finding.getActualValue());
        payload.put("expectedValue", finding.getExpectedValue());
        payload.put("evidenceSummary", summarizeEvidences(evidences));
        return writeJson(payload);
    }

    private String sqlSystemPrompt() {
        return "你是业务数据核查 SQL 助手。只允许输出 JSON，字段为 sql。"
                + "sql 必须是只读 SELECT 查询，禁止 UPDATE、DELETE、INSERT、DROP、ALTER、TRUNCATE、MERGE、CREATE，"
                + "不要输出修复 SQL，不要解释。";
    }

    private String recommendationSystemPrompt() {
        return "你是业务规则模板推荐助手。只允许输出 JSON，字段为 templateCode、templateParams、confidence、explanation。"
                + "templateCode 只能是 NOT_NULL、NON_NEGATIVE、NUMERIC_TYPE、FIELD_EXPRESSION、"
                + "ROW_EXPRESSION、EXISTS_IN_TABLE、FIELD_EQUALS、AGGREGATION_EQUALS、DUPLICATE_CHECK。"
                + "字段级模板参数必须包含 tableName 和 fields；FIELD_EXPRESSION 参数必须包含 tableName 和 expression。"
                + "ROW_EXPRESSION 参数必须包含 tableName 和 conditions；conditions 每项包含 left、operator、right，"
                + "可选 when 表达仅在满足条件时执行；operator 支持 ==、!=、>、>=、<、<=、in、notIn、isNull、isNotNull。"
                + "表达式节点可使用 field、literal/value，或 op + left + right 表达 +、-、*、/，"
                + "也可使用 if + then + else 表达条件分支，if 内包含 left、operator、right。"
                + "EXISTS_IN_TABLE 参数必须包含 source、target、key；"
                + "FIELD_EQUALS 参数必须包含 source、target、key、sourceField、targetField；"
                + "AGGREGATION_EQUALS 参数必须包含 source、target、groupBy、sum、targetField，可选 targetKey；"
                + "DUPLICATE_CHECK 参数必须包含 tableName 和 groupBy，可选 where 过滤条件。"
                + "金额关系、状态条件、时间逻辑、库存连续性、汇总关系、跨表关系必须推荐能表达业务关系的模板，不能降级为单纯类型检查。"
                + "所有参数只能使用用户提供的表名和字段名。";
    }

    private String sqlUserPrompt(SqlDraftRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("draftType", normalizeDraftType(request.getDraftType()));
        payload.put("tableName", request.getTableName());
        payload.put("fieldName", request.getFieldName());
        payload.put("actualValue", request.getActualValue());
        payload.put("expectedValue", request.getExpectedValue());
        payload.put("recordKey", request.getRecordKey());
        payload.put("userIntent", request.getUserIntent());
        return writeJson(payload);
    }

    private String recommendationUserPrompt(RuleDefinitionEntity rule, Map<String, List<String>> tableFields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", rule.getRuleId());
        payload.put("ruleName", rule.getRuleName());
        payload.put("description", rule.getDescription());
        payload.put("pseudoLogic", rule.getPseudoLogic());
        payload.put("applicableTables", rule.getApplicableTables());
        payload.put("availableTables", tableFields);
        return writeJson(payload);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String stripCodeFence(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        return text.trim();
    }

    private String normalizeDraftType(String draftType) {
        return "MANUAL_REVIEW".equals(draftType) ? "MANUAL_REVIEW" : "VALIDATION_CHECK";
    }

    private String normalizeConfidence(String confidence) {
        String normalized = confidence == null ? "" : confidence.trim().toUpperCase(Locale.ROOT);
        if ("HIGH".equals(normalized) || "MEDIUM".equals(normalized) || "LOW".equals(normalized)) {
            return normalized;
        }
        return "MEDIUM";
    }

    private Map<String, List<String>> loadTableFields(String datasetId) {
        Map<String, List<String>> tables = new LinkedHashMap<>();
        for (DataTableSnapshotEntity table : tableRepository.findByDatasetId(datasetId)) {
            tables.put(table.getLogicalName(), jsonStringList(table.getHeadersJson()));
        }
        return tables;
    }

    private List<String> jsonStringList(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private Optional<String> validationFailure(RuleBindingRecommendationResult result,
                                               Map<String, List<String>> tableFields,
                                               RuleTemplateSemanticMatch localMatch) {
        if (!SUPPORTED_RECOMMENDATION_TEMPLATES.contains(result.getTemplateCode())) {
            return Optional.of("模板不在可推荐白名单中: " + result.getTemplateCode());
        }
        try {
            TemplateBindingValidator.validate(result.getTemplateCode(), result.getTemplateParams(), tableFields);
        } catch (BadRequestException ex) {
            return Optional.of(ex.getMessage());
        }
        if (localMatch.isApplicable() && "HIGH".equals(localMatch.getConfidence())
                && !localMatch.getTemplateCode().equals(result.getTemplateCode())) {
            return Optional.of("模型推荐模板弱化了本地高置信语义映射");
        }
        if (localMatch.isApplicable() && "FIELD_EXPRESSION".equals(localMatch.getTemplateCode())
                && "FIELD_EXPRESSION".equals(result.getTemplateCode())) {
            String required = objectString(localMatch.getTemplateParams().get("expression"));
            String expression = objectString(result.getTemplateParams().get("expression"));
            if (!containsAllConditions(expression, required)) {
                return Optional.of("模型表达式未覆盖本地语义映射条件");
            }
        }
        if (localMatch.isApplicable() && "ROW_EXPRESSION".equals(localMatch.getTemplateCode())
                && "ROW_EXPRESSION".equals(result.getTemplateCode())) {
            if (!containsAllRowConditions(result.getTemplateParams().get("conditions"),
                    localMatch.getTemplateParams().get("conditions"))) {
                return Optional.of("模型行表达式未覆盖本地语义映射条件");
            }
        }
        return Optional.empty();
    }

    private boolean normalizeEquivalentFieldExpression(RuleBindingRecommendationResult result,
                                                       Map<String, List<String>> tableFields,
                                                       RuleTemplateSemanticMatch localMatch) {
        if (!localMatch.isApplicable() || !"ROW_EXPRESSION".equals(localMatch.getTemplateCode())
                || !"FIELD_EXPRESSION".equals(result.getTemplateCode())) {
            return false;
        }
        try {
            TemplateBindingValidator.validate(result.getTemplateCode(), result.getTemplateParams(), tableFields);
        } catch (BadRequestException ex) {
            return false;
        }
        String expression = objectString(result.getTemplateParams().get("expression"));
        String requiredExpression = String.join(" && ",
                rowConditionTexts(localMatch.getTemplateParams().get("conditions")));
        if (!containsAllConditions(expression, requiredExpression)) {
            return false;
        }
        result.setTemplateCode(localMatch.getTemplateCode());
        result.setTemplateParams(new LinkedHashMap<>(localMatch.getTemplateParams()));
        return true;
    }

    private String fallbackWarning(String failureReason) {
        if (isBlank(failureReason)) {
            return "模型推荐未通过校验，已降级为本地推荐";
        }
        return "模型推荐校验失败：" + failureReason + "，已降级为本地推荐";
    }

    private boolean containsAllRowConditions(Object conditions, Object requiredConditions) {
        List<String> actual = rowConditionTexts(conditions);
        for (String required : rowConditionTexts(requiredConditions)) {
            if (!actual.contains(required)) {
                return false;
            }
        }
        return true;
    }

    private List<String> rowConditionTexts(Object rawConditions) {
        List<String> result = new ArrayList<>();
        if (!(rawConditions instanceof List)) {
            return result;
        }
        for (Object item : (List<?>) rawConditions) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> condition = (Map<?, ?>) item;
            String text = rowConditionText(condition);
            if (condition.containsKey("when")) {
                text = "when " + rowConditionText(objectMapRaw(condition.get("when"))) + " then " + text;
            }
            result.add(text);
        }
        return result;
    }

    private String rowConditionText(Map<?, ?> condition) {
        String operator = objectString(condition.get("operator"));
        if ("isNull".equals(operator) || "isNotNull".equals(operator)) {
            return rowExpressionText(condition.get("left")) + " " + operator;
        }
        return rowExpressionText(condition.get("left")) + " " + operator + " "
                + rowExpressionText(condition.get("right"));
    }

    private String rowExpressionText(Object rawExpression) {
        if (rawExpression instanceof List) {
            List<String> values = new ArrayList<>();
            for (Object item : (List<?>) rawExpression) {
                values.add(objectString(item));
            }
            return String.join(",", values);
        }
        if (!(rawExpression instanceof Map)) {
            return objectString(rawExpression);
        }
        Map<?, ?> expression = (Map<?, ?>) rawExpression;
        if (expression.containsKey("field")) {
            return objectString(expression.get("field"));
        }
        if (expression.containsKey("literal")) {
            return objectString(expression.get("literal"));
        }
        if (expression.containsKey("value")) {
            return objectString(expression.get("value"));
        }
        if (expression.containsKey("if")) {
            Map<?, ?> predicate = objectMapRaw(expression.get("if"));
            return "if " + rowExpressionText(predicate.get("left")) + " "
                    + objectString(predicate.get("operator")) + " "
                    + rowExpressionText(predicate.get("right")) + " then "
                    + rowExpressionText(expression.get("then")) + " else "
                    + rowExpressionText(expression.get("else"));
        }
        return rowExpressionText(expression.get("left")) + " "
                + objectString(expression.get("op")) + " "
                + rowExpressionText(expression.get("right"));
    }

    private Map<?, ?> objectMapRaw(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }

    private boolean containsAllConditions(String expression, String requiredExpression) {
        List<String> conditions = normalizedConditions(expression);
        for (String required : normalizedConditions(requiredExpression)) {
            if (!conditions.contains(required)) {
                return false;
            }
        }
        return true;
    }

    private List<String> normalizedConditions(String expression) {
        List<String> conditions = new ArrayList<>();
        for (String condition : safe(expression).split("\\s+&&\\s+")) {
            if (!isBlank(condition)) {
                conditions.add(condition.trim().replaceAll("\\s+", " "));
            }
        }
        return conditions;
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String objectString(Object value) {
        return value == null ? "" : value.toString();
    }

    private void requireRecommendationRepositories() {
        if (ruleRepository == null || tableRepository == null) {
            throw new IllegalStateException("规则推荐依赖未初始化");
        }
    }

    private String stringValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private String conditionFor(String fieldName, String expectedValue) {
        String field = quoteIdentifier(fieldName);
        if (">= 0".equals(expectedValue)) {
            return field + " < 0";
        }
        if ("> 0".equals(expectedValue)) {
            return field + " <= 0";
        }
        if ("非空".equals(expectedValue)) {
            return "(" + field + " IS NULL OR TRIM(" + field + ") = '')";
        }
        if ("数值类型".equals(expectedValue)) {
            return "(" + field + " IS NOT NULL AND TRIM(" + field + ") <> '')";
        }
        return field + " IS NOT NULL";
    }

    private String impactBy(ValidationFindingEntity finding) {
        String text = (safe(finding.getRuleName()) + " " + safe(finding.getDescription()) + " "
                + safe(finding.getTableName())).toLowerCase(Locale.ROOT);
        if (text.contains("支付") || text.contains("金额") || text.contains("退款")) {
            return "可能影响资金核对、支付对账和报表统计，建议优先处理。";
        }
        if (text.contains("库存") || text.contains("商品")) {
            return "可能影响库存扣减、商品履约和上下架状态判断。";
        }
        return "可能影响业务链路一致性和后续统计口径，建议结合证据链复核。";
    }

    private String summarizeEvidences(List<FindingEvidenceEntity> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return "暂无额外证据，建议以异常记录字段为准进行核查。";
        }
        StringJoiner joiner = new StringJoiner("；");
        for (FindingEvidenceEntity evidence : evidences) {
            joiner.add(safe(evidence.getEvidenceType()) + " / " + safe(evidence.getFieldName())
                    + " / actual=" + safe(evidence.getActualValue())
                    + " / expected=" + safe(evidence.getExpectedValue()));
        }
        return joiner.toString();
    }

    private void rejectDangerousIntent(String userIntent) {
        if (isBlank(userIntent)) {
            return;
        }
        String lower = userIntent.toLowerCase(Locale.ROOT);
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (lower.contains(keyword)) {
                throw new BadRequestException("只允许生成只读 SELECT SQL 草案");
            }
        }
    }

    private void requireText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new BadRequestException(fieldName + " 不能为空");
        }
    }

    private String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    public static class AnalysisResult {
        private String source;
        private boolean generatedByAi;
        private String reason;
        private String impact;
        private String suggestion;
        private String evidenceSummary;
        private List<String> warnings = new ArrayList<>();

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public boolean isGeneratedByAi() { return generatedByAi; }
        public void setGeneratedByAi(boolean generatedByAi) { this.generatedByAi = generatedByAi; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getImpact() { return impact; }
        public void setImpact(String impact) { this.impact = impact; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
        public String getEvidenceSummary() { return evidenceSummary; }
        public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }

    public static class SqlDraftRequest {
        private String draftType;
        private String tableName;
        private String fieldName;
        private String actualValue;
        private String expectedValue;
        private String recordKey;
        private String userIntent;

        public String getDraftType() { return draftType; }
        public void setDraftType(String draftType) { this.draftType = draftType; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getActualValue() { return actualValue; }
        public void setActualValue(String actualValue) { this.actualValue = actualValue; }
        public String getExpectedValue() { return expectedValue; }
        public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
        public String getRecordKey() { return recordKey; }
        public void setRecordKey(String recordKey) { this.recordKey = recordKey; }
        public String getUserIntent() { return userIntent; }
        public void setUserIntent(String userIntent) { this.userIntent = userIntent; }
    }

    public static class SqlDraftResult {
        private String draftType;
        private String sql;
        private boolean executable;
        private boolean generatedByAi;
        private String source;
        private List<String> warnings = new ArrayList<>();

        public String getDraftType() { return draftType; }
        public void setDraftType(String draftType) { this.draftType = draftType; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public boolean isExecutable() { return executable; }
        public void setExecutable(boolean executable) { this.executable = executable; }
        public boolean isGeneratedByAi() { return generatedByAi; }
        public void setGeneratedByAi(boolean generatedByAi) { this.generatedByAi = generatedByAi; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }

    public static class RuleBindingRecommendationRequest {
        private String datasetId;
        private String ruleId;

        public String getDatasetId() { return datasetId; }
        public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    }

    public static class RuleBindingRecommendationResult {
        private String templateCode;
        private Map<String, Object> templateParams = new LinkedHashMap<>();
        private String confidence;
        private String explanation;
        private boolean requiresHumanReview = true;
        private String source;
        private boolean generatedByAi;
        private List<String> warnings = new ArrayList<>();

        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
        public Map<String, Object> getTemplateParams() { return templateParams; }
        public void setTemplateParams(Map<String, Object> templateParams) { this.templateParams = templateParams; }
        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        public boolean isRequiresHumanReview() { return requiresHumanReview; }
        public void setRequiresHumanReview(boolean requiresHumanReview) { this.requiresHumanReview = requiresHumanReview; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public boolean isGeneratedByAi() { return generatedByAi; }
        public void setGeneratedByAi(boolean generatedByAi) { this.generatedByAi = generatedByAi; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }

    private static class RecommendationParseResult {
        private final Optional<RuleBindingRecommendationResult> result;
        private final String failureReason;

        private RecommendationParseResult(Optional<RuleBindingRecommendationResult> result, String failureReason) {
            this.result = result;
            this.failureReason = failureReason;
        }

        static RecommendationParseResult success(RuleBindingRecommendationResult result) {
            return new RecommendationParseResult(Optional.of(result), "");
        }

        static RecommendationParseResult failure(String failureReason) {
            return new RecommendationParseResult(Optional.empty(), failureReason);
        }
    }
}
