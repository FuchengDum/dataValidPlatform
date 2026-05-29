# 赛题5：业务数据准确性验证工具-Web平台与CLI-部署演示操作文档

本文档用于指导将本项目迁移到其他电脑后，完成前后端工程构建、启动和演示使用。项目采用前后端分离结构：

```text
competition/
  bin/        CLI 启动脚本
  backend/    Spring Boot + Apache POI + H2 + Spring Data JPA
  frontend/   Vue 3 + Vite
  examples/   CLI 最小分发样例
  各赛题输入案例/
    赛题5-业务数据准确性验证工具-输入案例.xlsx
```

## 1. 环境准备

### 1.1 必需软件

| 软件 | 建议版本 | 用途 |
|---|---:|---|
| JDK | 11 或以上 | 运行 Spring Boot 后端和 CLI Jar |
| Maven | 3.8 或以上 | 构建后端工程和 CLI Jar |
| Node.js | 18 或以上 | 构建和运行前端工程 |
| npm | 9 或以上 | 安装前端依赖 |
| 浏览器 | Chrome / Edge | 访问演示页面 |
| 本地大模型服务 | OpenAI 兼容接口，可选 | 生成 AI 异常分析和 SQL 草案 |

检查命令：

```bash
java -version
mvn -version
node -v
npm -v
```

### 1.2 推荐迁移内容

拷贝整个 `competition` 目录到新电脑，至少需要包含以下内容：

```text
backend/
bin/
examples/distribution-minimal/
examples/generic-validation/
examples/generic-jdbc/
frontend/
各赛题输入案例/
通用数据验证工具-CLI-规则片段库.md
通用数据验证工具-CLI-AI推荐补充入口.md
赛题5-业务数据准确性验证工具-Web平台-需求文档.md
赛题5-业务数据准确性验证工具-Web平台-设计文档.md
赛题5-业务数据准确性验证工具-Web平台与CLI-部署演示操作文档.md
```

可以不拷贝以下运行产物，迁移后重新构建即可：

```text
backend/target/
backend/data/
frontend/node_modules/
frontend/dist/
```

## 2. 后端工程构建与启动

### 2.1 进入后端目录

```bash
cd competition/backend
```

### 2.2 构建后端

```bash
mvn clean package
```

构建成功后，会生成：

```text
backend/target/data-validator-0.1.0.jar
```

### 2.3 开发方式启动

适合本地调试和演示准备：

```bash
mvn spring-boot:run
```

默认访问地址：

```text
http://localhost:8080
```

### 2.4 Jar 方式启动

适合迁移到新电脑后稳定演示：

```bash
java -jar target/data-validator-0.1.0.jar
```

如需指定端口：

```bash
java -jar target/data-validator-0.1.0.jar --server.port=8080
```

### 2.5 后端默认配置说明

后端默认使用 H2 内存数据库，配置位于：

```text
backend/src/main/resources/application.yml
backend/src/main/resources/application-h2.yml
```

默认配置含义：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 后端端口 | `8080` | REST API 服务端口 |
| 数据库 | `H2 mem` | 重启后数据会清空，适合演示 |
| 上传目录 | `data/uploads` | 保存上传的 Excel 文件 |
| 报告目录 | `data/reports` | 保存导出的报告 |
| H2 控制台 | `/h2-console` | 可查看内存数据库 |
| AI 开关 | `LOCAL_AI_ENABLED=false` | 默认关闭，关闭时使用本地规则化分析和 SQL 草案 |
| AI 接口地址 | `LOCAL_AI_ENDPOINT=` | OpenAI 兼容接口地址，可填基础地址或完整 chat completions 地址 |
| AI 模型 | `LOCAL_AI_MODEL=local-model` | 本地模型名称 |

H2 控制台连接信息：

```text
URL: jdbc:h2:mem:data-validator
User Name: sa
Password: 留空
```

### 2.6 启用本地 AI 模型

AI 能力是可选增强项。未启用或模型调用失败时，系统仍会使用本地规则化逻辑生成原因分析和只读 SQL 草案。

本地模型服务需兼容 OpenAI Chat Completions 请求格式，后端会调用：

```text
POST /v1/chat/completions
Authorization: Bearer <LOCAL_AI_API_KEY>
```

开发方式启动示例：

