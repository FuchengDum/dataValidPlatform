package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GenericValidationLinter {
    private static final String SUPPORTED_SCHEMA_VERSION = "1";
    private static final Set<String> FIELD_TEMPLATES = new HashSet<>(
            Arrays.asList("NOT_NULL", "NON_NEGATIVE", "NUMERIC_TYPE"));
    private static final Set<String> SUPPORTED_TEMPLATES = new HashSet<>(Arrays.asList(
            "NOT_NULL", "NON_NEGATIVE", "NUMERIC_TYPE", "FIELD_EXPRESSION", "ROW_EXPRESSION",
            "EXISTS_IN_TABLE", "RELATION_EXISTS", "FIELD_EQUALS", "JOIN_ASSERT",
            "AGGREGATION_EQUALS", "AGGREGATE_ASSERT", "DUPLICATE_ASSERT", "DUPLICATE_CHECK"));
    private static final Set<String> COMPATIBLE_TEMPLATES = new HashSet<>(
            Arrays.asList("FIELD_EQUALS", "AGGREGATION_EQUALS", "DUPLICATE_CHECK"));

    private final GenericRuleAssetLoader assetLoader;

    public GenericValidationLinter(GenericRuleAssetLoader assetLoader) {
        this.assetLoader = assetLoader;
    }

    public GenericLintResult lintConfig(Path configPath) {
        GenericLintResult result = new GenericLintResult();
        GenericValidationConfig config = loadConfig(configPath, result);
        if (config == null) {
            return result;
        }
        lintSchema(config.getSchemaVersion(), "schemaVersion", result);
        Path baseDir = configPath.toAbsolutePath().getParent();
        GenericValidationConfig.SourceConfig source = resolveSource(config.getSource(), baseDir, result);
        GenericRulePackage rules = resolveRules(config.getRules(), baseDir, result);
        if (source != null) {
            lintSource(source, result);
        }
        if (source != null && rules != null) {
            lintRules(rules, source, result);
        }
        return result;
    }

    public GenericLintResult lintRules(Path rulePath, Path metadataPath) {
        GenericLintResult result = new GenericLintResult();
        GenericRulePackage rules = loadRules(rulePath, result);
        GenericValidationConfig.SourceConfig source = loadSource(metadataPath, result);
        if (source != null) {
            lintSource(source, result);
        }
        if (rules != null && source != null) {
            lintRules(rules, source, result);
        }
        return result;
    }

    private void lintRules(GenericRulePackage rulePackage, GenericValidationConfig.SourceConfig source,
                           GenericLintResult result) {
        lintSchema(rulePackage.getSchemaVersion(), "schemaVersion", result);
        Map<String, List<String>> tableFields = tableFields(source);
        Set<String> ruleIds = new HashSet<>();
        List<GenericRulePackage.GenericRule> rules = rulePackage.getRules();
        if (rules == null || rules.isEmpty()) {
            result.error("MISSING_RULES", "规则包必须包含 rules 列表", "rules", "请至少配置一条规则。");
            return;
        }
        for (int index = 0; index < rules.size(); index++) {
            lintRule(rules.get(index), index, tableFields, result);
            String ruleId = rules.get(index) == null ? null : rules.get(index).getRuleId();
            if (!isBlank(ruleId) && !ruleIds.add(ruleId)) {
                result.error("DUPLICATE_RULE_ID", "规则 ID 重复: " + ruleId,
                        "rules[" + index + "].ruleId", "请保证规则包内 ruleId 唯一。");
            }
        }
    }

    private void lintRule(GenericRulePackage.GenericRule rule, int index,
                          Map<String, List<String>> tableFields, GenericLintResult result) {
        String base = "rules[" + index + "]";
        if (rule == null) {
            result.error("MISSING_RULE", "规则不能为空", base, "请删除空规则或补充规则定义。");
            return;
        }
        requireText(rule.getRuleId(), "ruleId", base + ".ruleId", result);
        String templateCode = rule.getTemplateCode();
        if (!requireText(templateCode, "templateCode", base + ".templateCode", result)) {
            return;
        }
        if (!SUPPORTED_TEMPLATES.contains(templateCode)) {
            result.error("UNKNOWN_TEMPLATE", "不支持的规则模板: " + templateCode,
                    base + ".templateCode", "请使用已支持模板或先扩展模板执行器。");
            return;
        }
        if (COMPATIBLE_TEMPLATES.contains(templateCode)) {
            result.warning("COMPATIBLE_TEMPLATE", "当前模板属于兼容模板: " + templateCode,
                    base + ".templateCode", "新规则优先使用结构化模板。");
        }
        int errorsBefore = result.getErrors().size();
        lintTemplateReferences(rule, index, tableFields, result);
        if (result.getErrors().size() == errorsBefore) {
            validateTemplate(rule, base, tableFields, result);
        }
    }

    private void lintSource(GenericValidationConfig.SourceConfig source, GenericLintResult result) {
        lintSchema(source.getSchemaVersion(), "schemaVersion", result);
        if (source.getTables() == null || source.getTables().isEmpty()) {
            result.error("MISSING_TABLES", "元数据必须包含 tables 列表", "tables", "请在 source.yml 中配置业务表。");
            return;
        }
        Set<String> names = new HashSet<>();
        for (int index = 0; index < source.getTables().size(); index++) {
            GenericValidationConfig.TableConfig table = source.getTables().get(index);
            String base = "tables[" + index + "]";
            if (table == null) {
                result.error("MISSING_TABLE", "表配置不能为空", base, "请删除空表配置或补充表定义。");
                continue;
            }
            if (requireText(table.getLogicalName(), "logicalName", base + ".logicalName", result)
                    && !names.add(table.getLogicalName())) {
                result.error("DUPLICATE_TABLE", "表名重复: " + table.getLogicalName(),
                        base + ".logicalName", "请保证 logicalName 唯一。");
            }
            requireText(table.getPrimaryKey(), "primaryKey", base + ".primaryKey", result);
            if (fields(table).isEmpty()) {
                result.error("MISSING_FIELDS", "表缺少 headers 或 rows: " + table.getLogicalName(),
                        base + ".headers", "请在 source.yml 中提供字段列表。");
            }
        }
    }

    private void lintTemplateReferences(GenericRulePackage.GenericRule rule, int index,
                                        Map<String, List<String>> tableFields, GenericLintResult result) {
        String base = "rules[" + index + "].templateParams";
        Map<String, Object> params = rule.getTemplateParams();
        if (params == null || params.isEmpty()) {
            result.error("MISSING_TEMPLATE_PARAMS", "规则缺少 templateParams", base, "请补充模板参数。");
            return;
        }
        if (FIELD_TEMPLATES.contains(rule.getTemplateCode())) {
            String table = tableName(params, "tableName", base + ".tableName", tableFields, result);
            checkFields(params.get("fields"), table, tableFields, base + ".fields", result);
        } else if ("ROW_EXPRESSION".equals(rule.getTemplateCode())) {
            String table = tableName(params, "tableName", base + ".tableName", tableFields, result);
            scanFieldNodes(params.get("conditions"), table, tableFields, base + ".conditions", result);
        } else if ("FIELD_EQUALS".equals(rule.getTemplateCode())) {
            checkFieldEquals(params, base, tableFields, result);
        } else if ("EXISTS_IN_TABLE".equals(rule.getTemplateCode())) {
            checkExistsInTable(params, base, tableFields, result);
        } else if ("RELATION_EXISTS".equals(rule.getTemplateCode())) {
            checkRelationExists(params, base, tableFields, result);
        } else if ("JOIN_ASSERT".equals(rule.getTemplateCode())) {
            checkJoinAssert(params, base, tableFields, result);
        } else if ("AGGREGATE_ASSERT".equals(rule.getTemplateCode())) {
            checkAggregateAssert(params, base, tableFields, result);
        } else if ("DUPLICATE_CHECK".equals(rule.getTemplateCode())
                || "DUPLICATE_ASSERT".equals(rule.getTemplateCode())) {
            String table = tableName(params, params.containsKey("table") ? "table" : "tableName",
                    base + "." + (params.containsKey("table") ? "table" : "tableName"), tableFields, result);
            checkFields(params.get("groupBy"), table, tableFields, base + ".groupBy", result);
        }
    }

    private void checkFieldEquals(Map<String, Object> params, String base,
                                  Map<String, List<String>> tableFields, GenericLintResult result) {
        String source = tableName(params, "source", base + ".source", tableFields, result);
        String target = tableName(params, "target", base + ".target", tableFields, result);
        checkField(params.get("key"), source, tableFields, base + ".key", result);
        checkField(params.get("key"), target, tableFields, base + ".key", result);
        checkField(params.get("sourceField"), source, tableFields, base + ".sourceField", result);
        checkField(params.get("targetField"), target, tableFields, base + ".targetField", result);
    }

    private void checkExistsInTable(Map<String, Object> params, String base,
                                    Map<String, List<String>> tableFields, GenericLintResult result) {
        String source = tableName(params, "source", base + ".source", tableFields, result);
        String target = tableName(params, "target", base + ".target", tableFields, result);
        checkField(params.get("key"), source, tableFields, base + ".key", result);
        checkField(params.get("key"), target, tableFields, base + ".key", result);
    }

    private void checkRelationExists(Map<String, Object> params, String base,
                                     Map<String, List<String>> tableFields, GenericLintResult result) {
        String source = tableName(params, "source", base + ".source", tableFields, result);
        String target = tableName(params, "target", base + ".target", tableFields, result);
        checkRelationKeys(params, source, target, base, tableFields, result);
        scanFieldNodes(params.get("sourceWhere"), source, tableFields, base + ".sourceWhere", result);
        scanFieldNodes(params.get("targetWhere"), target, tableFields, base + ".targetWhere", result);
        checkSourceExists(params.get("sourceExists"), source, base + ".sourceExists", tableFields, result);
    }

    private void checkJoinAssert(Map<String, Object> params, String base,
                                 Map<String, List<String>> tableFields, GenericLintResult result) {
        String source = tableName(params, "source", base + ".source", tableFields, result);
        String target = tableName(params, "target", base + ".target", tableFields, result);
        checkRelationKeys(params, source, target, base, tableFields, result);
        scanJoinNodes(params.get("assert"), source, target, tableFields, base + ".assert", result);
        scanFieldNodes(params.get("sourceWhere"), source, tableFields, base + ".sourceWhere", result);
        scanFieldNodes(params.get("targetWhere"), target, tableFields, base + ".targetWhere", result);
    }

    @SuppressWarnings("unchecked")
    private void checkAggregateAssert(Map<String, Object> params, String base,
                                      Map<String, List<String>> tableFields, GenericLintResult result) {
        String source = tableName(params, "source", base + ".source", tableFields, result);
        String target = tableName(params, "target", base + ".target", tableFields, result);
        checkAggregateGroupBy(params.get("groupBy"), source, target, base + ".groupBy", tableFields, result);
        checkAggregateSpec(params.get("aggregate"), source, base + ".aggregate", tableFields, result);
        Object assertion = params.get("assert");
        if (assertion instanceof Map) {
            Map<String, Object> values = (Map<String, Object>) assertion;
            checkField(values.get("targetField"), target, tableFields, base + ".assert.targetField", result);
            checkAggregateSpec(values.get("aggregate"), target, base + ".assert.aggregate", tableFields, result);
        }
        checkAggregateSpec(params.get("targetAggregate"), target, base + ".targetAggregate", tableFields, result);
        scanFieldNodes(params.get("sourceWhere"), source, tableFields, base + ".sourceWhere", result);
        scanFieldNodes(params.get("targetWhere"), target, tableFields, base + ".targetWhere", result);
    }

    @SuppressWarnings("unchecked")
    private void checkRelationKeys(Map<String, Object> params, String source, String target, String base,
                                   Map<String, List<String>> tableFields, GenericLintResult result) {
        Object rawKeys = params.get("keys");
        if (rawKeys instanceof List) {
            List<Object> keys = (List<Object>) rawKeys;
            for (int index = 0; index < keys.size(); index++) {
                Object rawKey = keys.get(index);
                String path = base + ".keys[" + index + "]";
                if (rawKey instanceof Map) {
                    Map<String, Object> key = (Map<String, Object>) rawKey;
                    checkField(key.get("sourceField"), source, tableFields, path + ".sourceField", result);
                    checkField(key.get("targetField"), target, tableFields, path + ".targetField", result);
                } else {
                    checkField(rawKey, source, tableFields, path, result);
                    checkField(rawKey, target, tableFields, path, result);
                }
            }
            return;
        }
        checkField(params.get("key"), source, tableFields, base + ".key", result);
        Object targetKey = params.containsKey("targetKey") ? params.get("targetKey") : params.get("key");
        checkField(targetKey, target, tableFields, base + (params.containsKey("targetKey") ? ".targetKey" : ".key"),
                result);
    }

    @SuppressWarnings("unchecked")
    private void checkAggregateGroupBy(Object rawGroupBy, String source, String target, String path,
                                       Map<String, List<String>> tableFields, GenericLintResult result) {
        if (rawGroupBy instanceof List) {
            List<Object> values = (List<Object>) rawGroupBy;
            for (int index = 0; index < values.size(); index++) {
                Object item = values.get(index);
                String itemPath = path + "[" + index + "]";
                if (item instanceof Map) {
                    Map<String, Object> key = (Map<String, Object>) item;
                    checkField(key.get("sourceField"), source, tableFields, itemPath + ".sourceField", result);
                    checkField(key.get("targetField"), target, tableFields, itemPath + ".targetField", result);
                } else {
                    checkField(item, source, tableFields, itemPath, result);
                    checkField(item, target, tableFields, itemPath, result);
                }
            }
            return;
        }
        checkField(rawGroupBy, source, tableFields, path, result);
    }

    @SuppressWarnings("unchecked")
    private void checkAggregateSpec(Object rawAggregate, String table, String path,
                                    Map<String, List<String>> tableFields, GenericLintResult result) {
        if (!(rawAggregate instanceof Map)) {
            return;
        }
        Map<String, Object> aggregate = (Map<String, Object>) rawAggregate;
        String fn = value(aggregate.get("fn")).trim();
        if (!"COUNT".equalsIgnoreCase(fn)) {
            checkField(aggregate.get("field"), table, tableFields, path + ".field", result);
        }
    }

    @SuppressWarnings("unchecked")
    private void checkSourceExists(Object rawSourceExists, String source, String path,
                                   Map<String, List<String>> tableFields, GenericLintResult result) {
        if (!(rawSourceExists instanceof Map)) {
            return;
        }
        Map<String, Object> sourceExists = (Map<String, Object>) rawSourceExists;
        String target = tableName(sourceExists, "target", path + ".target", tableFields, result);
        checkRelationKeys(sourceExists, source, target, path, tableFields, result);
        scanFieldNodes(sourceExists.get("targetWhere"), target, tableFields, path + ".targetWhere", result);
    }

    @SuppressWarnings("unchecked")
    private void scanFieldNodes(Object node, String table, Map<String, List<String>> tableFields,
                                String path, GenericLintResult result) {
        if (node instanceof List) {
            List<Object> values = (List<Object>) node;
            for (int index = 0; index < values.size(); index++) {
                scanFieldNodes(values.get(index), table, tableFields, path + "[" + index + "]", result);
            }
            return;
        }
        if (!(node instanceof Map)) {
            return;
        }
        Map<String, Object> values = (Map<String, Object>) node;
        if (values.containsKey("field")) {
            checkField(values.get("field"), table, tableFields, path + ".field", result);
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            scanFieldNodes(entry.getValue(), table, tableFields, path + "." + entry.getKey(), result);
        }
    }

    @SuppressWarnings("unchecked")
    private void scanJoinNodes(Object node, String source, String target, Map<String, List<String>> tableFields,
                               String path, GenericLintResult result) {
        if (node instanceof List) {
            List<Object> values = (List<Object>) node;
            for (int index = 0; index < values.size(); index++) {
                scanJoinNodes(values.get(index), source, target, tableFields, path + "[" + index + "]", result);
            }
            return;
        }
        if (!(node instanceof Map)) {
            return;
        }
        Map<String, Object> values = (Map<String, Object>) node;
        checkField(values.get("sourceField"), source, tableFields, path + ".sourceField", result);
        checkField(values.get("targetField"), target, tableFields, path + ".targetField", result);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            scanJoinNodes(entry.getValue(), source, target, tableFields, path + "." + entry.getKey(), result);
        }
    }

    private void validateTemplate(GenericRulePackage.GenericRule rule, String base,
                                  Map<String, List<String>> tableFields, GenericLintResult result) {
        try {
            TemplateBindingValidator.validate(rule.getTemplateCode(), rule.getTemplateParams(), tableFields);
        } catch (BadRequestException ex) {
            result.error("INVALID_TEMPLATE_PARAMS", ex.getMessage(), base + ".templateParams",
                    "请按模板参数说明修正规则参数。");
        }
    }

    private Map<String, List<String>> tableFields(GenericValidationConfig.SourceConfig source) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        if (source.getTables() == null) {
            return fields;
        }
        for (GenericValidationConfig.TableConfig table : source.getTables()) {
            if (table != null && !isBlank(table.getLogicalName())) {
                fields.put(table.getLogicalName(), fields(table));
            }
        }
        return fields;
    }

    private List<String> fields(GenericValidationConfig.TableConfig table) {
        if (table.getHeaders() != null && !table.getHeaders().isEmpty()) {
            return table.getHeaders();
        }
        if (table.getRows() != null && !table.getRows().isEmpty()) {
            return new ArrayList<>(table.getRows().get(0).keySet());
        }
        return new ArrayList<>();
    }

    private String tableName(Map<String, Object> params, String key, String path,
                             Map<String, List<String>> tableFields, GenericLintResult result) {
        String table = value(params == null ? null : params.get(key));
        if (isBlank(table)) {
            result.error("MISSING_PARAM", "模板参数缺少 " + key, path, "请补充模板必填参数。");
            return null;
        }
        if (!tableFields.containsKey(table)) {
            result.error("UNKNOWN_TABLE", "规则引用了未知表: " + table, path, "请在 source.yml 中补充表或修正表名。");
        }
        return table;
    }

    private void checkFields(Object values, String table, Map<String, List<String>> tableFields,
                             String path, GenericLintResult result) {
        List<String> fields = toList(values);
        if (fields.isEmpty()) {
            result.error("MISSING_PARAM", "字段列表不能为空", path, "请补充至少一个字段。");
            return;
        }
        for (int index = 0; index < fields.size(); index++) {
            checkField(fields.get(index), table, tableFields, path + "[" + index + "]", result);
        }
    }

    private void checkField(Object rawField, String table, Map<String, List<String>> tableFields,
                            String path, GenericLintResult result) {
        String field = value(rawField);
        if (isBlank(field) || isBlank(table) || !tableFields.containsKey(table)) {
            return;
        }
        if (!tableFields.get(table).contains(field)) {
            result.error("UNKNOWN_FIELD", "规则引用了未知字段: " + field, path,
                    "请在 source.yml 中补充字段或修正规则字段名。");
        }
    }

    private List<String> toList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                values.add(value(item));
            }
        } else if (!isBlank(value(value))) {
            values.add(value(value));
        }
        return values;
    }

    private GenericValidationConfig loadConfig(Path path, GenericLintResult result) {
        try {
            GenericValidationConfig config = assetLoader.loadConfig(path);
            if (config == null) {
                result.error("EMPTY_CONFIG", "配置文件为空", "$", "请补充 validator.yml 内容。");
            }
            return config;
        } catch (Exception ex) {
            result.error("CONFIG_PARSE_ERROR", ex.getMessage(), "$", "请检查 validator.yml 是否存在且格式正确。");
            return null;
        }
    }

    private GenericRulePackage loadRules(Path path, GenericLintResult result) {
        try {
            GenericRulePackage rules = assetLoader.loadRules(path);
            if (rules == null) {
                result.error("EMPTY_RULES", "规则文件为空", "$", "请补充 rules.yml 内容。");
            }
            return rules;
        } catch (Exception ex) {
            result.error("RULES_PARSE_ERROR", ex.getMessage(), "$", "请检查 rules.yml 是否存在且格式正确。");
            return null;
        }
    }

    private GenericValidationConfig.SourceConfig loadSource(Path path, GenericLintResult result) {
        try {
            GenericValidationConfig.SourceConfig source = assetLoader.loadSource(path);
            if (source == null) {
                result.error("EMPTY_METADATA", "元数据文件为空", "$", "请补充 source.yml 内容。");
            }
            return source;
        } catch (Exception ex) {
            result.error("METADATA_PARSE_ERROR", ex.getMessage(), "$", "请检查 source.yml 是否存在且格式正确。");
            return null;
        }
    }

    private GenericValidationConfig.SourceConfig resolveSource(GenericValidationConfig.SourceConfig source,
                                                               Path baseDir, GenericLintResult result) {
        if (source == null) {
            result.error("MISSING_SOURCE", "配置缺少 source", "source", "请在 validator.yml 中配置 source。");
            return null;
        }
        if (isBlank(source.getFile())) {
            return source;
        }
        return loadSource(resolve(baseDir, source.getFile()), result);
    }

    private GenericRulePackage resolveRules(GenericValidationConfig.RuleConfig rules,
                                            Path baseDir, GenericLintResult result) {
        if (rules == null || isBlank(rules.getFile())) {
            result.error("MISSING_RULE_FILE", "配置缺少 rules.file", "rules.file", "请在 validator.yml 中配置规则文件。");
            return null;
        }
        return loadRules(resolve(baseDir, rules.getFile()), result);
    }

    private boolean requireText(String value, String field, String path, GenericLintResult result) {
        if (!isBlank(value)) {
            return true;
        }
        result.error("MISSING_REQUIRED_FIELD", "缺少必填字段: " + field, path, "请补充该字段。");
        return false;
    }

    private void lintSchema(String schemaVersion, String path, GenericLintResult result) {
        if (isBlank(schemaVersion)) {
            result.warning("MISSING_SCHEMA_VERSION", "缺少 schemaVersion", path,
                    "建议声明 schemaVersion: 1，便于后续兼容演进。");
            return;
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion.trim())) {
            result.error("UNSUPPORTED_SCHEMA_VERSION", "不支持的 schemaVersion: " + schemaVersion, path,
                    "当前仅支持 schemaVersion: " + SUPPORTED_SCHEMA_VERSION + "。");
        }
    }

    private Path resolve(Path baseDir, String path) {
        Path candidate = Path.of(path);
        return candidate.isAbsolute() ? candidate : baseDir.resolve(candidate).normalize();
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
