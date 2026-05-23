package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GenericValidationRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void runsGenericYamlRulePackageWithoutBuiltinRuleIds() throws Exception {
        write("source.yml", ""
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: contract_bill\n"
                + "    primaryKey: 账单ID\n"
                + "    headers: [账单ID, 合同金额, 减免金额, 应收金额]\n"
                + "    rows:\n"
                + "      - 账单ID: B001\n"
                + "        合同金额: 1000\n"
                + "        减免金额: 80\n"
                + "        应收金额: 920\n"
                + "      - 账单ID: B002\n"
                + "        合同金额: 1000\n"
                + "        减免金额: 80\n"
                + "        应收金额: 950\n");
        write("rules.yml", ""
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    ruleName: 应收金额关系校验\n"
                + "    category: SINGLE_BUSINESS_RULE\n"
                + "    severity: CRITICAL\n"
                + "    templateCode: ROW_EXPRESSION\n"
                + "    templateParams:\n"
                + "      tableName: contract_bill\n"
                + "      conditions:\n"
                + "        - left: { field: 应收金额 }\n"
                + "          operator: '=='\n"
                + "          right:\n"
                + "            op: '-'\n"
                + "            left: { field: 合同金额 }\n"
                + "            right: { field: 减免金额 }\n");
        write("validator.yml", ""
                + "source:\n"
                + "  type: file\n"
                + "  file: source.yml\n"
                + "rules:\n"
                + "  file: rules.yml\n"
                + "validation:\n"
                + "  failOnSeverity: CRITICAL\n"
                + "  formats: [json, markdown]\n"
                + "  outputDir: reports\n");

        GenericValidationRunner runner = runner();
        GenericValidationResult result = runner.run(tempDir.resolve("validator.yml"));

        assertThat(result.getTotalRules()).isEqualTo(1);
        assertThat(result.getExecutedRules()).isEqualTo(1);
        assertThat(result.getCriticalCount()).isEqualTo(1);
        assertThat(result.getFindings()).hasSize(1);
        assertThat(result.getFindings().get(0).getRecordKey()).isEqualTo("B002");
        assertThat(result.getReports()).containsKeys("json", "markdown");
        assertThat(Files.exists(Path.of(result.getReports().get("json")))).isTrue();
        assertThat(runner.exitCode(result, "CRITICAL")).isEqualTo(2);
    }

    @Test
    void writesVersionedJsonAndJunitReportWithExecutionMetadata() throws Exception {
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: contract_bill\n"
                + "    primaryKey: bill_id\n"
                + "    headers: [bill_id, contract_amount, discount_amount, receivable_amount]\n"
                + "    rows:\n"
                + "      - bill_id: B001\n"
                + "        contract_amount: 1000\n"
                + "        discount_amount: 80\n"
                + "        receivable_amount: 950\n");
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    ruleName: receivable amount check\n"
                + "    category: SINGLE_BUSINESS_RULE\n"
                + "    severity: CRITICAL\n"
                + "    templateCode: ROW_EXPRESSION\n"
                + "    templateParams:\n"
                + "      tableName: contract_bill\n"
                + "      conditions:\n"
                + "        - left: { field: receivable_amount }\n"
                + "          operator: '=='\n"
                + "          right:\n"
                + "            op: '-'\n"
                + "            left: { field: contract_amount }\n"
                + "            right: { field: discount_amount }\n");
        write("validator.yml", ""
                + "schemaVersion: 1\n"
                + "source:\n"
                + "  type: file\n"
                + "  file: source.yml\n"
                + "rules:\n"
                + "  file: rules.yml\n"
                + "validation:\n"
                + "  failOnSeverity: CRITICAL\n"
                + "  formats: [json, junit]\n"
                + "  outputDir: reports\n"
                + "  commandSummary: run --config validator.yml\n");

        GenericValidationResult result = runner().run(tempDir.resolve("validator.yml"));

        JsonNode report = new ObjectMapper().readTree(Path.of(result.getReports().get("json")).toFile());
        assertThat(report.get("reportVersion").asText()).isEqualTo("1");
        assertThat(report.get("toolVersion").asText()).isNotBlank();
        assertThat(report.get("rulePackageHash").asText()).hasSize(64);
        assertThat(report.get("sourceSummary").get("type").asText()).isEqualTo("file");
        assertThat(report.get("sourceSummary").get("tables").get(0).get("rowCount").asInt()).isEqualTo(1);
        assertThat(report.get("execution").get("command").asText()).isEqualTo("run --config validator.yml");
        assertThat(report.get("execution").get("startedAt").asText()).isNotBlank();
        assertThat(result.getReports()).containsKey("junit");
        String junit = Files.readString(Path.of(result.getReports().get("junit")));
        assertThat(junit).contains("<testsuite").contains("<failure");
    }

    @Test
    void runsRulesWithChineseCategoryAndSeverityAliases() throws Exception {
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: t_order\n"
                + "    primaryKey: 订单ID\n"
                + "    headers: [订单ID, 订单金额]\n"
                + "    rows:\n"
                + "      - 订单ID: ORD001\n"
                + "        订单金额: -1\n");
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: R001\n"
                + "    ruleName: 金额非负校验\n"
                + "    category: 单表校验-字段约束\n"
                + "    severity: 严重\n"
                + "    templateCode: NON_NEGATIVE\n"
                + "    templateParams:\n"
                + "      tableName: t_order\n"
                + "      fields: [订单金额]\n");
        write("validator.yml", ""
                + "schemaVersion: 1\n"
                + "source:\n"
                + "  type: file\n"
                + "  file: source.yml\n"
                + "rules:\n"
                + "  file: rules.yml\n"
                + "validation:\n"
                + "  noReport: true\n");

        GenericValidationResult result = runner().run(tempDir.resolve("validator.yml"));

        assertThat(result.getExecutedRules()).isEqualTo(1);
        assertThat(result.getCriticalCount()).isEqualTo(1);
        assertThat(result.getFindings().get(0).getRuleCategory())
                .isEqualTo(com.example.datavalidator.domain.RuleCategory.SINGLE_FIELD_CONSTRAINT);
    }

    @Test
    void case5ClasspathAndExampleRulePackagesStayInSync() throws Exception {
        Path classpathRules = Path.of("src/main/resources/case5/rules.yml");
        Path exampleRules = Path.of("../examples/case5-seed/rules.yml");

        assertThat(Files.readString(classpathRules)).isEqualTo(Files.readString(exampleRules));
    }

    @Test
    void runsCase5SeedRulePackageFromJdbcSource() throws Exception {
        Path config = Path.of("../examples/case5-seed/validator.yml");

        GenericValidationResult result = runner().run(config);

        assertThat(result.getTotalRules()).isEqualTo(30);
        assertThat(result.getExecutedRules()).isEqualTo(30);
        assertThat(result.getSourceSummary().getTableCount()).isEqualTo(5);
        assertThat(result.getSourceSummary().getTotalRows()).isEqualTo(70);
        assertThat(result.getFindings()).isNotEmpty();
        Set<String> ruleIds = new HashSet<>();
        result.getFindings().forEach(finding -> ruleIds.add(finding.getRuleId()));
        assertThat(ruleIds).contains("R001", "R003", "R012", "R030");
    }

    @Test
    void rejectsDangerousSqlInput() {
        assertThatThrownBy(() -> SqlReadOnlyGuard.requireSelect("UPDATE t_order SET amount = 0"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SELECT");
        assertThatThrownBy(() -> SqlReadOnlyGuard.requireSelect("SELECT * FROM t_order; DELETE FROM t_order"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("注释或多语句");
        assertThatThrownBy(() -> SqlReadOnlyGuard.requireSelect("SELECT * FROM t_order"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SELECT *");
        assertThatThrownBy(() -> SqlReadOnlyGuard.requireSelect("SELECT id FROM t_order FOR UPDATE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FOR UPDATE");
        assertThatThrownBy(() -> SqlReadOnlyGuard.requireSelect("SELECT SLEEP(1) AS slow FROM t_order"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("危险函数");
        assertThat(SqlReadOnlyGuard.quoteQualifiedIdentifier("billing.contract_bill", "h2"))
                .isEqualTo("\"billing\".\"contract_bill\"");
        assertThat(SqlReadOnlyGuard.quoteQualifiedIdentifier("billing.contract_bill", "mysql"))
                .isEqualTo("`billing`.`contract_bill`");
    }

    @Test
    void loadsJdbcTablesWithGeneratedReadonlySqlAndFieldMappings() {
        JdbcTemplate jdbcTemplate = h2Jdbc("stage6_table");
        jdbcTemplate.execute("CREATE SCHEMA \"billing\"");
        jdbcTemplate.execute("CREATE TABLE \"billing\".\"contract_bill\" "
                + "(\"bill_id\" VARCHAR(20), \"contract_amount\" DECIMAL(12,2), "
                + "\"discount_amount\" DECIMAL(12,2), \"recv\" DECIMAL(12,2))");
        jdbcTemplate.update("INSERT INTO \"billing\".\"contract_bill\" VALUES ('B001', 1000, 80, 920)");

        GenericValidationConfig.SourceConfig source = jdbcSource("stage6_table");
        GenericValidationConfig.TableConfig table = jdbcTable("contract_bill", "billing.contract_bill");
        table.setHeaders(Arrays.asList("bill_id", "contract_amount", "discount_amount", "receivable_amount"));
        table.getFieldMappings().put("receivable_amount", "recv");
        source.getTables().add(table);

        Map<String, com.example.datavalidator.domain.DataTable> tables = dataSourceProvider().load(source, tempDir);

        assertThat(tables.get("contract_bill").getHeaders())
                .containsExactly("bill_id", "contract_amount", "discount_amount", "receivable_amount");
        assertThat(tables.get("contract_bill").getRows()).hasSize(1);
        assertThat(tables.get("contract_bill").getRows().get(0).getPrimaryKey()).isEqualTo("B001");
        assertThat(tables.get("contract_bill").getRows().get(0).value("receivable_amount")).isEqualTo("920");
    }

    @Test
    void loadsJdbcSqlQueryWhenOutputColumnsMatchHeaders() {
        JdbcTemplate jdbcTemplate = h2Jdbc("stage6_sql");
        jdbcTemplate.execute("CREATE TABLE contract_bill (bill_id VARCHAR(20), amount DECIMAL(12,2))");
        jdbcTemplate.update("INSERT INTO contract_bill VALUES ('B001', 120)");

        GenericValidationConfig.SourceConfig source = jdbcSource("stage6_sql");
        GenericValidationConfig.TableConfig table = new GenericValidationConfig.TableConfig();
        table.setLogicalName("contract_bill");
        table.setPrimaryKey("bill_id");
        table.setHeaders(Arrays.asList("bill_id", "amount"));
        table.setSql("SELECT bill_id AS bill_id, amount AS amount FROM contract_bill");
        source.getTables().add(table);

        Map<String, com.example.datavalidator.domain.DataTable> tables = dataSourceProvider().load(source, tempDir);

        assertThat(tables.get("contract_bill").getRows()).hasSize(1);
        assertThat(tables.get("contract_bill").getRows().get(0).value("amount")).isEqualTo("120");
    }

    @Test
    void rejectsJdbcQueryAboveMaxRows() {
        JdbcTemplate jdbcTemplate = h2Jdbc("stage6_max_rows");
        jdbcTemplate.execute("CREATE TABLE \"contract_bill\" (\"bill_id\" VARCHAR(20), \"amount\" INT)");
        jdbcTemplate.update("INSERT INTO \"contract_bill\" VALUES ('B001', 1)");
        jdbcTemplate.update("INSERT INTO \"contract_bill\" VALUES ('B002', 2)");

        GenericValidationConfig.SourceConfig source = jdbcSource("stage6_max_rows");
        source.getJdbc().setMaxRows(1);
        GenericValidationConfig.TableConfig table = jdbcTable("contract_bill", "contract_bill");
        table.setHeaders(Arrays.asList("bill_id", "amount"));
        source.getTables().add(table);

        assertThatThrownBy(() -> dataSourceProvider().load(source, tempDir))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maxRows");
    }

    @Test
    void rejectsJdbcSqlQueryMissingConfiguredHeaders() {
        JdbcTemplate jdbcTemplate = h2Jdbc("stage6_missing_header");
        jdbcTemplate.execute("CREATE TABLE contract_bill (bill_id VARCHAR(20), amount INT)");
        jdbcTemplate.update("INSERT INTO contract_bill VALUES ('B001', 1)");

        GenericValidationConfig.SourceConfig source = jdbcSource("stage6_missing_header");
        GenericValidationConfig.TableConfig table = new GenericValidationConfig.TableConfig();
        table.setLogicalName("contract_bill");
        table.setPrimaryKey("bill_id");
        table.setHeaders(Arrays.asList("bill_id", "amount"));
        table.setSql("SELECT bill_id AS bill_id FROM contract_bill");
        source.getTables().add(table);

        assertThatThrownBy(() -> dataSourceProvider().load(source, tempDir))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void rejectsJdbcUrlSecretsBeforeConnecting() {
        GenericValidationConfig.JdbcConfig jdbc = new GenericValidationConfig.JdbcConfig();
        jdbc.setUrl("jdbc:h2:mem:stage7_secret?password=plain-text");
        jdbc.setDriverClassName("org.h2.Driver");
        jdbc.setUsername("sa");
        jdbc.setDialect("h2");

        assertThatThrownBy(() -> new GenericJdbcDataSourceFactory().open(jdbc))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JDBC URL")
                .hasMessageContaining("password");
    }

    @Test
    void loadsCsvJsonJsonlAndXlsxTablesFromFileSource() throws Exception {
        write("bill.csv", "id,amount,remark\nB001,100,ok\nB002,,missing amount\n");
        write("payment.jsonl", ""
                + "{\"id\":\"P001\",\"amount\":100,\"paid\":true}\n"
                + "{\"id\":\"P002\",\"amount\":0,\"paid\":false}\n");
        write("refund.json", ""
                + "[{\"id\":\"R001\",\"amount\":5,\"reason\":\"manual\"},"
                + "{\"id\":\"R002\",\"amount\":null,\"reason\":\"empty\"}]");
        writeWorkbook("inventory.xlsx");

        GenericValidationConfig.SourceConfig source = new GenericValidationConfig.SourceConfig();
        source.setType("file");
        source.getTables().add(table("bill", "id", "bill.csv", "csv"));
        source.getTables().add(table("payment", "id", "payment.jsonl", "jsonl"));
        source.getTables().add(table("refund", "id", "refund.json", "json"));
        GenericValidationConfig.TableConfig inventory = table("inventory", "id", "inventory.xlsx", "xlsx");
        inventory.setSheet("stock");
        source.getTables().add(inventory);

        Map<String, com.example.datavalidator.domain.DataTable> tables = dataSourceProvider().load(source, tempDir);

        assertThat(tables).containsOnlyKeys("bill", "payment", "refund", "inventory");
        assertThat(tables.get("bill").getHeaders()).containsExactly("id", "amount", "remark");
        assertThat(tables.get("bill").getRows().get(1).getPrimaryKey()).isEqualTo("B002");
        assertThat(tables.get("bill").getRows().get(1).value("amount")).isEmpty();
        assertThat(tables.get("payment").getRows().get(0).value("paid")).isEqualTo("true");
        assertThat(tables.get("refund").getRows().get(1).value("amount")).isEmpty();
        assertThat(tables.get("inventory").getHeaders()).containsExactly("id", "quantity");
        assertThat(tables.get("inventory").getRows().get(0).value("quantity")).isEqualTo("9");
    }

    @Test
    void reportsUnsupportedFileFormatWithTableName() {
        GenericValidationConfig.SourceConfig source = new GenericValidationConfig.SourceConfig();
        source.setType("file");
        source.getTables().add(table("bill", "id", "bill.parquet", "parquet"));

        assertThatThrownBy(() -> dataSourceProvider().load(source, tempDir))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bill")
                .hasMessageContaining("parquet");
    }

    private GenericValidationRunner runner() {
        ObjectMapper objectMapper = new ObjectMapper();
        GenericRuleAssetLoader loader = new GenericRuleAssetLoader(objectMapper);
        GenericDataSourceProvider dataSourceProvider = dataSourceProvider(loader);
        return new GenericValidationRunner(loader, dataSourceProvider,
                new GenericValidationReportWriter(objectMapper), new TemplateRuleExecutor());
    }

    private GenericDataSourceProvider dataSourceProvider() {
        return dataSourceProvider(new GenericRuleAssetLoader(new ObjectMapper()));
    }

    private GenericDataSourceProvider dataSourceProvider(GenericRuleAssetLoader loader) {
        return new GenericDataSourceProvider(loader, mock(JdbcTemplate.class));
    }

    private JdbcTemplate h2Jdbc(String databaseName) {
        return new JdbcTemplate(new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }

    private GenericValidationConfig.SourceConfig jdbcSource(String databaseName) {
        GenericValidationConfig.SourceConfig source = new GenericValidationConfig.SourceConfig();
        source.setType("jdbc");
        source.getJdbc().setUrl("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        source.getJdbc().setDriverClassName("org.h2.Driver");
        source.getJdbc().setUsername("sa");
        source.getJdbc().setDialect("h2");
        source.getJdbc().setMaxRows(100);
        return source;
    }

    private GenericValidationConfig.TableConfig jdbcTable(String logicalName, String physicalName) {
        GenericValidationConfig.TableConfig table = new GenericValidationConfig.TableConfig();
        table.setLogicalName(logicalName);
        table.setPhysicalName(physicalName);
        table.setPrimaryKey("bill_id");
        return table;
    }

    private GenericValidationConfig.TableConfig table(String logicalName, String primaryKey,
                                                      String file, String format) {
        GenericValidationConfig.TableConfig table = new GenericValidationConfig.TableConfig();
        table.setLogicalName(logicalName);
        table.setPrimaryKey(primaryKey);
        table.setFile(file);
        table.setFormat(format);
        return table;
    }

    private void writeWorkbook(String fileName) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(tempDir.resolve(fileName))) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("stock");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("id");
            header.createCell(1).setCellValue("quantity");
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("I001");
            row.createCell(1).setCellValue(9);
            workbook.write(output);
        }
    }

    private void write(String fileName, String content) throws Exception {
        Files.writeString(tempDir.resolve(fileName), content);
    }
}