```bash
LOCAL_AI_ENABLED=true \
LOCAL_AI_ENDPOINT=http://127.0.0.1:8317/v1/chat/completions \
LOCAL_AI_API_KEY=123456 \
LOCAL_AI_MODEL=gpt-5.4 \
mvn spring-boot:run
```

`LOCAL_AI_ENDPOINT` 支持两种写法：

```text
http://127.0.0.1:8317
http://127.0.0.1:8317/v1/chat/completions
```

Jar 方式启动示例：

```bash
LOCAL_AI_ENABLED=true \
LOCAL_AI_ENDPOINT=http://127.0.0.1:8317/v1/chat/completions \
LOCAL_AI_API_KEY=123456 \
LOCAL_AI_MODEL=gpt-5.4 \
java -jar target/data-validator-0.1.0.jar
```

环境变量说明：

| 变量 | 是否必填 | 说明 |
|---|---|---|
| `LOCAL_AI_ENABLED` | 否 | `true` 时启用本地模型调用，默认 `false` |
| `LOCAL_AI_ENDPOINT` | 启用 AI 时必填 | OpenAI 兼容服务地址 |
| `LOCAL_AI_API_KEY` | 按模型服务要求 | 用于 `Authorization: Bearer`，不要写入源码 |
| `LOCAL_AI_MODEL` | 启用 AI 时必填 | 本地模型名称 |
| `LOCAL_AI_TIMEOUT_SECONDS` | 否 | 模型请求超时时间，默认 `30` 秒 |

## 3. 前端工程构建与启动

### 3.1 进入前端目录

```bash
cd competition/frontend
```

### 3.2 安装依赖

首次迁移到新电脑后执行：

```bash
npm install
```

### 3.3 开发方式启动

```bash
npm run dev
```

默认访问地址：

```text
http://localhost:5173
```

前端默认调用后端：

```text
http://localhost:8080
```

如果后端端口或主机变化，可在启动前设置 `VITE_API_BASE`：

```bash
VITE_API_BASE=http://localhost:8081 npm run dev
```

Windows PowerShell 可使用：

```powershell
$env:VITE_API_BASE="http://localhost:8081"
npm run dev
```

### 3.4 构建前端

```bash
npm run build
```

构建产物位于：

```text
frontend/dist/
```

### 3.5 预览前端构建产物

```bash
npm run preview
```

默认访问地址：

```text
http://localhost:4173
```

## 4. 推荐演示启动方式

### 4.1 启动后端

打开第一个终端：

```bash
cd competition/backend
mvn spring-boot:run
```

如果要演示本地 AI 模型生成异常分析和 SQL 草案，可改用：

```bash
cd competition/backend
LOCAL_AI_ENABLED=true \
LOCAL_AI_ENDPOINT=http://127.0.0.1:8317/v1/chat/completions \
LOCAL_AI_API_KEY=123456 \
LOCAL_AI_MODEL=gpt-5.4 \
mvn spring-boot:run
```

看到类似日志表示后端启动成功：

```text
Tomcat started on port(s): 8080
Started DataValidatorApplication
```

### 4.2 启动前端

打开第二个终端：

```bash
cd competition/frontend
npm install
npm run dev
```

看到类似日志表示前端启动成功：

```text
Local: http://localhost:5173/
```

### 4.3 打开页面

浏览器访问：

```text
http://localhost:5173
```

## 5. 演示使用流程

### 5.1 选择输入文件

点击页面右上角 `选择 Excel`，选择标准输入文件：

```text
competition/各赛题输入案例/赛题5-业务数据准确性验证工具-输入案例.xlsx
```

当前 MVP 要求上传完整赛题 Excel，不建议只拆出单个业务 sheet。即使页面筛选只关注 `t_order`，也需要完整 Excel 支持跨表规则校验。

上传成功后，页面会展示识别摘要：

| 指标 | 预期值 |
|---|---:|
| 业务表 | 5 |
| 规则数 | 30 |

### 5.2 执行校验

点击 `开始校验`。校验完成后页面会展示：

```text
异常总数
严重异常数
警告异常数
耗时 ms
异常疑点清单
规则覆盖列表
```

### 5.3 筛选异常

左侧筛选区支持：

