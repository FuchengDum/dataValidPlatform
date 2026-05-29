# 通用文件数据源样例

本样例演示 `source.yml` 直接引用本地业务数据文件，再复用同一个通用规则包执行校验。

当前文本样例包含：

1. `data/contract_bill.csv`：CSV 表。
2. `data/payment.jsonl`：JSONL 表。
3. `data/refund.json`：JSON 数组表。

XLSX 文件读取已由 `GenericValidationRunnerTest` 动态生成工作簿覆盖，避免提交二进制样例文件。

运行：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="lint --config ../examples/generic-file-data/validator.yml --output ../examples/generic-file-data/reports/lint.json"
```

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="run --config ../examples/generic-file-data/validator.yml"
```

CI 中可只输出机器可读 summary，并跳过报告落盘：

```bash
cd backend
java -jar target/data-validator-0.1.0.jar run --config ../examples/generic-file-data/validator.yml --json --no-report
```

当前样例中 `B002` 的 `receivable_amount` 与 `contract_amount - discount_amount` 不一致，预期返回退出码 `2` 并生成 JSON、Markdown 和 JUnit XML 报告。
