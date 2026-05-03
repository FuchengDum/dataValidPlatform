package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.FindingEvidenceEntity;
import com.example.datavalidator.persistence.ValidationFindingEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
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

    private final AiChatClient aiChatClient;
    private final ObjectMapper objectMapper;

    public AiAssistService(AiChatClient aiChatClient, ObjectMapper objectMapper) {
        this.aiChatClient = aiChatClient;
        this.objectMapper = objectMapper;
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
}