| 筛选项 | 示例 |
|---|---|
| 严重等级 | `严重`、`警告` |
| 业务表 | `t_order`、`t_order_item`、`t_product`、`t_payment`、`t_inventory_log` |
| 规则编号 | `R006` |

例如只查看订单表异常：

```text
业务表选择 t_order
点击刷新列表
```

### 5.4 查看异常详情

点击异常清单中的任意一行，右侧会展示：

```text
命中规则
记录主键
实际值
期望值
原因分析
影响说明
修复建议
证据链
```

详情区还提供 AI 辅助操作：

| 按钮 | 作用 | 安全边界 |
|---|---|---|
| `AI 分析` | 根据异常记录、规则和证据链生成原因、影响、建议和证据摘要 | 只展示文本，不自动修改数据 |
| `只读校验 SQL` | 生成用于复核当前规则命中的 `SELECT` SQL 草案 | 只生成预览，不自动执行 |
| `人工核查 SQL` | 生成辅助人工排查上下游链路的 `SELECT` SQL 草案 | 只生成预览，不自动执行 |

如果页面显示来源为 `OPENAI_COMPATIBLE · 模型生成`，表示本次内容来自本地模型。如果显示 `LOCAL_RULE_BASED · 本地降级`，表示模型未启用、调用失败或模型返回内容未通过安全校验，系统已降级为本地规则化结果。

### 5.5 导出报告

点击 `导出报告`，系统会生成 Markdown 报告并通过浏览器打开下载地址。报告文件同时会保存到后端目录：

```text
backend/data/reports/
```

## 6. 常用接口核验

如果需要不用前端、直接核验后端接口，可使用以下命令。

### 6.1 上传 Excel

```bash
curl -F "file=@../各赛题输入案例/赛题5-业务数据准确性验证工具-输入案例.xlsx" \
  http://localhost:8080/api/files/upload
```

返回中的 `datasetId` 用于启动校验。

### 6.2 启动校验

```bash
curl -H "Content-Type: application/json" \
  -d '{"datasetId":"替换为上传返回的datasetId","enableAiAnalysis":false}' \
  http://localhost:8080/api/validations
```

返回中的 `jobId` 用于查询结果。

### 6.3 查询汇总

```bash
curl http://localhost:8080/api/validations/替换为jobId/summary
```

### 6.4 查询异常列表

```bash
curl "http://localhost:8080/api/findings?jobId=替换为jobId"
```

只查询订单表：

```bash
curl "http://localhost:8080/api/findings?jobId=替换为jobId&tableName=t_order"
```

### 6.5 生成报告

```bash
curl -H "Content-Type: application/json" \
  -d '{"jobId":"替换为jobId","format":"MARKDOWN"}' \
  http://localhost:8080/api/reports
```

### 6.6 AI 异常分析

先通过异常列表接口拿到 `findingId`，再调用：

```bash
curl http://localhost:8080/api/ai/findings/替换为findingId/analysis
```

返回中的 `source` 和 `generatedByAi` 可判断结果来源：

```text
source=OPENAI_COMPATIBLE, generatedByAi=true 表示模型生成
source=LOCAL_RULE_BASED, generatedByAi=false 表示本地降级
```

### 6.7 生成只读校验 SQL 草案

```bash
curl -H "Content-Type: application/json" \
  -d '{"draftType":"VALIDATION_CHECK","tableName":"t_payment","fieldName":"支付金额","actualValue":"-12.50","expectedValue":">= 0","recordKey":"P001"}' \
  http://localhost:8080/api/ai/sql-drafts
```

### 6.8 生成人工核查 SQL 草案

```bash
curl -H "Content-Type: application/json" \
  -d '{"draftType":"MANUAL_REVIEW","tableName":"t_payment","fieldName":"支付金额","actualValue":"-12.50","expectedValue":">= 0","recordKey":"P001"}' \
  http://localhost:8080/api/ai/sql-drafts
```

无论是否启用模型，系统都不会自动执行 SQL。模型返回的 SQL 必须是 `SELECT`，如果包含 `UPDATE`、`DELETE`、`INSERT`、`DROP`、`ALTER`、`TRUNCATE`、`MERGE`、`CREATE` 等危险关键字，后端会丢弃模型结果并降级为本地只读 SQL 草案。

