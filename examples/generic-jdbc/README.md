# 通用 JDBC 数据源样例

本样例演示通用验证工具通过只读 JDBC 接入业务库。H2 配置可直接运行，MySQL 仅提供模板，密码必须通过环境变量传入。

运行 lint：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="lint --config ../examples/generic-jdbc/validator.yml --output ../examples/generic-jdbc/reports/lint.json"
```

运行校验：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="run --config ../examples/generic-jdbc/validator.yml"
```

CI 中可只输出机器可读 summary，并跳过报告落盘：

```bash
cd backend
java -jar target/data-validator-0.1.0.jar run --config ../examples/generic-jdbc/validator.yml --json --no-report
```

当前 H2 样例会通过 `INIT=RUNSCRIPT` 初始化内存库。`contract_bill` 使用表模式，工具生成显式字段 SQL；`payment` 使用 SQL 模式，SQL 必须显式声明列名或别名。

源码压缩包不包含 `backend/target/data-validator-0.1.0.jar` 时，需要先在
`backend/` 目录执行 `mvn package -DskipTests`。Windows 下请从同一目录
使用批处理入口执行 CLI：

```bat
cd backend
..\bin\data-validator.cmd lint --config ..\examples\generic-jdbc\validator.yml
..\bin\data-validator.cmd run --config ..\examples\generic-jdbc\validator.yml --json --no-report
```

默认报告格式包含 JSON、Markdown 和 JUnit XML。CLI 返回退出码 `2` 表示发现达到失败等级的数据异常，不表示工具执行失败。

## 规则片段库

`rule-snippets.yml` 提供 8 类可复制规则片段：非空、非负、数值类型、金额关系、跨表存在、关联断言、聚合一致性和重复校验。片段说明见仓库根目录 `通用数据验证工具-CLI-规则片段库.md`。

验证片段库：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="lint --rules ../examples/generic-jdbc/rule-snippets.yml --metadata ../examples/generic-jdbc/source.yml"
mvn spring-boot:run -Dspring-boot.run.arguments="validate --rules ../examples/generic-jdbc/rule-snippets.yml --source ../examples/generic-jdbc/source.yml --output ../examples/generic-jdbc/reports"
```

MySQL 配置请从 `mysql-template.yml` 复制到自己的 `source.yml`，并设置只读账号密码环境变量：

```bash
export BIZ_READONLY_PASSWORD=your-readonly-password
```

不要在 YAML、源码或样例中写入明文数据库密码。
