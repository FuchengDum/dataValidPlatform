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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private final ExcelImportService service = new ExcelImportService(datasetRepository, tableRepository,
            rowRepository, ruleRepository, bindingRepository, new JsonService(new ObjectMapper()), dataProvider);

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

    private Map<String, DataTable> seedTables() {
        Map<String, DataTable> tables = new LinkedHashMap<>();
        tables.put("t_order", table("t_order", "订单ID", "用户ID", "订单金额", "优惠金额", "实付金额"));
        tables.put("t_order_item", table("t_order_item", "明细ID", "订单ID", "商品ID", "小计金额"));
        tables.put("t_product", table("t_product", "商品ID", "商品名称", "库存数量"));
        tables.put("t_payment", table("t_payment", "支付ID", "订单ID", "支付金额", "支付状态"));
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

    private void ruleSheet(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] headers = {"规则编号", "规则名称", "规则分类", "适用表", "规则描述",
                "校验逻辑(SQL/伪代码)", "严重等级", "异常示例"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("R006");
        row.createCell(1).setCellValue("实付金额与订单金额关系校验");
        row.createCell(2).setCellValue("单表业务规则");
        row.createCell(3).setCellValue("t_order");
        row.createCell(4).setCellValue("实付金额 = 订单金额 - 优惠金额，且实付金额<=订单金额");
        row.createCell(5).setCellValue("实付金额 = 订单金额 - 优惠金额 AND 实付金额 <= 订单金额");
        row.createCell(6).setCellValue("严重");
        row.createCell(7).setCellValue("ORD006");
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