## 7. 打包成单后端服务的可选方式

如演示现场希望只启动一个 Spring Boot 服务，可以把前端构建产物放到后端静态资源目录后重新打包。

### 7.1 构建前端

```bash
cd competition/frontend
npm install
npm run build
```

### 7.2 复制前端产物

将 `frontend/dist/` 下的内容复制到：

```text
backend/src/main/resources/static/
```

### 7.3 重新构建并启动后端

```bash
cd competition/backend
mvn clean package
java -jar target/data-validator-0.1.0.jar
```

浏览器访问：

```text
http://localhost:8080
```

说明：当前推荐演示方式仍是前后端分别启动，排查问题更直接。

## 8. CLI 部署演示方式

CLI 适合在没有浏览器页面、需要脚本化演示、或需要把工具作为轻量分发包交付时使用。CLI 与后端服务复用同一个 Jar，不需要启动 Web 服务。

### 8.1 CLI 分发目录

从仓库根目录打包后，CLI 演示至少需要保留以下目录结构：

```text
competition/
  bin/data-validator
  backend/target/data-validator-0.1.0.jar
  examples/distribution-minimal/
  examples/generic-validation/
  examples/generic-jdbc/
```

`examples/distribution-minimal/` 是最小可运行样例，包含：

| 文件 | 说明 |
|---|---|
| `validator.yml` | CLI 主配置，声明数据源、规则包、输出目录和失败阈值 |
| `source.yml` | 内联样例数据 |
| `rules.yml` | 通用规则包 |
| `expected-result.md` | 样例预期结果和退出码说明 |

### 8.2 构建 CLI Jar

在新电脑或演示机器上先构建后端 Jar：

```bash
cd competition/backend
mvn package -DskipTests
cd ..
```

构建完成后确认 CLI 可执行：

```bash
bin/data-validator --version
```

预期输出：

```text
data-validator 0.1.0
```

如果脚本没有执行权限，可在 macOS 或 Linux 上执行：

```bash
chmod +x bin/data-validator
```

Windows PowerShell 可直接使用 Jar：

```powershell
java -jar backend/target/data-validator-0.1.0.jar --version
```

### 8.3 运行最小样例

先校验配置、规则和数据源是否能被 CLI 正常读取：

```bash
bin/data-validator lint --config examples/distribution-minimal/validator.yml
```

再执行完整校验：

```bash
bin/data-validator run --config examples/distribution-minimal/validator.yml
```

最小样例会故意命中 1 条 `CRITICAL` 异常，用于演示质量门禁。因此 `run` 命令返回退出码 `2` 是预期结果，不表示 CLI 执行失败。

### 8.4 JSON 输出和无报告模式

如果演示重点是脚本集成，可使用 JSON 输出：

```bash
bin/data-validator run \
  --config examples/distribution-minimal/validator.yml \
  --json \
  --no-report
```

也可以绕过主配置，直接指定规则包和数据源：

```bash
bin/data-validator validate \
  --rules examples/distribution-minimal/rules.yml \
  --source examples/distribution-minimal/source.yml \
  --json \
  --no-report
```

常用退出码：

| 退出码 | 含义 |
|---:|---|
| `0` | 执行成功，且未达到失败阈值 |
| `1` | 参数、配置、文件路径或运行时错误 |
| `2` | 执行成功，但命中达到失败阈值的异常 |

### 8.5 规则推荐演示

CLI 可基于元数据和已有规则生成候选规则建议：

```bash
bin/data-validator recommend \
  --rules examples/distribution-minimal/rules.yml \
  --metadata examples/distribution-minimal/source.yml
```

该命令用于演示“已有规则资产 + 数据元信息”的规则扩展能力，不会修改原规则文件。

### 8.6 JDBC 快速上手演示

阶段 10 新增了 `init jdbc`，用于在空目录中生成可直接运行的 JDBC 校验样板。演示时建议先使用订单履约模板，它默认使用 H2 内存库，不需要数据库账号和密码。

```bash
DEMO_DIR="/tmp/order-fulfillment-demo-$(date +%Y%m%d%H%M%S)"

bin/data-validator init jdbc \
  --template order-fulfillment \
  --output "$DEMO_DIR"
```

生成目录包含：

```text
validator.yml
source.yml
rules.yml
README.md
```

