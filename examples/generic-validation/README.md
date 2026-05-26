# 通用数据验证 CLI 样例

本目录提供一组最小可运行的非订单领域样例，用于验证通用数据验证工具不依赖 `R001-R030` 内置规则分支。

## 文件说明

| 文件 | 说明 |
|---|---|
| `validator.yml` | CLI 主配置，指定数据源、规则包、报告输出目录和退出码策略 |
| `source.yml` | inline 文件数据源，包含 `contract_bill` 样例表 |
| `rules.yml` | 通用模板规则包，使用 `ROW_EXPRESSION` 校验应收金额 |

## 运行方式

先检查配置和规则资产：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="lint --config ../examples/generic-validation/validator.yml --output ../examples/generic-validation/reports/lint.json"
```

在后端目录运行：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="run --config ../examples/generic-validation/validator.yml"
```

CI 中可只输出机器可读 summary，并跳过报告落盘：

```bash
cd backend
java -jar target/data-validator-0.1.0.jar run --config ../examples/generic-validation/validator.yml --json --no-report
```

如果本机可直接执行 `java -jar`：

```bash
cd backend
mvn package -DskipTests
java -jar target/data-validator-0.1.0.jar run --config ../examples/generic-validation/validator.yml
```

生成规则绑定推荐：

优先使用仓库根目录 `通用数据验证工具-CLI-规则片段库.md` 中的规则片段。片段库无法覆盖时，再把 `recommend` 作为补充入口生成候选规则。详细边界见 `通用数据验证工具-CLI-AI推荐补充入口.md`。

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="recommend --rules ../examples/generic-validation/rules.yml --metadata ../examples/generic-validation/source.yml --output ../examples/generic-validation/reports/recommendations.json --candidate-rules ../examples/generic-validation/reports/rules.recommended.yml"
```

推荐结果 JSON 会包含 `diff`、`warningDetails`、`warningCategories` 和 `candidateGenerated`。候选规则包只写入 `--candidate-rules` 指定的新文件，不会自动覆盖正式 `rules.yml`。

如需调试 AI 推荐过程，可显式落盘 prompt 和 response：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="recommend --rules ../examples/generic-validation/rules.yml --metadata ../examples/generic-validation/source.yml --output ../examples/generic-validation/reports/recommendations.json --debug-ai ../examples/generic-validation/reports/ai-debug"
```

## 预期结果

样例中 `B002` 的 `receivable_amount` 为 `950`，但按规则应为 `contract_amount - discount_amount = 920`，因此应命中 1 条严重异常。

CLI 返回退出码 `2` 表示发现严重异常，不表示工具执行失败。

退出码：

| 退出码 | 含义 |
|---:|---|
| `0` | 执行成功，未触发配置的失败等级 |
| `1` | 工具执行失败 |
| `2` | 发现达到失败等级的数据异常 |
| `3` | 配置、规则或命令参数非法 |

校验报告和推荐结果默认输出到：

```text
examples/generic-validation/reports/
```

默认报告格式包含 JSON、Markdown 和 JUnit XML。
