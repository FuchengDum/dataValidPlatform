package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.persistence.DataRowSnapshotEntity;
import com.example.datavalidator.persistence.DataTableSnapshotEntity;
import com.example.datavalidator.persistence.DatasetEntity;
import com.example.datavalidator.persistence.RuleBindingEntity;
import com.example.datavalidator.persistence.RuleDefinitionEntity;
import com.example.datavalidator.repository.DataRowSnapshotRepository;
import com.example.datavalidator.repository.DataTableSnapshotRepository;
import com.example.datavalidator.repository.DatasetRepository;
import com.example.datavalidator.repository.RuleBindingRepository;
import com.example.datavalidator.repository.RuleDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExcelImportServiceTest {
    private final DatasetRepository datasetRepository = mock(DatasetRepository.class);
    private final DataTableSnapshotRepository tableRepository = mock(DataTableSnapshotRepository.class);
    private final DataRowSnapshotRepository rowRepository = mock(DataRowSnapshotRepository.class);
    private final RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
    private final RuleBindingRepository bindingRepository = mock(RuleBindingRepository.class);
    private final BusinessTableDataProvider dataProvider = mock(BusinessTableDataProvider.class);
    private final JsonService jsonService = new JsonService(new ObjectMapper());
    private final ExcelImportService service = new ExcelImportService(datasetRepository, tableRepository,
            rowRepository, ruleRepository, bindingRepository, jsonService, dataProvider);

    @Test
    void importWorkbookDoesNotRequireBusinessSheetsAndDoesNotPersistBusinessRows() throws Exception {
        when(dataProvider.loadAllTables()).thenReturn(seedTables());
        when(datasetRepository.save(any(DatasetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableRepository.save(any(DataTableSnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.save(any(RuleDefinitionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bindingRepository.save(any(RuleBindingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExcelImportService.ImportResult result = service.importWorkbook(workbookWithoutBusinessSheets());

        assertThat(result.getBusinessTableCount()).isEqualTo(5);
        assertThat(result.getRuleCount()).isEqualTo(1);
        verify(tableRepository, times(5)).save(any(DataTableSnapshotEntity.class));
        verify(rowRepository, never()).save(any(DataRowSnapshotEntity.class));
    }

    @Test
    void importWorkbookUsesFirstClassTemplatesForDefaultBindings() throws Exception {
        when(dataProvider.loadAllTables()).thenReturn(seedTables());
        when(datasetRepository.save(any(DatasetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableRepository.save(any(DataTableSnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.save(any(RuleDefinitionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bindingRepository.save(any(RuleBindingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<RuleBindingEntity> captor = ArgumentCaptor.forClass(RuleBindingEntity.class);

        service.importWorkbook(workbookWithDefaultBindingRules());

        verify(bindingRepository, times(6)).save(captor.capture());
        Map<String, RuleBindingEntity> bindings = captor.getAllValues().stream()
                .collect(Collectors.toMap(RuleBindingEntity::getRuleId, binding -> binding));
        assertThat(bindings.get("R017").getTemplateCode()).isEqualTo("AGGREGATE_ASSERT");
        assertThat(bindings.get("R017").getTemplateParamsJson()).contains(
                "\"source\":\"t_order_item\"", "\"target\":\"t_order\"", "\"小计金额\"", "\"订单金额\"");
        assertThat(bindings.get("R020").getTemplateCode()).isEqualTo("AGGREGATE_ASSERT");
        assertThat(bindings.get("R020").getTemplateParamsJson()).contains(
                "\"source\":\"t_payment\"", "\"target\":\"t_order\"", "\"支付金额\"", "\"实付金额\"",
                "\"支付状态\"", "\"支付成功\"");
        assertThat(bindings.get("R030").getTemplateCode()).isEqualTo("AGGREGATE_ASSERT");
        assertThat(bindings.get("R030").getTemplateParamsJson()).contains(
                "\"source\":\"t_order_item\"", "\"target\":\"t_order\"", "\"小计金额\"", "\"订单金额\"");
        assertThat(bindings.get("R019").getTemplateCode()).isEqualTo("JOIN_ASSERT");
        assertThat(bindings.get("R019").getTemplateParamsJson()).contains(
                "\"source\":\"t_order_item\"", "\"target\":\"t_product\"", "\"单价\"", "\"售价\"");
        assertThat(bindings.get("R024").getTemplateCode()).isEqualTo("JOIN_ASSERT");
        assertThat(bindings.get("R024").getTemplateParamsJson()).contains(
                "\"source\":\"t_payment\"", "\"target\":\"t_order\"", "\"用户ID\"");
        assertThat(bindings.get("R029").getTemplateCode()).isEqualTo("DUPLICATE_ASSERT");
        assertThat(bindings.get("R029").getTemplateParamsJson()).contains(
                "\"table\":\"t_payment\"", "\"groupBy\":[\"订单ID\"]", "\"count\":1");
        Map<String, List<String>> headersByTable = seedTables().values().stream()
                .collect(Collectors.toMap(DataTable::getLogicalName, DataTable::getHeaders));
        for (RuleBindingEntity binding : bindings.values()) {
            TemplateBindingValidator.validate(binding.getTemplateCode(),
                    jsonService.readObjectMap(binding.getTemplateParamsJson()), headersByTable);
        }
    }

    @Test
    void importWorkbookRemovesWpsNullRelationshipsBeforePoiReadsPackage() throws Exception {
        when(dataProvider.loadAllTables()).thenReturn(seedTables());
        when(datasetRepository.save(any(DatasetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableRepository.save(any(DataTableSnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.save(any(RuleDefinitionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bindingRepository.save(any(RuleBindingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Logger poiLogger = (Logger) LoggerFactory.getLogger("org.apache.poi.ooxml.POIXMLDocumentPart");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        poiLogger.addAppender(appender);

        ExcelImportService.ImportResult result;
        try {
            result = service.importWorkbook(workbookWithWpsNullRelationship());
        } finally {
            poiLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(result.getRuleCount()).isEqualTo(1);
        assertThat(appender.list)
                .noneMatch(event -> event.getFormattedMessage().contains("Skipped invalid entry /xl/NULL"));
    }

    private Map<String, DataTable> seedTables() {
        Map<String, DataTable> tables = new LinkedHashMap<>();
        tables.put("t_order", table("t_order", "订单ID", "用户ID", "订单金额", "优惠金额", "实付金额"));
        tables.put("t_order_item", table("t_order_item", "明细ID", "订单ID", "商品ID", "小计金额", "单价"));
        tables.put("t_product", table("t_product", "商品ID", "商品名称", "库存数量", "售价"));
        tables.put("t_payment", table("t_payment", "支付ID", "订单ID", "用户ID", "支付金额", "支付状态"));
        tables.put("t_inventory_log", table("t_inventory_log", "流水ID", "商品ID", "变动数量"));
        return tables;
    }

    private DataTable table(String logicalName, String... headers) {
        DataTable table = new DataTable();
        table.setLogicalName(logicalName);
        table.setSheetName(logicalName);
        table.setHeaders(Arrays.asList(headers));
        DataRow row = new DataRow();
        row.setRowIndex(1);
        row.setPrimaryKey(logicalName + "-1");
        row.getValues().put(headers[0], logicalName + "-1");
        table.getRows().add(row);
        return table;
    }

    private MockMultipartFile workbookWithoutBusinessSheets() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        ruleSheet(workbook.createSheet("业务规则库"));
        simpleSheet(workbook.createSheet("字段约束说明"), "字段");
        simpleSheet(workbook.createSheet("关联逻辑说明"), "关联");
        scenarioSheet(workbook.createSheet("校验场景覆盖矩阵"));
        simpleSheet(workbook.createSheet("使用说明"), "说明");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return new MockMultipartFile("file", "rules.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
    }

    private MockMultipartFile workbookWithDefaultBindingRules() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        ruleSheet(workbook.createSheet("业务规则库"), Arrays.asList(
                ruleRow("R017", "订单-明细金额一致性", "多表关联核对", "t_order,t_order_item",
                        "订单金额应等于其所有明细小计金额之和"),
                ruleRow("R019", "明细-商品价格一致性", "多表关联核对", "t_order_item,t_product",
                        "明细中的单价应与商品表中的售价一致"),
                ruleRow("R020", "订单-支付金额一致性", "多表关联核对", "t_order,t_payment",
                        "订单实付金额应等于支付表中对应支付金额之和"),
                ruleRow("R024", "支付-订单用户一致性", "多表关联核对", "t_payment,t_order",
                        "支付记录中的用户ID应与订单中的用户ID一致"),
                ruleRow("R029", "同一订单重复支付校验", "指标一致性校验", "t_payment",
                        "同一订单不应有多条支付成功记录"),
                ruleRow("R030", "订单金额与明细汇总一致性", "指标一致性校验", "t_order,t_order_item",
                        "订单金额与明细汇总一致性")));
        simpleSheet(workbook.createSheet("字段约束说明"), "字段");
        simpleSheet(workbook.createSheet("关联逻辑说明"), "关联");
        scenarioSheet(workbook.createSheet("校验场景覆盖矩阵"));
        simpleSheet(workbook.createSheet("使用说明"), "说明");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return new MockMultipartFile("file", "rules.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
    }

    private MockMultipartFile workbookWithWpsNullRelationship() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        ruleSheet(workbook.createSheet("业务规则库"));
        simpleSheet(workbook.createSheet("字段约束说明"), "字段");
        simpleSheet(workbook.createSheet("关联逻辑说明"), "关联");
        scenarioSheet(workbook.createSheet("校验场景覆盖矩阵"));
        simpleSheet(workbook.createSheet("使用说明"), "说明");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        byte[] withInvalidRelationship = addWpsNullRelationship(output.toByteArray());
        return new MockMultipartFile("file", "rules.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", withInvalidRelationship);
    }

    private byte[] addWpsNullRelationship(byte[] workbookBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(workbookBytes));
             ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                zipOutput.putNextEntry(new ZipEntry(entry.getName()));
                byte[] entryBytes = input.readAllBytes();
                if ("xl/_rels/workbook.xml.rels".equals(entry.getName())) {
                    String xml = new String(entryBytes, StandardCharsets.UTF_8);
                    String wpsNullRelationship = "<Relationship Id=\"rId999\" "
                            + "Type=\"http://www.wps.cn/officeDocument/2020/cellImage\" Target=\"NULL\"/>";
                    entryBytes = xml.replace("</Relationships>", wpsNullRelationship + "</Relationships>")
                            .getBytes(StandardCharsets.UTF_8);
                }
                zipOutput.write(entryBytes);
                zipOutput.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private void ruleSheet(Sheet sheet) {
        ruleSheet(sheet, Collections.singletonList(ruleRow("R006", "实付金额与订单金额关系校验", "单表业务规则",
                "t_order", "实付金额 = 订单金额 - 优惠金额，且实付金额<=订单金额")));
    }

    private void ruleSheet(Sheet sheet, List<String[]> rules) {
        Row header = sheet.createRow(0);
        String[] headers = {"规则编号", "规则名称", "规则分类", "适用表", "规则描述",
                "校验逻辑(SQL/伪代码)", "严重等级", "异常示例"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        for (int rowIndex = 0; rowIndex < rules.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            String[] values = rules.get(rowIndex);
            for (int cellIndex = 0; cellIndex < values.length; cellIndex++) {
                row.createCell(cellIndex).setCellValue(values[cellIndex]);
            }
        }
    }

    private String[] ruleRow(String ruleId, String ruleName, String category,
                             String tables, String description) {
        return new String[] {ruleId, ruleName, category, tables, description,
                description, "严重", ruleId + "-EX"};
    }

    private void scenarioSheet(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("场景编号");
        header.createCell(1).setCellValue("覆盖规则");
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("S001");
        row.createCell(1).setCellValue("R006");
    }

    private void simpleSheet(Sheet sheet, String title) {
        sheet.createRow(0).createCell(0).setCellValue(title);
    }
}