先检查生成的配置和规则：

```bash
bin/data-validator lint \
  --config "$DEMO_DIR/validator.yml" \
  --output "$DEMO_DIR/reports/lint.json"
```

再执行校验：

```bash
bin/data-validator run \
  --config "$DEMO_DIR/validator.yml" \
  --json \
  --no-report
```

订单履约样板会稳定执行 30 条规则，并命中样例数据中的异常。返回退出码 `2` 表示发现达到失败阈值的数据问题，不表示工具运行失败。

### 8.7 lint 修复建议演示

阶段 11 增强了 lint 的结构化修复建议。演示时可以故意把生成目录中的 `rules.yml` 某个字段名改错，再运行：

```bash
bin/data-validator lint \
  --config "$DEMO_DIR/validator.yml" \
  --output "$DEMO_DIR/reports/lint-after-edit.json"
```

输出 JSON 中每个问题包含：

```text
code
message
path
suggestion
```

讲解重点：

1. `path` 指出具体配置位置，例如 `rules[0].templateParams.fields[0]`。
2. `suggestion` 会给出可替换字段、YAML 片段或安全配置建议。
3. JDBC 密码、token、secret 不会被回显到错误建议中。

### 8.8 规则片段库演示

阶段 12 新增了规则片段库。推荐演示顺序是：先看片段，再复制片段改字段，最后运行 lint。

可直接验证内置片段包：

```bash
bin/data-validator lint \
  --rules examples/generic-jdbc/rule-snippets.yml \
  --metadata examples/generic-jdbc/source.yml \
  --output examples/generic-jdbc/reports/rule-snippets-lint.json
```

执行片段包：

```bash
bin/data-validator validate \
  --rules examples/generic-jdbc/rule-snippets.yml \
  --source examples/generic-jdbc/source.yml \
  --output examples/generic-jdbc/reports
```

片段库覆盖 8 类常见规则：非空、非负、数值类型、金额关系、跨表存在、关联断言、聚合一致性和重复校验。详细说明见仓库根目录：

```text
通用数据验证工具-CLI-规则片段库.md
```

### 8.9 AI 推荐作为补充入口演示

阶段 13 将 `recommend` 明确为规则片段库之后的补充入口。推荐话术是：能用片段表达的规则优先复制片段；片段无法覆盖时，再生成候选规则包供人工审阅。

生成推荐 JSON 和候选规则包：

```bash
bin/data-validator recommend \
  --rules examples/generic-validation/rules.yml \
  --metadata examples/generic-validation/source.yml \
  --output examples/generic-validation/reports/recommendations.json \
  --candidate-rules examples/generic-validation/reports/rules.recommended.yml
```

如需演示 AI prompt 和模型响应落盘，可增加 `--debug-ai`：

```bash
bin/data-validator recommend \
  --rules examples/generic-validation/rules.yml \
  --metadata examples/generic-validation/source.yml \
  --output examples/generic-validation/reports/recommendations.json \
  --candidate-rules examples/generic-validation/reports/rules.recommended.yml \
  --debug-ai examples/generic-validation/reports/ai-debug
```

推荐结果 JSON 重点看：

| 字段 | 说明 |
|---|---|
| `diff` | 原始规则、推荐模板、推荐参数和推荐原因 |
| `confidence` | 推荐置信度 |
| `warningCategories` | 字段缺失、模板不支持、AI 降级、安全拒绝等分类 |
| `candidateGenerated` | 是否写入候选规则包 |

候选规则包只写入 `--candidate-rules` 指定的新文件，不会覆盖正式 `rules.yml`。低置信度、字段缺失、模板不支持、AI 降级或安全拒绝时，只输出推荐 JSON，不生成可执行候选规则。

详细边界见仓库根目录：

```text
通用数据验证工具-CLI-AI推荐补充入口.md
```

### 8.10 指定 Jar 路径和 JVM 参数

默认脚本会查找：

```text
backend/target/data-validator-0.1.0.jar
```

如果分发包中的 Jar 放在其他目录，可通过环境变量指定：

```bash
DATA_VALIDATOR_JAR=/opt/data-validator/data-validator-0.1.0.jar \
bin/data-validator --version
```

如需调整 JVM 参数：

