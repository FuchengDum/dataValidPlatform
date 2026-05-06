package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.DatasetSourceType;
import com.example.datavalidator.domain.RuleCategory;
import com.example.datavalidator.domain.RuleDefinition;
import com.example.datavalidator.domain.Severity;
import com.example.datavalidator.domain.WorkbookDataset;
import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.DataTableSnapshotEntity;
import com.example.datavalidator.persistence.DatasetEntity;
import com.example.datavalidator.persistence.RuleBindingEntity;
import com.example.datavalidator.persistence.RuleDefinitionEntity;
import com.example.datavalidator.repository.DataRowSnapshotRepository;
import com.example.datavalidator.repository.DataTableSnapshotRepository;
import com.example.datavalidator.repository.DatasetRepository;
import com.example.datavalidator.repository.RuleBindingRepository;
import com.example.datavalidator.repository.RuleDefinitionRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExcelImportService {
    private static final Map<String, String> BUSINESS_SHEETS = new LinkedHashMap<>();
    private static final Map<String, String> PRIMARY_KEYS = new HashMap<>();

    static {
        BUSINESS_SHEETS.put("订单表_t_order", "t_order");
        BUSINESS_SHEETS.put("订单明细表_t_order_item", "t_order_item");
        BUSINESS_SHEETS.put("商品表_t_product", "t_product");
        BUSINESS_SHEETS.put("支付表_t_payment", "t_payment");
        BUSINESS_SHEETS.put("库存流水表_t_inventory_log", "t_inventory_log");
        PRIMARY_KEYS.put("t_order", "订单ID");
        PRIMARY_KEYS.put("t_order_item", "明细ID");
        PRIMARY_KEYS.put("t_product", "商品ID");
        PRIMARY_KEYS.put("t_payment", "支付ID");
        PRIMARY_KEYS.put("t_inventory_log", "流水ID");
    }

    private final DatasetRepository datasetRepository;
    private final DataTableSnapshotRepository tableRepository;
    private final DataRowSnapshotRepository rowRepository;
    private final RuleDefinitionRepository ruleRepository;
    private final RuleBindingRepository bindingRepository;
    private final JsonService jsonService;
    private final BusinessTableDataProvider businessTableDataProvider;

    public ExcelImportService(DatasetRepository datasetRepository,
                              DataTableSnapshotRepository tableRepository,
                              DataRowSnapshotRepository rowRepository,
                              RuleDefinitionRepository ruleRepository,
                              RuleBindingRepository bindingRepository,
                              JsonService jsonService,
                              BusinessTableDataProvider businessTableDataProvider) {
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.rowRepository = rowRepository;
        this.ruleRepository = ruleRepository;
        this.bindingRepository = bindingRepository;
        this.jsonService = jsonService;
        this.businessTableDataProvider = businessTableDataProvider;
    }

    @Transactional
    public ImportResult importWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("上传文件不能为空");
        }
        String fileName = file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename();
        if (!fileName.endsWith(".xlsx")) {
            throw new BadRequestException("仅支持 .xlsx 文件");
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            validateSheets(workbook);
            String datasetId = IdFactory.next("ds");
            WorkbookDataset dataset = new WorkbookDataset();
            dataset.setDatasetId(datasetId);
            dataset.setFileName(fileName);
            dataset.setSourceName(fileName);

            dataset.setBusinessTables(businessTableDataProvider.loadAllTables());

            Map<String, List<String>> scenarioMap = readScenarioMap(workbook.getSheet("校验场景覆盖矩阵"));
            List<RuleDefinition> rules = readRules(workbook.getSheet("业务规则库"), scenarioMap);
            dataset.setRules(rules);
            saveDataset(dataset);

            return new ImportResult(datasetId, fileName, dataset.getBusinessTables().size(), rules.size(),
                    countScenarioIds(scenarioMap), 7);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Excel解析失败: " + ex.getMessage());
        }
    }

    public WorkbookDataset loadDataset(String datasetId) {
        DatasetEntity datasetEntity = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new BadRequestException("数据集不存在: " + datasetId));
        WorkbookDataset dataset = new WorkbookDataset();
        dataset.setDatasetId(datasetId);
        dataset.setSourceType(DatasetSourceType.valueOf(datasetEntity.getSourceType()));
        dataset.setSourceName(datasetEntity.getSourceName());
        dataset.setFileName(datasetEntity.getFileName());

        for (DataTableSnapshotEntity tableEntity : tableRepository.findByDatasetId(datasetId)) {
            DataTable table;
            if (DatasetSourceType.DATABASE_TABLE.name().equals(tableEntity.getSourceType())) {
                table = businessTableDataProvider.loadTable(tableEntity.getLogicalName());
            } else {
                table = new DataTable();
                table.setSheetName(tableEntity.getSheetName());
                table.setLogicalName(tableEntity.getLogicalName());
                table.setSourceType(DatasetSourceType.valueOf(tableEntity.getSourceType()));
                table.setHeaders(jsonService.readStringList(tableEntity.getHeadersJson()));
                List<DataRow> rows = rowRepository
                        .findByDatasetIdAndTableNameOrderByRowIndex(datasetId, tableEntity.getLogicalName())
                        .stream()
                        .map(entity -> {
                            DataRow row = new DataRow();
                            row.setRowIndex(entity.getRowIndex());
                            row.setPrimaryKey(entity.getPrimaryKey());
                            row.setValues(jsonService.readStringMap(entity.getValuesJson()));
                            return row;
                        }).collect(Collectors.toList());
                table.setRows(rows);
            }
            dataset.getBusinessTables().put(table.getLogicalName(), table);
        }

        List<RuleDefinition> rules = ruleRepository.findByDatasetIdOrderByRuleId(datasetId)
                .stream().map(this::toRuleDefinition).collect(Collectors.toList());
        dataset.setRules(rules);
        return dataset;
    }

    private void validateSheets(Workbook workbook) {
        List<String> required = new ArrayList<>(
                Arrays.asList("业务规则库", "字段约束说明", "关联逻辑说明", "校验场景覆盖矩阵", "使用说明"));
        for (String sheetName : required) {
            if (workbook.getSheet(sheetName) == null) {
                throw new BadRequestException("缺少必需 sheet：" + sheetName);
            }
        }
    }

    private DataTable readBusinessTable(Sheet sheet, String sheetName, String logicalName) {
        DataFormatter formatter = new DataFormatter();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new BadRequestException("sheet 表头为空：" + sheetName);
        }
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = formatter.formatCellValue(headerRow.getCell(i)).trim();
            if (!header.isEmpty()) {
                headers.add(header);
            }
        }
        String primaryKeyField = PRIMARY_KEYS.get(logicalName);
        if (!headers.contains(primaryKeyField)) {
            throw new BadRequestException(sheetName + " 缺少主键字段：" + primaryKeyField);
        }

        DataTable table = new DataTable();
        table.setSheetName(sheetName);
        table.setLogicalName(logicalName);
        table.setHeaders(headers);

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row excelRow = sheet.getRow(rowIndex);
            if (excelRow == null) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            boolean hasValue = false;
            for (int col = 0; col < headers.size(); col++) {
                String value = formatter.formatCellValue(excelRow.getCell(col)).trim();
                if (!value.isEmpty()) {
                    hasValue = true;
                }
                values.put(headers.get(col), value);
            }
            if (!hasValue) {
                continue;
            }
            DataRow row = new DataRow();
            row.setRowIndex(rowIndex + 1);
            row.setValues(values);
            row.setPrimaryKey(values.get(primaryKeyField));
            table.getRows().add(row);
        }
        return table;
    }

    private List<RuleDefinition> readRules(Sheet sheet, Map<String, List<String>> scenarioMap) {
        DataFormatter formatter = new DataFormatter();
        List<String> headers = readHeaders(sheet, formatter);
        List<RuleDefinition> rules = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, String> values = rowToMap(row, headers, formatter);
            String ruleId = values.getOrDefault("规则编号", "").trim();
            if (ruleId.isEmpty()) {
                continue;
            }
            RuleDefinition rule = new RuleDefinition();
            rule.setRuleId(ruleId);
            rule.setRuleName(values.getOrDefault("规则名称", ruleId));
            rule.setCategory(parseCategory(values.get("规则分类")));
            rule.setApplicableTables(split(values.get("适用表")));
            rule.setDescription(values.get("规则描述"));
            rule.setPseudoLogic(values.get("校验逻辑(SQL/伪代码)"));
            rule.setSeverity("警告".equals(values.get("严重等级")) ? Severity.WARNING : Severity.CRITICAL);
            rule.setExample(values.get("异常示例"));
            rule.setScenarioIds(scenarioMap.getOrDefault(ruleId, new ArrayList<>()));
            rule.setTemplateCode(templateFor(ruleId));
            rules.add(rule);
        }
        return rules;
    }

    private Map<String, List<String>> readScenarioMap(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        List<String> headers = readHeaders(sheet, formatter);
        Map<String, List<String>> result = new HashMap<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, String> values = rowToMap(row, headers, formatter);
            String scenarioId = values.getOrDefault("场景编号", "").trim();
            if (!scenarioId.matches("S\\d{3}")) {
                continue;
            }
            for (String ruleId : split(values.get("覆盖规则"))) {
                if (!ruleId.matches("R\\d{3}")) {
                    continue;
                }
                result.computeIfAbsent(ruleId, key -> new ArrayList<>()).add(scenarioId);
            }
        }
        return result;
    }

    private int countScenarioIds(Map<String, List<String>> scenarioMap) {
        return (int) scenarioMap.values().stream()
                .flatMap(List::stream)
                .distinct()
                .count();
    }

    private List<String> readHeaders(Sheet sheet, DataFormatter formatter) {
        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            headers.add(formatter.formatCellValue(headerRow.getCell(i)).trim());
        }
        return headers;
    }

    private Map<String, String> rowToMap(Row row, List<String> headers, DataFormatter formatter) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int col = 0; col < headers.size(); col++) {
            values.put(headers.get(col), formatter.formatCellValue(row.getCell(col)).trim());
        }
        return values;
    }

    private void saveDataset(WorkbookDataset dataset) {
        DatasetEntity datasetEntity = new DatasetEntity();
        datasetEntity.setDatasetId(dataset.getDatasetId());
        datasetEntity.setSourceType(dataset.getSourceType().name());
        datasetEntity.setSourceName(dataset.getSourceName());
        datasetEntity.setFileName(dataset.getFileName());
        datasetEntity.setStatus("IMPORTED");
        datasetEntity.setImportedAt(LocalDateTime.now());
        datasetRepository.save(datasetEntity);

        for (DataTable table : dataset.getBusinessTables().values()) {
            DataTableSnapshotEntity tableEntity = new DataTableSnapshotEntity();
            tableEntity.setId(IdFactory.next("tbl"));
            tableEntity.setDatasetId(dataset.getDatasetId());
            tableEntity.setSheetName(table.getSheetName());
            tableEntity.setLogicalName(table.getLogicalName());
            tableEntity.setSourceType(table.getSourceType().name());
            tableEntity.setHeadersJson(jsonService.write(table.getHeaders()));
            tableEntity.setRowCount(table.getRows().size());
            tableRepository.save(tableEntity);
        }

        for (RuleDefinition rule : dataset.getRules()) {
            RuleDefinitionEntity entity = new RuleDefinitionEntity();
            entity.setDatasetId(dataset.getDatasetId());
            entity.setRuleId(rule.getRuleId());
            entity.setRuleName(rule.getRuleName());
            entity.setCategory(rule.getCategory().name());
            entity.setApplicableTables(String.join(",", rule.getApplicableTables()));
            entity.setDescription(rule.getDescription());
            entity.setPseudoLogic(rule.getPseudoLogic());
            entity.setSeverity(rule.getSeverity().name());
            entity.setExample(rule.getExample());
            entity.setScenarioIds(String.join(",", rule.getScenarioIds()));
            entity.setExecutorType(rule.getExecutorType());
            entity.setTemplateCode(rule.getTemplateCode());
            ruleRepository.save(entity);
            bindingRepository.save(defaultBinding(dataset.getDatasetId(), rule));
        }
    }

    private RuleBindingEntity defaultBinding(String datasetId, RuleDefinition rule) {
        RuleBindingEntity entity = new RuleBindingEntity();
        entity.setId(IdFactory.next("bind"));
        entity.setDatasetId(datasetId);
        entity.setRuleId(rule.getRuleId());
        entity.setExecutorType("BUILTIN");
        entity.setBuiltinExecutorName(rule.getRuleId());
        entity.setTemplateCode(rule.getTemplateCode());
        entity.setTemplateParamsJson(jsonService.write(defaultTemplateParams(rule.getRuleId())));
        return entity;
    }

    private Map<String, Object> defaultTemplateParams(String ruleId) {
        Map<String, Object> params = new LinkedHashMap<>();
        switch (ruleId) {
            case "R001":
                params.put("tableName", "t_order");
                params.put("fields", Arrays.asList("订单金额", "实付金额", "优惠金额"));
                break;
            case "R002":
                params.put("tableName", "t_order");
                params.put("fields", Arrays.asList("用户ID", "订单状态", "下单时间", "收货地址"));
                break;
            case "R003":
                params.put("tableName", "t_order");
                params.put("fields", Arrays.asList("订单金额", "实付金额"));
                break;
            case "R008":
                params.put("tableName", "t_product");
                params.put("fields", Arrays.asList("库存数量", "成本价"));
                break;
            case "R013":
                params.put("tableName", "t_payment");
                params.put("fields", Arrays.asList("支付金额", "退款金额"));
                break;
            case "R017":
            case "R030":
                params.put("source", "t_order_item");
                params.put("target", "t_order");
                params.put("groupBy", Arrays.asList(relationKey("订单ID", "订单ID")));
                params.put("aggregate", aggregate("SUM", "小计金额"));
                params.put("assert", aggregateAssert("==", "订单金额", "0.01"));
                break;
            case "R019":
                params.put("source", "t_order_item");
                params.put("target", "t_product");
                params.put("keys", Arrays.asList(relationKey("商品ID", "商品ID")));
                params.put("assert", joinAssert(sourceField("单价"), "==", targetField("售价"), "0.01"));
                break;
            case "R020":
                params.put("source", "t_payment");
                params.put("target", "t_order");
                params.put("groupBy", Arrays.asList(relationKey("订单ID", "订单ID")));
                params.put("aggregate", aggregate("SUM", "支付金额"));
                params.put("sourceWhere", condition(field("支付状态"), "==", literal("支付成功")));
                params.put("assert", aggregateAssert("==", "实付金额", "0.01"));
                break;
            case "R024":
                params.put("source", "t_payment");
                params.put("target", "t_order");
                params.put("keys", Arrays.asList(relationKey("订单ID", "订单ID")));
                params.put("assert", joinAssert(sourceField("用户ID"), "==", targetField("用户ID"), null));
                break;
            case "R029":
                params.put("table", "t_payment");
                params.put("groupBy", Arrays.asList("订单ID"));
                params.put("where", condition(field("支付状态"), "==", literal("支付成功")));
                params.put("assert", duplicateAssert("<=", 1));
                break;
            default:
                break;
        }
        return params;
    }

    private Map<String, Object> relationKey(String sourceField, String targetField) {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("sourceField", sourceField);
        key.put("targetField", targetField);
        return key;
    }

    private Map<String, Object> aggregate(String fn, String field) {
        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("fn", fn);
        aggregate.put("field", field);
        return aggregate;
    }

    private Map<String, Object> aggregateAssert(String operator, String targetField, String tolerance) {
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("op", operator);
        assertion.put("targetField", targetField);
        assertion.put("tolerance", tolerance);
        return assertion;
    }

    private Map<String, Object> joinAssert(Object left, String operator, Object right, String tolerance) {
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("left", left);
        assertion.put("op", operator);
        assertion.put("right", right);
        if (tolerance != null) {
            assertion.put("tolerance", tolerance);
        }
        return assertion;
    }

    private Map<String, Object> sourceField(String fieldName) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("sourceField", fieldName);
        return expression;
    }

    private Map<String, Object> targetField(String fieldName) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("targetField", fieldName);
        return expression;
    }

    private Map<String, Object> condition(Object left, String operator, Object right) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("left", left);
        condition.put("operator", operator);
        condition.put("right", right);
        return condition;
    }

    private Map<String, Object> field(String fieldName) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("field", fieldName);
        return expression;
    }

    private Map<String, Object> literal(Object value) {
        Map<String, Object> expression = new LinkedHashMap<>();
        expression.put("literal", value);
        return expression;
    }

    private Map<String, Object> duplicateAssert(String operator, int count) {
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("op", operator);
        assertion.put("count", count);
        return assertion;
    }

    private RuleDefinition toRuleDefinition(RuleDefinitionEntity entity) {
        RuleDefinition rule = new RuleDefinition();
        rule.setRuleId(entity.getRuleId());
        rule.setRuleName(entity.getRuleName());
        rule.setCategory(RuleCategory.valueOf(entity.getCategory()));
        rule.setApplicableTables(split(entity.getApplicableTables()));
        rule.setDescription(entity.getDescription());
        rule.setPseudoLogic(entity.getPseudoLogic());
        rule.setSeverity(Severity.valueOf(entity.getSeverity()));
        rule.setExample(entity.getExample());
        rule.setScenarioIds(split(entity.getScenarioIds()));
        rule.setExecutorType(entity.getExecutorType());
        rule.setTemplateCode(entity.getTemplateCode());
        return rule;
    }

    private RuleCategory parseCategory(String category) {
        if (category == null) {
            return RuleCategory.SINGLE_BUSINESS_RULE;
        }
        if (category.contains("字段约束")) {
            return RuleCategory.SINGLE_FIELD_CONSTRAINT;
        }
        if (category.contains("多表")) {
            return RuleCategory.MULTI_TABLE_RELATION;
        }
        if (category.contains("指标")) {
            return RuleCategory.METRIC_CONSISTENCY;
        }
        return RuleCategory.SINGLE_BUSINESS_RULE;
    }

    private List<String> split(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.replace("，", ",").replace("、", ",").replace("↔", ",").split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    private String templateFor(String ruleId) {
        switch (ruleId) {
            case "R001":
            case "R008":
            case "R013":
            case "R016":
                return "NON_NEGATIVE";
            case "R002":
                return "NOT_NULL";
            case "R003":
                return "NUMERIC_TYPE";
            case "R011":
                return "FIELD_EXPRESSION";
            case "R017":
            case "R020":
            case "R030":
                return "AGGREGATE_ASSERT";
            case "R018":
            case "R023":
                return "EXISTS_IN_TABLE";
            case "R019":
            case "R024":
                return "JOIN_ASSERT";
            case "R029":
                return "DUPLICATE_ASSERT";
            default:
                return null;
        }
    }

    public static class ImportResult {
        private final String datasetId;
        private final String fileName;
        private final int businessTableCount;
        private final int ruleCount;
        private final int scenarioCount;
        private final int relationCount;

        public ImportResult(String datasetId, String fileName, int businessTableCount, int ruleCount,
                            int scenarioCount, int relationCount) {
            this.datasetId = datasetId;
            this.fileName = fileName;
            this.businessTableCount = businessTableCount;
            this.ruleCount = ruleCount;
            this.scenarioCount = scenarioCount;
            this.relationCount = relationCount;
        }

        public String getDatasetId() { return datasetId; }
        public String getFileName() { return fileName; }
        public int getBusinessTableCount() { return businessTableCount; }
        public int getRuleCount() { return ruleCount; }
        public int getScenarioCount() { return scenarioCount; }
        public int getRelationCount() { return relationCount; }
    }
}
