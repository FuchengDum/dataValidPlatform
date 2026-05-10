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
            "EXISTS_IN_TABLE", "RELATION_EXISTS", "FIELD_EQUALS", "JOIN_ASSERT", "AGGREGATION_EQUALS",
            "AGGREGATE_ASSERT", "DUPLICATE_ASSERT", "DUPLICATE_CHECK");

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
            normalizeRecommendationParams(result);
            normalizeEquivalentRelationExists(result, localMatch);
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

    private void normalizeRecommendationParams(RuleBindingRecommendationResult result) {
        Map<String, Object> params = result.getTemplateParams();
        if ("FIELD_EXPRESSION".equals(result.getTemplateCode()) && params.get("expression") instanceof Map) {
            Object expression = params.get("expression");
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("tableName", params.get("tableName"));
            normalized.put("conditions", Collections.singletonList(expression));
            result.setTemplateCode("ROW_EXPRESSION");
            result.setTemplateParams(normalized);
            return;
        }
        if ("AGGREGATION_EQUALS".equals(result.getTemplateCode())) {
            result.setTemplateCode("AGGREGATE_ASSERT");
            result.setTemplateParams(normalizeAggregationEqualsParams(params));
            return;
        }
        if ("FIELD_EQUALS".equals(result.getTemplateCode())) {
            result.setTemplateCode("JOIN_ASSERT");
            result.setTemplateParams(normalizeFieldEqualsParams(params));
            return;
        }
        if ("RELATION_EXISTS".equals(result.getTemplateCode())) {
            normalizeRelationExistsParams(params);
            return;
        }
        if (!"ROW_EXPRESSION".equals(result.getTemplateCode())) {
            return;
        }
        Object conditions = params.get("conditions");
        if (conditions instanceof Map) {
            params.put("conditions", Collections.singletonList(conditions));
        }
    }

    private Map<String, Object> normalizeAggregationEqualsParams(Map<String, Object> params) {
        String groupBy = objectString(params.get("groupBy"));
        String targetKey = objectString(params.get("targetKey"));
        if (isBlank(targetKey)) {
            targetKey = groupBy;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("source", params.get("source"));
        normalized.put("target", params.get("target"));
        normalized.put("groupBy", Collections.singletonList(relationKey(groupBy, targetKey)));
        normalized.put("aggregate", aggregate("SUM", params.get("sum")));
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("op", "==");
        assertion.put("tolerance", params.getOrDefault("tolerance", 0.01));
        assertion.put("targetField", params.get("targetField"));
        normalized.put("assert", assertion);
        return normalized;
    }

    private Map<String, Object> normalizeFieldEqualsParams(Map<String, Object> params) {
        String key = objectString(params.get("key"));
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("source", params.get("source"));
        normalized.put("target", params.get("target"));
        normalized.put("keys", Collections.singletonList(relationKey(key, key)));
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("left", joinSourceField(params.get("sourceField")));
        assertion.put("op", "==");
        assertion.put("right", joinTargetField(params.get("targetField")));
        normalized.put("assert", assertion);
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private void normalizeRelationExistsParams(Map<String, Object> params) {
        normalizePredicateParam(params, "sourceWhere");
        normalizePredicateParam(params, "targetWhere");
        Object sourceExists = params.get("sourceExists");
        if (sourceExists instanceof Map) {
            normalizePredicateParam((Map<String, Object>) sourceExists, "targetWhere");
        }
    }

    private void normalizePredicateParam(Map<String, Object> params, String key) {
        Object predicate = params.get(key);
        if (predicate != null) {
            params.put(key, normalizePredicate(predicate));
        }
    }

    @SuppressWarnings("unchecked")
    private Object normalizePredicate(Object predicate) {
        if (predicate instanceof List) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : (List<?>) predicate) {
                normalized.add(normalizePredicate(item));
            }
            return normalized;
        }
        if (!(predicate instanceof Map)) {
            return predicate;
        }
        Map<String, Object> value = objectMap(predicate);
        if (!value.containsKey("left") && value.containsKey("field") && value.containsKey("operator")) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("left", relationField(value.get("field")));
            normalized.put("operator", value.get("operator"));
            normalized.put("right", relationPredicateRight(value));
            return normalized;
        }
        for (String key : Arrays.asList("and", "or")) {
            if (value.get(key) instanceof List) {
                List<Object> normalized = new ArrayList<>();
                for (Object item : (List<?>) value.get(key)) {
                    normalized.add(normalizePredicate(item));
                }
                value.put(key, normalized);
            }
        }
        if (value.containsKey("not")) {
            value.put("not", normalizePredicate(value.get("not")));
        }
        return value;
    }

    private Map<String, Object> relationField(Object field) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        return result;
    }

    private Object relationPredicateRight(Map<String, Object> predicate) {
        Object rawValue = predicate.containsKey("value") ? predicate.get("value") : predicate.get("literal");
        if (rawValue instanceof List || rawValue instanceof Map) {
            return rawValue;
        }
        return literal(rawValue);
    }

    private Map<String, Object> relationKey(String sourceField, String targetField) {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("sourceField", sourceField);
        key.put("targetField", targetField);
        return key;
    }

    private Map<String, Object> joinSourceField(Object field) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("sourceField", field);
        return expression;
    }

    private Map<String, Object> joinTargetField(Object field) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("targetField", field);
        return expression;
    }

    private Map<String, Object> aggregate(String fn, Object field) {
        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("fn", fn);
        aggregate.put("field", field);
        return aggregate;
    }

    private Map<String, Object> literal(Object value) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("literal", value);
        return expression;
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
                + "ROW_EXPRESSION、EXISTS_IN_TABLE、RELATION_EXISTS、FIELD_EQUALS、JOIN_ASSERT、AGGREGATION_EQUALS、AGGREGATE_ASSERT、DUPLICATE_ASSERT、DUPLICATE_CHECK。"
                + "字段级模板参数必须包含 tableName 和 fields；FIELD_EXPRESSION 参数必须包含 tableName 和字符串 expression，结构化条件对象必须使用 ROW_EXPRESSION。"
                + "ROW_EXPRESSION 参数必须包含 tableName 和 conditions；conditions 必须是数组，即使只有一条条件也要用数组；每项包含 left、operator、right，"
                + "可选 when 表达仅在满足条件时执行；operator 支持 ==、!=、>、>=、<、<=、in、notIn、isNull、isNotNull。"
                + "表达式节点可使用 field、literal/value，或 op + left + right 表达 +、-、*、/，"
                + "也可使用 if + then + else 表达条件分支，if 内包含 left、operator、right。"
                + "where/when 支持单个条件，也支持 {and:[...]}/{or:[...]}/{not:{...}} 组合条件。"
                + "EXISTS_IN_TABLE 参数必须包含 source、target、key；"
                + "RELATION_EXISTS 参数必须包含 source、target、keys、expectExists；"
                + "keys 每项包含 sourceField、targetField；可选 sourceWhere、targetWhere、sourceExists。"
                + "sourceExists 包含 target、keys、targetWhere，用于先按第三张表过滤源记录。"
                + "FIELD_EQUALS 参数必须包含 source、target、key、sourceField、targetField；"
                + "JOIN_ASSERT 参数必须包含 source、target、keys、assert；keys 每项包含 sourceField、targetField；"
                + "assert 包含 left、op、right，可选 tolerance；left/right 可使用 sourceField、targetField、literal/value 或 op + left + right。"
                + "AGGREGATION_EQUALS 参数必须包含 source、target、groupBy、sum、targetField，可选 targetKey；"
                + "AGGREGATE_ASSERT 参数必须包含 source、target、groupBy、aggregate、assert；"
                + "groupBy 可为字段名或 {sourceField,targetField} 数组，aggregate 包含 fn 和 field；"
                + "assert 包含 op、tolerance，且必须提供 targetField 或 aggregate；"
                + "聚合汇总一致性优先使用 AGGREGATE_ASSERT，AGGREGATION_EQUALS 仅作为旧格式兼容；"
                + "DUPLICATE_ASSERT 参数必须包含 table、groupBy、assert，可选 where；"
                + "assert 可为 {op:'<=',count:1} 或 {count:'<= 1'}，用于比较分组记录数；"
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
                && !localMatch.getTemplateCode().equals(result.getTemplateCode())
                && !crossTemplateCoversLocal(result, localMatch)) {
            return Optional.of("模型推荐模板弱化了本地高置信语义映射");
        }
        if (localMatch.isApplicable() && "HIGH".equals(localMatch.getConfidence())
                && "RELATION_EXISTS".equals(localMatch.getTemplateCode())
                && "RELATION_EXISTS".equals(result.getTemplateCode())
                && !relationExistsCoversLocal(result.getTemplateParams(), localMatch.getTemplateParams())) {
            return Optional.of("模型关系存在模板未覆盖本地语义映射条件");
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

    private boolean crossTemplateCoversLocal(RuleBindingRecommendationResult result,
                                             RuleTemplateSemanticMatch localMatch) {
        return "NOT_NULL".equals(localMatch.getTemplateCode())
                && "ROW_EXPRESSION".equals(result.getTemplateCode())
                && rowExpressionCoversNotNull(result.getTemplateParams(), localMatch.getTemplateParams());
    }

    private boolean rowExpressionCoversNotNull(Map<String, Object> params, Map<String, Object> localParams) {
        if (!objectString(params.get("tableName")).equals(objectString(localParams.get("tableName")))) {
            return false;
        }
        List<String> actualFields = unconditionalIsNotNullFields(params.get("conditions"));
        for (String field : stringValues(localParams.get("fields"))) {
            if (!actualFields.contains(field)) {
                return false;
            }
        }
        return true;
    }

    private List<String> unconditionalIsNotNullFields(Object rawConditions) {
        List<String> fields = new ArrayList<>();
        for (Map<?, ?> condition : rowConditionMaps(rawConditions)) {
            if (!condition.containsKey("when")
                    && "isNotNull".equals(objectString(condition.get("operator")))) {
                fields.add(rowExpressionText(condition.get("left")));
            }
        }
        return fields;
    }

    private List<String> stringValues(Object rawValues) {
        List<String> values = new ArrayList<>();
        if (rawValues instanceof List) {
            for (Object item : (List<?>) rawValues) {
                String value = objectString(item);
                if (!isBlank(value)) {
                    values.add(value);
                }
            }
            return values;
        }
        String value = objectString(rawValues);
        if (!isBlank(value)) {
            values.add(value);
        }
        return values;
    }

    private boolean normalizeEquivalentFieldExpression(RuleBindingRecommendationResult result,
                                                       Map<String, List<String>> tableFields,
                                                       RuleTemplateSemanticMatch localMatch) {
        if (!localMatch.isApplicable() || !"ROW_EXPRESSION".equals(localMatch.getTemplateCode())
                || !"FIELD_EXPRESSION".equals(result.getTemplateCode())) {
            return false;
        }
        String tableName = objectString(result.getTemplateParams().get("tableName"));
        if (!tableFields.containsKey(tableName)) {
            return false;
        }
        String expression = objectString(result.getTemplateParams().get("expression"));
        String requiredExpression = String.join(" && ",
                rowConditionTexts(localMatch.getTemplateParams().get("conditions")));
        if (!containsAllConditions(expression, requiredExpression)
                && !containsAllEquivalentConditions(expression, requiredExpression)) {
            return false;
        }
        result.setTemplateCode(localMatch.getTemplateCode());
        result.setTemplateParams(new LinkedHashMap<>(localMatch.getTemplateParams()));
        return true;
    }

    private boolean normalizeEquivalentRelationExists(RuleBindingRecommendationResult result,
                                                      RuleTemplateSemanticMatch localMatch) {
        if (!localMatch.isApplicable() || !"HIGH".equals(localMatch.getConfidence())
                || !"RELATION_EXISTS".equals(localMatch.getTemplateCode())
                || !"RELATION_EXISTS".equals(result.getTemplateCode())) {
            return false;
        }
        Map<String, Object> params = result.getTemplateParams();
        Map<String, Object> localParams = localMatch.getTemplateParams();
        if (!relationExistsEquivalentSameDirection(params, localParams)
                && !relationExistsEquivalentReversed(params, localParams)) {
            return false;
        }
        result.setTemplateParams(new LinkedHashMap<>(localParams));
        return true;
    }

    private boolean relationExistsEquivalentSameDirection(Map<String, Object> params, Map<String, Object> localParams) {
        return objectString(params.get("source")).equals(objectString(localParams.get("source")))
                && objectString(params.get("target")).equals(objectString(localParams.get("target")))
                && relationExpectExists(params) == relationExpectExists(localParams)
                && containsAllRelationKeys(params.get("keys"), localParams.get("keys"))
                && relationPredicateCompatible(params.get("sourceWhere"), localParams.get("sourceWhere"), true)
                && relationPredicateCompatible(params.get("targetWhere"), localParams.get("targetWhere"), true)
                && sourceExistsCompatible(params.get("sourceExists"), localParams.get("sourceExists"));
    }

    private boolean relationExistsEquivalentReversed(Map<String, Object> params, Map<String, Object> localParams) {
        return objectString(params.get("source")).equals(objectString(localParams.get("target")))
                && objectString(params.get("target")).equals(objectString(localParams.get("source")))
                && relationExpectExists(params) == relationExpectExists(localParams)
                && containsAllRelationKeysReversed(params.get("keys"), localParams.get("keys"))
                && relationPredicateCompatible(params.get("sourceWhere"), localParams.get("targetWhere"), true)
                && relationPredicateCompatible(params.get("targetWhere"), localParams.get("sourceWhere"), true)
                && params.get("sourceExists") == null
                && localParams.get("sourceExists") == null;
    }

    private boolean relationExpectExists(Map<String, Object> params) {
        return !Boolean.FALSE.equals(params.get("expectExists"));
    }

    private boolean containsAllRelationKeys(Object actualKeys, Object requiredKeys) {
        List<String> actual = relationKeyTexts(actualKeys);
        for (String required : relationKeyTexts(requiredKeys)) {
            if (!actual.contains(required)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAllRelationKeysReversed(Object actualKeys, Object requiredKeys) {
        List<String> actual = relationKeyTexts(actualKeys);
        for (String required : reversedRelationKeyTexts(requiredKeys)) {
            if (!actual.contains(required)) {
                return false;
            }
        }
        return true;
    }

    private List<String> relationKeyTexts(Object rawKeys) {
        List<String> result = new ArrayList<>();
        if (!(rawKeys instanceof List)) {
            return result;
        }
        for (Object item : (List<?>) rawKeys) {
            Map<?, ?> key = objectMapRaw(item);
            String sourceField = objectString(key.get("sourceField"));
            String targetField = objectString(key.get("targetField"));
            if (!isBlank(sourceField) && !isBlank(targetField)) {
                result.add(sourceField + "->" + targetField);
            }
        }
        return result;
    }

    private List<String> reversedRelationKeyTexts(Object rawKeys) {
        List<String> result = new ArrayList<>();
        if (!(rawKeys instanceof List)) {
            return result;
        }
        for (Object item : (List<?>) rawKeys) {
            Map<?, ?> key = objectMapRaw(item);
            String sourceField = objectString(key.get("sourceField"));
            String targetField = objectString(key.get("targetField"));
            if (!isBlank(sourceField) && !isBlank(targetField)) {
                result.add(targetField + "->" + sourceField);
            }
        }
        return result;
    }

    private boolean relationPredicateCompatible(Object actual, Object required, boolean allowMissingActual) {
        if (required == null) {
            return true;
        }
        if (actual == null) {
            return allowMissingActual;
        }
        return relationPredicateText(actual).equals(relationPredicateText(required));
    }

    private String relationPredicateText(Object rawPredicate) {
        Map<?, ?> predicate = objectMapRaw(rawPredicate);
        if (predicate.containsKey("and") || predicate.containsKey("or")) {
            String operator = predicate.containsKey("and") ? "and" : "or";
            List<String> items = new ArrayList<>();
            Object children = predicate.get(operator);
            if (children instanceof List) {
                for (Object child : (List<?>) children) {
                    items.add(relationPredicateText(child));
                }
            }
            Collections.sort(items);
            return operator + "(" + String.join(",", items) + ")";
        }
        if (predicate.containsKey("not")) {
            return "not(" + relationPredicateText(predicate.get("not")) + ")";
        }
        return rowConditionText(predicate);
    }

    private boolean sourceExistsCompatible(Object actual, Object required) {
        if (required == null) {
            return true;
        }
        Map<?, ?> actualMap = objectMapRaw(actual);
        Map<?, ?> requiredMap = objectMapRaw(required);
        return !actualMap.isEmpty()
                && objectString(actualMap.get("target")).equals(objectString(requiredMap.get("target")))
                && containsAllRelationKeys(actualMap.get("keys"), requiredMap.get("keys"))
                && relationPredicateCompatible(actualMap.get("targetWhere"), requiredMap.get("targetWhere"), false);
    }

    private boolean relationExistsCoversLocal(Map<String, Object> params, Map<String, Object> localParams) {
        return objectString(params.get("source")).equals(objectString(localParams.get("source")))
                && objectString(params.get("target")).equals(objectString(localParams.get("target")))
                && relationExpectExists(params) == relationExpectExists(localParams)
                && containsAllRelationKeys(params.get("keys"), localParams.get("keys"))
                && relationPredicateCompatible(params.get("sourceWhere"), localParams.get("sourceWhere"), false)
                && relationPredicateCompatible(params.get("targetWhere"), localParams.get("targetWhere"), false)
                && sourceExistsCompatible(params.get("sourceExists"), localParams.get("sourceExists"));
    }

    private String fallbackWarning(String failureReason) {
        if (isBlank(failureReason)) {
            return "模型推荐未通过校验，已降级为本地推荐";
        }
        return "模型推荐校验失败：" + failureReason + "，已降级为本地推荐";
    }

    private boolean containsAllRowConditions(Object conditions, Object requiredConditions) {
        List<Map<?, ?>> actualConditions = rowConditionMaps(conditions);
        List<String> actual = rowConditionTexts(conditions);
        for (Map<?, ?> requiredCondition : rowConditionMaps(requiredConditions)) {
            String required = rowConditionTextWithWhen(requiredCondition);
            if (!actual.contains(required) && !rowConditionCoveredBySplitEquality(actualConditions, requiredCondition)) {
                return false;
            }
        }
        return true;
    }

    private List<String> rowConditionTexts(Object rawConditions) {
        List<String> result = new ArrayList<>();
        for (Map<?, ?> condition : rowConditionMaps(rawConditions)) {
            result.add(rowConditionTextWithWhen(condition));
        }
        return result;
    }

    private List<Map<?, ?>> rowConditionMaps(Object rawConditions) {
        List<Map<?, ?>> result = new ArrayList<>();
        if (rawConditions instanceof Map) {
            result.add((Map<?, ?>) rawConditions);
            return result;
        }
        if (!(rawConditions instanceof List)) {
            return result;
        }
        for (Object item : (List<?>) rawConditions) {
            if (item instanceof Map) {
                result.add((Map<?, ?>) item);
            }
        }
        return result;
    }

    private String rowConditionTextWithWhen(Map<?, ?> condition) {
        String text = rowConditionText(condition);
        if (condition.containsKey("when")) {
            text = "when " + rowConditionText(objectMapRaw(condition.get("when"))) + " then " + text;
        }
        return text;
    }

    private String rowConditionText(Map<?, ?> condition) {
        String operator = objectString(condition.get("operator"));
        if ("isNull".equals(operator) || "isNotNull".equals(operator)) {
            return rowExpressionText(condition.get("left")) + " " + operator;
        }
        return rowExpressionText(condition.get("left")) + " " + operator + " "
                + rowExpressionText(condition.get("right"));
    }

    private boolean rowConditionCoveredBySplitEquality(List<Map<?, ?>> actualConditions, Map<?, ?> requiredCondition) {
        Map<?, ?> requiredWhen = objectMapRaw(requiredCondition.get("when"));
        if (!"in".equals(objectString(requiredWhen.get("operator")))) {
            return false;
        }
        List<String> requiredValues = rowExpressionValues(requiredWhen.get("right"));
        if (requiredValues.isEmpty()) {
            return false;
        }
        for (String value : requiredValues) {
            boolean covered = false;
            for (Map<?, ?> actualCondition : actualConditions) {
                if (sameRowAssertion(actualCondition, requiredCondition)
                        && sameEqualityWhen(actualCondition, requiredWhen, value)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    private boolean sameRowAssertion(Map<?, ?> actualCondition, Map<?, ?> requiredCondition) {
        return rowExpressionText(actualCondition.get("left")).equals(rowExpressionText(requiredCondition.get("left")))
                && objectString(actualCondition.get("operator")).equals(objectString(requiredCondition.get("operator")))
                && rowExpressionText(actualCondition.get("right")).equals(rowExpressionText(requiredCondition.get("right")));
    }

    private boolean sameEqualityWhen(Map<?, ?> actualCondition, Map<?, ?> requiredWhen, String value) {
        Map<?, ?> actualWhen = objectMapRaw(actualCondition.get("when"));
        String operator = objectString(actualWhen.get("operator"));
        return ("==".equals(operator) || "=".equals(operator))
                && rowExpressionText(actualWhen.get("left")).equals(rowExpressionText(requiredWhen.get("left")))
                && rowExpressionText(actualWhen.get("right")).equals(value);
    }

    private List<String> rowExpressionValues(Object rawExpression) {
        List<String> values = new ArrayList<>();
        if (rawExpression instanceof List) {
            for (Object item : (List<?>) rawExpression) {
                values.add(rowExpressionText(item));
            }
            return values;
        }
        String value = rowExpressionText(rawExpression);
        if (!isBlank(value)) {
            values.add(value);
        }
        return values;
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
            return rowExpressionText(expression.get("literal"));
        }
        if (expression.containsKey("value")) {
            return rowExpressionText(expression.get("value"));
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
        for (String condition : safe(expression).split("(?i)\\s+(?:&&|AND)\\s+")) {
            if (!isBlank(condition)) {
                conditions.add(condition.trim().replaceAll("\\s+", " "));
            }
        }
        return conditions;
    }

    private boolean containsAllEquivalentConditions(String expression, String requiredExpression) {
        List<String> conditions = normalizedConditions(expression);
        for (String required : normalizedConditions(requiredExpression)) {
            if (!conditionCovered(conditions, required)) {
                return false;
            }
        }
        return true;
    }

    private boolean conditionCovered(List<String> conditions, String required) {
        if (conditions.contains(required)) {
            return true;
        }
        String operator = required.contains(" == ") ? " == " : required.contains(" = ") ? " = " : "";
        if (isBlank(operator)) {
            return false;
        }
        String[] parts = required.split(Pattern.quote(operator), 2);
        if (parts.length != 2) {
            return false;
        }
        for (String condition : conditions) {
            if (isAbsToleranceEquality(condition, parts[0], parts[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean isAbsToleranceEquality(String condition, String left, String right) {
        String normalized = compact(condition);
        if (!normalized.startsWith("ABS(") || (!normalized.contains(")<=") && !normalized.contains(")<"))) {
            return false;
        }
        String leftValue = compact(left);
        String rightValue = stripOuterParentheses(compact(right));
        return normalized.contains("ABS(" + leftValue + "-" + rightValue + ")")
                || normalized.contains("ABS(" + leftValue + "-(" + rightValue + "))")
                || normalized.contains("ABS((" + leftValue + ")-(" + rightValue + "))")
                || normalized.contains("ABS((" + leftValue + ")-" + rightValue + ")")
                || normalized.contains("ABS((" + rightValue + ")-" + leftValue + ")")
                || normalized.contains("ABS((" + rightValue + ")-(" + leftValue + "))")
                || normalized.contains("ABS(" + rightValue + "-" + leftValue + ")");
    }

    private String compact(String value) {
        return safe(value).replaceAll("\\s+", "");
    }

    private String stripOuterParentheses(String value) {
        String result = value;
        while (result.startsWith("(") && result.endsWith(")") && result.length() > 1) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
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