```bash
JAVA_OPTS="-Xmx512m" \
bin/data-validator run --config examples/distribution-minimal/validator.yml
```

### 8.11 CLI 报告产物

不加 `--no-report` 时，最小样例会在运行时生成报告目录：

```text
examples/distribution-minimal/reports/
```

该目录是运行产物，迁移源码或制作干净分发包时可以不包含，演示前重新执行 CLI 会自动生成。`examples/generic-validation/reports/`、`examples/generic-jdbc/reports/` 和 `init jdbc` 生成目录下的 `reports/` 同样属于演示产物。

## 9. MySQL 扩展启动方式

当前演示默认使用 H2，不需要安装 MySQL。如需验证 MySQL profile，需要准备 MySQL 数据库，并设置环境变量：

```bash
export MYSQL_URL="jdbc:mysql://localhost:3306/data_validator?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="你的密码"
```

启动后端：

```bash
cd competition/backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

或 Jar 方式：

```bash
java -jar target/data-validator-0.1.0.jar --spring.profiles.active=mysql
```

注意：MySQL 方案用于后续扩展和持久化演示数据；比赛 MVP 演示建议使用默认 H2。

## 10. 常见问题

### 10.1 端口被占用

现象：

```text
Port 8080 was already in use
```

处理方式：

```bash
java -jar target/data-validator-0.1.0.jar --server.port=8081
```

同时前端需要指定新的后端地址：

```bash
VITE_API_BASE=http://localhost:8081 npm run dev
```

### 10.2 前端页面能打开，但接口请求失败

检查项：

1. 后端是否已启动。
2. 后端端口是否为 `8080`。
3. 前端 `VITE_API_BASE` 是否指向正确后端地址。
4. 浏览器控制台是否有跨域或网络错误。

### 10.3 上传完整 Excel 后提示规则主键冲突

原因通常是旧版本数据库结构仍在运行，`rule_definition` 只使用了 `rule_id` 作为主键。

处理方式：

1. 确认代码中存在迁移脚本：

```text
backend/src/main/resources/db/migration/V2__rule_definition_composite_pk.sql
```

2. 重启后端。
3. 如果使用 H2 内存库，重启后会自动重建表结构。
4. 如果使用 H2 file 或 MySQL，确认 Flyway 已执行 V2 迁移。

### 10.4 上传单个 sheet 失败

当前 MVP 以赛题5完整 Excel 为输入，不支持只上传单个业务 sheet。页面上的 `t_order`、`t_payment` 等选择项用于筛选校验结果，不表示导入文件只能包含该表。

### 10.5 H2 数据重启后丢失

默认 H2 使用内存模式：

```text
jdbc:h2:mem:data-validator
```

这是预期行为，适合现场快速演示。如果需要保留数据，可后续切换为 H2 file 模式或 MySQL profile。

### 10.6 Maven 或 npm 下载依赖失败

检查新电脑是否能访问 Maven Central 和 npm registry。必要时配置公司内网代理或镜像源。

npm 可临时切换镜像：

```bash
npm config set registry https://registry.npmmirror.com
npm install
```

Maven 可在用户目录配置：

```text
~/.m2/settings.xml
```

### 10.7 启用 AI 后仍显示本地降级

现象：

```text
LOCAL_RULE_BASED · 本地降级
```

常见原因：

1. 未设置 `LOCAL_AI_ENABLED=true`。
2. `LOCAL_AI_ENDPOINT` 地址不可访问。
3. 模型服务不是 OpenAI Chat Completions 兼容格式。
4. `LOCAL_AI_MODEL` 与本地模型服务中的模型名不一致。
5. 模型返回内容不是后端要求的 JSON。
6. SQL 草案中包含写操作或危险关键字，被安全校验拦截。

处理方式：

1. 确认本地模型服务已启动。
2. 确认 `LOCAL_AI_ENDPOINT` 可用。
3. 后端重启时重新传入 AI 环境变量。
4. 查看后端日志，确认是否发生模型请求超时或返回解析失败。

### 10.8 AI 接口地址如何填写

`LOCAL_AI_ENDPOINT` 支持基础地址和完整地址两种写法：

```text
http://127.0.0.1:8317
http://127.0.0.1:8317/v1/chat/completions
```

不要填写重复路径，例如：

```text
http://127.0.0.1:8317/v1/chat/completions/v1/chat/completions
```

### 10.9 CLI 提示找不到 Jar

现象：

```text
data-validator jar not found
```

处理方式：

1. 先执行 `cd backend && mvn package -DskipTests`。
2. 确认存在 `backend/target/data-validator-0.1.0.jar`。
3. 如果 Jar 放在自定义目录，设置 `DATA_VALIDATOR_JAR` 后再执行 `bin/data-validator`。

### 10.10 CLI 返回退出码 2

退出码 `2` 表示 CLI 执行成功，但校验结果命中了配置中的失败阈值。最小样例中存在 1 条 `CRITICAL` 异常，因此返回 `2` 是预期结果。

如果只想确认配置和文件路径是否正确，可先执行：

```bash
bin/data-validator lint --config examples/distribution-minimal/validator.yml
```

### 10.11 Windows 无法直接执行 bin/data-validator

`bin/data-validator` 是 sh 脚本，适用于 macOS、Linux、Git Bash 或 WSL。Windows PowerShell 可直接使用 Jar：

```powershell
java -jar backend/target/data-validator-0.1.0.jar run --config examples/distribution-minimal/validator.yml
```

## 11. 演示检查清单

演示前建议按以下顺序检查：

1. `java -version` 显示 JDK 11 或以上。
2. `mvn -version` 可正常执行。
3. `node -v` 显示 Node.js 18 或以上。
4. `npm install` 执行成功。
5. 后端 `mvn spring-boot:run` 启动成功。
6. 前端 `npm run dev` 启动成功。
7. 浏览器能打开 `http://localhost:5173`。
8. 能上传完整赛题5 Excel。
9. 页面显示业务表 `5`、规则数 `30`。
10. 点击 `开始校验` 后能显示异常统计和异常清单。
11. 能按 `t_order` 筛选异常。
12. 点击异常行能显示详情和证据链。
13. 如需演示 AI，确认本地模型服务已启动并设置 AI 环境变量。
14. 点击 `AI 分析` 能显示模型生成或本地降级结果。
15. 点击 `只读校验 SQL` 和 `人工核查 SQL` 能显示 SQL 草案。
16. 点击 `导出报告` 能生成报告。
17. 如需演示 CLI，确认 `bin/data-validator --version` 可输出版本号。
18. `bin/data-validator lint --config examples/distribution-minimal/validator.yml` 返回成功。
19. `bin/data-validator run --config examples/distribution-minimal/validator.yml` 能输出校验摘要。
20. 最小样例返回退出码 `2` 时，能说明这是命中 `CRITICAL` 异常导致的质量门禁结果。
21. `bin/data-validator init jdbc --template order-fulfillment --output <空目录>` 能生成 JDBC 样板。
22. 生成样板执行 `lint --config <目录>/validator.yml` 返回成功。
23. `examples/generic-jdbc/rule-snippets.yml` 可通过 `lint --rules ... --metadata ...` 校验。
24. `recommend --candidate-rules ... --debug-ai ...` 能生成推荐 JSON、候选规则包和 AI 调试文件。

## 12. 推荐演示话术

可以按下面顺序介绍系统能力：

1. 工具支持导入赛题5标准 Excel，自动识别 5 张业务表和规则资产。
2. 后端使用 Spring Boot + Apache POI 解析 Excel，并将数据集、规则、校验任务和异常结果保存到 H2。
3. 校验逻辑围绕 R001 到 R030 业务规则执行，覆盖字段约束、单表业务规则、跨表关联和指标一致性。
4. 前端提供上传、校验、异常筛选、异常详情和报告导出页面。
5. 异常详情中包含命中规则、实际值、期望值、原因、影响、建议和证据链，形成“发现异常到定位修复”的闭环。
6. CLI 模式支持 `init jdbc`、`lint`、`run`、`validate` 和 `recommend`，可用于脚本化校验、分发包演示和 CI 质量门禁。
7. 新增 JDBC 样板、lint 修复建议、规则片段库和 AI 推荐补充入口，让使用者可以先生成样板、再复制片段、最后用推荐能力补齐复杂规则。
8. 当前版本默认使用 H2，后续可通过 profile 切换 MySQL，并扩展数据库表输入和 SQL 查询结果输入。
