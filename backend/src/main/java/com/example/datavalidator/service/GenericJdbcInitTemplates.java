package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class GenericJdbcInitTemplates {
    private GenericJdbcInitTemplates() {
    }

    static Map<String, String> files(String templateName) throws Exception {
        String normalized = isBlank(templateName) ? "default" : templateName.trim().toLowerCase(Locale.ROOT);
        if ("default".equals(normalized)) {
            return defaultJdbc();
        }
        if ("order-fulfillment".equals(normalized)) {
            return orderFulfillment();
        }
        throw new BadRequestException("不支持的 jdbc 模板: " + templateName
                + "，当前仅支持默认骨架或 order-fulfillment");
    }

    private static Map<String, String> defaultJdbc() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("validator.yml", defaultValidator());
        files.put("source.yml", defaultSource());
        files.put("rules.yml", defaultRules());
        files.put("README.md", defaultReadme());
        return files;
    }

    private static Map<String, String> orderFulfillment() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("validator.yml", orderValidator());
        files.put("source.yml", orderSource());
        files.put("rules.yml", readClasspathResource("case5/rules.yml"));
        files.put("README.md", orderReadme());
        return files;
    }

    private static String defaultValidator() {
        return lines(
                "schemaVersion: 1",
                "source:",
                "  type: jdbc",
                "  file: source.yml",
                "rules:",
                "  file: rules.yml",
                "validation:",
                "  failOnSeverity: CRITICAL",
                "  formats: [json, markdown, junit]",
                "  outputDir: reports");
    }

    private static String defaultSource() {
        return lines(
                "schemaVersion: 1",
                "type: jdbc",
                "jdbc:",
                "  url: >-",
                "    jdbc:h2:mem:generic_jdbc_init;MODE=MySQL;DB_CLOSE_DELAY=-1;INIT=DROP ALL OBJECTS\\;CREATE TABLE \"contract_bill\" (\"bill_id\" VARCHAR(20), \"contract_amount\" DECIMAL(12,2), \"discount_amount\" DECIMAL(12,2), \"recv\" DECIMAL(12,2))\\;CREATE TABLE payment (payment_id VARCHAR(20), bill_id VARCHAR(20), paid_amount DECIMAL(12,2))\\;INSERT INTO \"contract_bill\" (\"bill_id\", \"contract_amount\", \"discount_amount\", \"recv\") VALUES ('B001', 1000.00, 80.00, 920.00)\\;INSERT INTO \"contract_bill\" (\"bill_id\", \"contract_amount\", \"discount_amount\", \"recv\") VALUES ('B002', 800.00, 100.00, 650.00)\\;INSERT INTO payment (payment_id, bill_id, paid_amount) VALUES ('P001', 'B001', 920.00)\\;INSERT INTO payment (payment_id, bill_id, paid_amount) VALUES ('P002', 'B999', 50.00)",
                "  driverClassName: org.h2.Driver",
                "  username: sa",
                "  dialect: h2",
                "  connectionTimeoutMs: 3000",
                "  queryTimeoutSeconds: 30",
                "  fetchSize: 100",
                "  maxRows: 100",
                "tables:",
                "  - logicalName: contract_bill",
                "    physicalName: contract_bill",
                "    primaryKey: bill_id",
                "    headers: [bill_id, contract_amount, discount_amount, receivable_amount]",
                "    fieldMappings:",
                "      receivable_amount: recv",
                "  - logicalName: payment",
                "    primaryKey: payment_id",
                "    headers: [payment_id, bill_id, paid_amount]",
                "    sql: \"SELECT payment_id AS payment_id, bill_id AS bill_id, paid_amount AS paid_amount FROM payment\"");
    }

    private static String defaultRules() {
        return lines(
                "schemaVersion: 1",
                "rules:",
                "  - ruleId: C960",
                "    ruleName: jdbc receivable amount check",
                "    category: SINGLE_BUSINESS_RULE",
                "    severity: CRITICAL",
                "    description: receivable_amount == contract_amount - discount_amount.",
                "    applicableTables: [contract_bill]",
                "    templateCode: ROW_EXPRESSION",
                "    templateParams:",
                "      tableName: contract_bill",
                "      conditions:",
                "        - left: { field: receivable_amount }",
                "          operator: \"==\"",
                "          right:",
                "            op: \"-\"",
                "            left: { field: contract_amount }",
                "            right: { field: discount_amount }",
                "  - ruleId: C961",
                "    ruleName: payment bill relation check",
                "    category: MULTI_TABLE_RELATION",
                "    severity: WARNING",
                "    description: payment.bill_id must exist in contract_bill.bill_id.",
                "    applicableTables: [payment, contract_bill]",
                "    templateCode: RELATION_EXISTS",
                "    templateParams:",
                "      source: payment",
                "      target: contract_bill",
                "      keys:",
                "        - sourceField: bill_id",
                "          targetField: bill_id",
                "      expectExists: true");
    }

    private static String defaultReadme() {
        return lines(
                "# JDBC 快速开始样板",
                "",
                "本目录提供一个最小可运行的只读 JDBC 样板，默认使用 H2 内存库，不包含本机绝对路径或明文密码。",
                "",
                "## 文件说明",
                "",
                "- `validator.yml`：CLI 主配置，引用 `source.yml` 和 `rules.yml`。",
                "- `source.yml`：只读 JDBC 数据源，演示 `physicalName`、`headers`、`fieldMappings` 与显式 SQL 列别名。",
                "- `rules.yml`：通用 YAML 模板规则，不依赖 Java 内置规则 switch。",
                "",
                "## 运行方式",
                "",
                "```bash",
                "cd backend",
                "mvn spring-boot:run -Dspring-boot.run.arguments=\"lint --config ../<your-dir>/validator.yml --output ../<your-dir>/reports/lint.json\"",
                "mvn spring-boot:run -Dspring-boot.run.arguments=\"run --config ../<your-dir>/validator.yml --json --no-report\"",
                "```",
                "",
                "默认样例会命中 1 条 CRITICAL 异常并返回退出码 `2`，这表示发现数据问题，不表示工具执行失败。",
                "",
                "## 扩展规则",
                "",
                "需要新增规则时，优先参考仓库根目录 `通用数据验证工具-CLI-规则片段库.md` 和 `examples/generic-jdbc/rule-snippets.yml`。",
                "复制片段后只替换 `ruleId`、表名和字段名，再运行 `lint --config validator.yml` 检查。",
                "",
                "## 迁移到真实只读 JDBC 库",
                "",
                "迁移到 MySQL 或其他 JDBC 库时，请至少替换以下字段：`url`、`driverClassName`、`dialect`、`physicalName`、`headers`、`fieldMappings`。",
                "真实库不要写明文 `password`，请改用 `passwordEnv`，例如：`passwordEnv: BIZ_READONLY_PASSWORD`。");
    }

    private static String orderValidator() {
        return lines(
                "schemaVersion: 1",
                "source:",
                "  type: jdbc",
                "  file: source.yml",
                "rules:",
                "  file: rules.yml",
                "validation:",
                "  failOnSeverity: CRITICAL",
                "  formats: [json, markdown]",
                "  outputDir: reports",
                "  noReport: true");
    }

    private static String orderSource() {
        return lines(
                "schemaVersion: 1",
                "type: jdbc",
                "jdbc:",
                "  url: \"jdbc:h2:mem:case5_seed_init;MODE=MySQL;DB_CLOSE_DELAY=-1;INIT=DROP ALL OBJECTS\\\\;RUNSCRIPT FROM 'classpath:db/migration/V3__seed_case5_business_tables.sql'\"",
                "  driverClassName: org.h2.Driver",
                "  username: sa",
                "  dialect: h2",
                "  connectionTimeoutMs: 3000",
                "  queryTimeoutSeconds: 30",
                "  fetchSize: 100",
                "  maxRows: 200",
                "tables:",
                "  - logicalName: t_order",
                "    primaryKey: 订单ID",
                "    headers: [订单ID, 用户ID, 订单状态, 订单金额, 实付金额, 优惠金额, 下单时间, 支付时间, 收货地址, 日期]",
                "    sql: >",
                "      SELECT \"订单ID\" AS \"订单ID\", \"用户ID\" AS \"用户ID\", \"订单状态\" AS \"订单状态\",",
                "             \"订单金额\" AS \"订单金额\", \"实付金额\" AS \"实付金额\", \"优惠金额\" AS \"优惠金额\",",
                "             \"下单时间\" AS \"下单时间\", \"支付时间\" AS \"支付时间\", \"收货地址\" AS \"收货地址\",",
                "             FORMATDATETIME(\"下单时间\", 'yyyy-MM-dd') AS \"日期\"",
                "      FROM \"t_order\"",
                "      ORDER BY \"__row_index\"",
                "  - logicalName: t_order_item",
                "    primaryKey: 明细ID",
                "    headers: [明细ID, 订单ID, 商品ID, 单价, 数量, 小计金额, 订单状态, 日期, 订单存在]",
                "    sql: >",
                "      SELECT i.\"明细ID\" AS \"明细ID\", i.\"订单ID\" AS \"订单ID\", i.\"商品ID\" AS \"商品ID\",",
                "             i.\"单价\" AS \"单价\", i.\"数量\" AS \"数量\", i.\"小计金额\" AS \"小计金额\",",
                "             o.\"订单状态\" AS \"订单状态\", FORMATDATETIME(o.\"下单时间\", 'yyyy-MM-dd') AS \"日期\",",
                "             CASE WHEN o.\"订单ID\" IS NULL THEN 'false' ELSE 'true' END AS \"订单存在\"",
                "      FROM \"t_order_item\" i",
                "      LEFT JOIN \"t_order\" o ON i.\"订单ID\" = o.\"订单ID\"",
                "      ORDER BY i.\"__row_index\"",
                "  - logicalName: t_product",
                "    primaryKey: 商品ID",
                "    headers: [商品ID, 成本价, 售价, 库存数量, 上架状态]",
                "    physicalName: t_product",
                "  - logicalName: t_payment",
                "    primaryKey: 支付ID",
                "    headers: [支付ID, 订单ID, 用户ID, 支付金额, 支付状态, 退款金额, 订单存在]",
                "    sql: >",
                "      SELECT p.\"支付ID\" AS \"支付ID\", p.\"订单ID\" AS \"订单ID\", p.\"用户ID\" AS \"用户ID\",",
                "             p.\"支付金额\" AS \"支付金额\", p.\"支付状态\" AS \"支付状态\", p.\"退款金额\" AS \"退款金额\",",
                "             CASE WHEN o.\"订单ID\" IS NULL THEN 'false' ELSE 'true' END AS \"订单存在\"",
                "      FROM \"t_payment\" p",
                "      LEFT JOIN \"t_order\" o ON p.\"订单ID\" = o.\"订单ID\"",
                "      ORDER BY p.\"__row_index\"",
                "  - logicalName: t_inventory_log",
                "    primaryKey: 流水ID",
                "    headers: [流水ID, 商品ID, 变动类型, 变动数量, 变动前库存, 变动后库存, 关联订单ID]",
                "    physicalName: t_inventory_log");
    }

    private static String orderReadme() {
        return lines(
                "# 订单履约 JDBC 样板",
                "",
                "本样板使用 H2 内存库加载内置订单履约种子数据，`rules.yml` 完全来自通用 YAML 模板规则包，可直接 `lint` 与 `run`。",
                "",
                "## 运行方式",
                "",
                "```bash",
                "cd backend",
                "mvn spring-boot:run -Dspring-boot.run.arguments=\"lint --config ../<your-dir>/validator.yml --output ../<your-dir>/reports/lint.json\"",
                "mvn spring-boot:run -Dspring-boot.run.arguments=\"run --config ../<your-dir>/validator.yml --json --no-report\"",
                "```",
                "",
                "当前样板会稳定命中严重异常并返回退出码 `2`。这表示发现达到门禁等级的数据问题，不表示工具执行失败。",
                "",
                "## 扩展规则",
                "",
                "需要新增规则时，优先参考仓库根目录 `通用数据验证工具-CLI-规则片段库.md` 和 `examples/generic-jdbc/rule-snippets.yml`。",
                "复制片段后只替换 `ruleId`、表名和字段名，再运行 `lint --config validator.yml` 检查。",
                "",
                "## 迁移到真实只读 JDBC 库",
                "",
                "1. 替换 `source.yml` 中的 `url`、`driverClassName` 与 `dialect`。",
                "2. 根据真实库表结构调整 `physicalName`、`headers`、`fieldMappings` 与自定义 SQL。",
                "3. 改用只读账号，并通过 `passwordEnv` 注入密码，例如：`passwordEnv: ORDER_FULFILLMENT_READONLY_PASSWORD`。",
                "4. 如果真实库字段名与样板字段不一致，请同步更新 `rules.yml` 中引用的表名和字段名。",
                "",
                "生成内容不包含明文数据库密码、token、secret 或本机绝对路径。");
    }

    private static String readClasspathResource(String resourcePath) throws Exception {
        try (InputStream inputStream = GenericJdbcInitTemplates.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("内置资源缺失: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
