# 赛题5：业务数据准确性验证工具-Web平台-设计文档

## 1. 文档说明

本文档基于《赛题5：业务数据准确性验证工具-Web平台-需求文档》编写，面向后续开发实现、测试验证和比赛演示交付。设计重点是将业务数据库场景下的数据准确性核验闭环落到可实现的 Java 技术架构上，明确系统分层、核心数据模型、规则执行方式、接口设计、页面设计、报告输出、AI 适配方式和测试策略。

工具的目标场景是对接各业务系统数据库，以只读方式抽取某个业务场景涉及的业务表或 SQL 查询结果，基于业务规则和规则模板输出异常疑点、证据链、原因分析和修复建议。赛题5 Excel 输入案例作为 MVP 演示数据源，用来验证端到端闭环，不代表长期唯一输入方式。

系统技术路线选择：

| 层次 | 技术选型 | 说明 |
|---|---|---|
| 后端框架 | Spring Boot | 提供 REST API、服务编排、文件上传、异常处理和静态资源托管 |
| Excel 解析 | Apache POI | 读取赛题5 Excel 多 sheet 数据，作为 MVP 演示输入和离线样例适配器 |
| 数据存储 | H2 Database + Spring Data JPA | 保存数据快照、校验任务、规则绑定、异常和报告元数据，预留 MySQL profile |
| 规则引擎 | 通用规则模板 + 内置规则执行器 | 规则执行逻辑通用化，业务语义通过规则绑定和模板参数表达，内置规则作为复杂业务兜底 |
| 输入源适配 | JDBC 只读适配器 + Excel 适配器 | 目标场景面向业务数据库只读输入，MVP 使用 Excel 证明流程闭环 |
| 报告输出 | Apache POI + Markdown/HTML 模板 | 输出 Excel、Markdown 或 HTML 核验报告 |
| 前端框架 | Vue 3 + Vite + Element Plus | 作为推荐实现，适合快速搭建演示页面 |
| 可替换前端 | React + Vite + Ant Design | 若团队前端更熟悉 React，可保持相同 API 与页面结构替换实现 |
| AI 接入 | 独立 AI 适配层 | AI 辅助生成校验 SQL 草案、规则模板映射建议、异常原因和修复建议，不参与确定性规则判定 |

## 2. 总体设计目标

### 2.1 设计原则

1. 规则执行结果稳定优先：基础校验采用通用规则模板或 Java 内置规则，避免依赖 AI 生成可执行逻辑。
2. 业务证据链清晰：每条异常必须能追溯到规则、记录、字段、实际值、期望值和关联数据。
3. 业务语义配置化：规则执行层不写死订单、商品、支付等业务表，表名、字段名、关联键和阈值通过业务元数据、规则绑定和模板参数配置。
4. MVP 范围可控：首版用赛题5固定 Excel 输入证明闭环，长期设计面向业务数据库只读输入，不建设大而全的数据质量平台。
5. 模块边界清楚：输入源适配、业务元数据映射、规则管理、规则执行、异常分析、报告导出和页面展示分层实现。
6. 前后端契约稳定：前端只依赖后端 REST API 返回的任务、统计和异常详情模型，可在 Vue 与 React 间替换。
7. AI 可开关、可降级：AI 服务不可用时，系统仍能完成规则执行，并使用内置模板生成基础解释和修复建议。
8. 数据库安全优先：业务数据库只读接入，不自动执行修复 SQL，不保存明文生产凭证。
9. 持久化可替换：业务逻辑依赖 Repository 和 Service 接口，默认 H2，后续切换 MySQL 不重写规则执行逻辑。

### 2.2 总体流程

```text
配置业务系统只读数据源
  |
  v
选择业务场景表集合或只读 SQL
  |
  v
配置逻辑表名、主键字段、字段映射和过滤条件
  |
  v
DataSourceAdapter 读取业务数据库或 Excel 演示文件
  |
  v
MetadataParser + RuleRepository 构建数据快照、业务元数据和规则库
  |
  v
Spring Data JPA 保存数据集快照
  |
  v
ValidationOrchestrator 编排规则执行
  |
  v
TemplateRuleExecutor / BuiltinRuleExecutor 执行规则绑定
  |
  v
FindingNormalizer 归并异常与补充证据
  |
  v
AI 辅助生成原因解释、修复建议和 SQL 草案
  |
  v
ReportService 生成报告
  |
  v
前端展示总览、列表、详情和导出入口
```

## 3. 系统架构

### 3.1 逻辑架构

```text
┌──────────────────────────────────────────────┐
│                  Web 前端                     │
│  数据源配置 / 规则配置 / 校验总览 / 详情 / 导出│
└──────────────────────┬───────────────────────┘
                       │ REST API
┌──────────────────────▼───────────────────────┐
│                Spring Boot API 层             │
│  DataSourceController / UploadController      │
│  ValidationController / Finding / Rule / Report│
└──────────────────────┬───────────────────────┘
                       │
┌──────────────────────▼───────────────────────┐
│                  应用服务层                   │
│  DataSourceImport / ValidationOrchestrator    │
│  RuleTemplate / Finding / Report / AI Assist  │
└──────────────────────┬───────────────────────┘
                       │
┌──────────────────────▼───────────────────────┐
│                  领域规则层                   │
│  RuleDefinition / RuleBinding / RuleTemplate  │
│  RuleResult / Finding / Evidence              │
└──────────────────────┬───────────────────────┘
                       │
┌──────────────────────▼───────────────────────┐
│                  持久化层                     │
│  Spring Data JPA Repository / H2 / MySQL      │
│  Dataset / Job / Rule / Finding / Report      │
└──────────────────────┬───────────────────────┘
                       │
┌──────────────────────▼───────────────────────┐
│                  基础设施层                   │
│  JDBC Adapter / Apache POI / Report Template  │
│  Optional AI Client                           │
└──────────────────────────────────────────────┘
```

### 3.2 部署架构

首版采用单体应用加前端静态资源的轻量部署方式：

1. 后端 Spring Boot 独立启动，提供 REST API。
2. 前端开发阶段由 Vite 启动，生产演示时可构建为静态文件，由 Spring Boot 托管或单独部署。
3. 上传文件和导出报告默认存放在本地工作目录，例如 `data/uploads`、`data/reports`。
4. 首版接入 H2 Database。默认使用 H2 `mem` 模式便于快速演示，可切换 H2 file 模式保留演示数据。
5. 导入任务、校验任务、规则定义、异常结果、规则执行情况和报告元数据通过 Spring Data JPA 保存到数据库。
6. 后续通过 Spring profile 切换到 MySQL，业务服务和规则执行层不直接依赖具体数据库实现。

## 4. 工程结构设计

推荐采用前后端分离目录结构：

```text
competition/
  backend/
    pom.xml
    src/main/java/com/example/datavalidator/
      DataValidatorApplication.java
      controller/
      service/
      domain/
      persistence/
      repository/
      rule/
      report/
      ai/
      datasource/
      infrastructure/
      config/
      exception/
    src/main/resources/
      application.yml
      application-h2.yml
      application-mysql.yml
      db/migration/
      templates/
      static/
    src/test/java/com/example/datavalidator/
  frontend/
    package.json
    index.html
    src/
      api/
      components/
      views/
      stores/
      router/
      styles/
  docs/
    samples/
```

如比赛时间较紧，也可以只实现 `backend` 并将 Vue 构建产物放入 `backend/src/main/resources/static`，通过同一个 Spring Boot 服务完成演示。

## 5. 后端模块设计

### 5.1 Controller 层

| Controller | 路径前缀 | 职责 |
|---|---|---|
| `UploadController` | `/api/files` | 接收 Excel 上传，创建导入任务 |
| `DataSourceController` | `/api/datasources` | 创建业务数据库表输入或 SQL 查询结果输入任务，MVP 可暂不提供完整前端向导 |
| `ValidationController` | `/api/validations` | 启动校验、查询任务状态和校验总览 |
| `FindingController` | `/api/findings` | 查询异常列表、筛选异常、查询异常详情 |
| `RuleController` | `/api/rules` | 查询规则列表、规则分类、规则覆盖情况和模板绑定信息 |
| `RuleTemplateController` | `/api/rule-templates` | 查询规则模板类型、模板参数和规则绑定关系 |
| `ReportController` | `/api/reports` | 生成并下载 Markdown、Excel 或 HTML 报告 |

### 5.2 Service 层

| Service | 职责 |
|---|---|
| `DataSourceImportService` | 根据输入源类型调度 JDBC 或 Excel 适配器，生成统一 `ValidationDataset` |
| `ExcelImportService` | 使用 Apache POI 读取 Excel，解析 sheet、表头和数据行 |
| `JdbcImportService` | 以只读方式执行数据库表读取或 SQL 查询，将结果转换为统一 `DataTable` |
| `MetadataParseService` | 解析字段约束、关联逻辑和校验场景矩阵 |
| `RuleLoadService` | 读取业务规则库，生成 `RuleDefinition` 集合，并补充内置规则和模板绑定信息 |
| `RuleTemplateService` | 维护规则模板、模板参数和规则绑定关系 |
| `ValidationOrchestrator` | 创建校验上下文，按规则分类调度执行器 |
| `RuleExecutionService` | 执行具体规则，生成原始异常结果 |
| `FindingService` | 标准化异常、补充证据、支持筛选和详情查询 |
| `AiAnalysisService` | 生成异常原因、影响范围和修复建议 |
| `ReportService` | 汇总统计与异常明细，生成报告文件 |
| `PersistenceSnapshotService` | 将数据集、任务、规则、异常和报告元数据保存到 H2/MySQL |
| `JobStoreService` | 基于 Repository 管理导入任务、校验任务、结果快照和报告路径 |

### 5.3 Domain 层

核心领域对象用于表达业务数据、规则、结果和报告。领域对象与 JPA 持久化实体分离：`domain/` 保持业务表达，`persistence/` 负责数据库映射，`repository/` 负责读写 H2 或 MySQL。

| 对象 | 说明 |
|---|---|
| `ValidationDataset` | 一次可校验的数据快照，可能来自业务数据库、SQL 查询结果或 Excel 演示文件；当前代码可暂沿用 `WorkbookDataset` 命名 |
| `DatasetSource` | 数据集来源，区分 Excel、数据库表和 SQL 查询结果 |
| `BusinessScenario` | 一个需要核验的业务场景，例如订单履约、客户开户、库存出入库或资金清算 |
| `BusinessTableMapping` | 业务场景中的逻辑表到物理表、SQL 查询或 Excel sheet 的映射 |
| `BusinessFieldMapping` | 业务字段名、物理字段名、字段类型、主键和展示名称的映射 |
| `DataTable` | 一张标准化后的逻辑业务表 |
| `DataRow` | 一行数据，内部用字段名到单元格值的映射表示 |
| `RuleDefinition` | 业务规则定义，包括编号、名称、类型、适用表、严重等级、描述和伪 SQL |
| `RuleTemplateDefinition` | 规则模板定义，例如非空、非负、字段等式、聚合一致性 |
| `RuleTemplateParam` | 规则模板参数定义，例如表名、字段名、关联键、比较表达式 |
| `RuleBinding` | 规则与内置执行器或模板执行器的绑定关系 |
| `FieldConstraint` | 字段约束定义 |
| `RelationDefinition` | 表关联逻辑定义 |
| `ScenarioDefinition` | 校验场景定义 |
| `ValidationJob` | 一次校验任务的状态、输入、结果和耗时 |
| `ValidationFinding` | 标准异常结果 |
| `Evidence` | 异常证据，包括关联记录、计算过程和对比值 |
| `ValidationSummary` | 校验统计摘要 |
| `ReportFile` | 导出报告元数据 |

## 6. 核心数据模型设计

### 6.1 ValidationDataset

```java
public class ValidationDataset {
    private String datasetId;
    private DatasetSourceType sourceType;
    private String scenarioId;
    private String sourceName;
    private String fileName;
    private Map<String, DataTable> businessTables;
    private List<BusinessTableMapping> tableMappings;
    private List<BusinessFieldMapping> fieldMappings;
    private List<RuleDefinition> rules;
    private List<RuleBinding> ruleBindings;
    private List<FieldConstraint> fieldConstraints;
    private List<RelationDefinition> relations;
    private List<ScenarioDefinition> scenarios;
    private LocalDateTime importedAt;
}
```

当前 MVP 代码中仍可使用 `WorkbookDataset` 承载 Excel 导入结果。后续接入数据库输入时，建议在概念和接口层逐步收敛到 `ValidationDataset`，避免模型名称暗示数据只能来自 Excel。

### 6.2 DataTable 与 DataRow

```java
public class DataTable {
    private String sheetName;
    private String logicalName;
    private DatasetSourceType sourceType;
    private List<String> headers;
    private List<DataRow> rows;
}

public class DataRow {
    private int rowIndex;
    private String primaryKey;
    private Map<String, CellValue> values;
}

public class CellValue {
    private String rawValue;
    private Object typedValue;
    private CellTypeHint typeHint;
}
```

设计说明：

1. `sourceType` 标识数据来源，Excel 输入为 `EXCEL_WORKBOOK`，数据库表输入为 `DATABASE_TABLE`，SQL 查询结果输入为 `SQL_QUERY_RESULT`。
2. `rawValue` 保留 Excel 或 ResultSet 原始展示值，便于报告展示和异常证据追溯。
3. `typedValue` 用于规则计算，例如数字、日期时间和字符串。
4. 不建立订单、商品、支付等强类型实体，避免把工具绑定到单一业务域。规则执行器通过逻辑表名和字段名访问数据。
5. 数据库输入时，`logicalName` 来自业务场景配置，物理表名和字段名保存在映射模型中，不直接暴露给模板执行器。

### 6.3 输入源类型

```java
public enum DatasetSourceType {
    EXCEL_WORKBOOK,
    DATABASE_TABLE,
    SQL_QUERY_RESULT
}
```

输入源说明：

| 类型 | 用途 | 实施优先级 |
|---|---|---|
| `DATABASE_TABLE` | 通过只读 JDBC 读取一组业务表，转换为 `DataTable` | 目标场景核心能力 |
| `SQL_QUERY_RESULT` | 执行人工确认后的只读 SQL，将查询结果作为一张逻辑表参与校验 | 目标场景增强能力 |
| `EXCEL_WORKBOOK` | 读取赛题5 Excel，多 sheet 转为统一数据集 | MVP 演示能力 |

### 6.4 RuleDefinition

```java
public class RuleDefinition {
    private String ruleId;
    private String ruleName;
    private RuleCategory category;
    private List<String> applicableTables;
    private String description;
    private String pseudoLogic;
    private Severity severity;
    private String example;
    private List<String> scenarioIds;
    private String executorType;
    private String templateCode;
}
```

枚举设计：

```java
public enum RuleCategory {
    SINGLE_FIELD_CONSTRAINT,
    SINGLE_BUSINESS_RULE,
    MULTI_TABLE_RELATION,
    METRIC_CONSISTENCY
}

public enum Severity {
    CRITICAL,
    WARNING
}
```

### 6.5 规则模板模型

```java
public class RuleTemplateDefinition {
    private String templateCode;
    private String templateName;
    private RuleTemplateType templateType;
    private String description;
    private List<RuleTemplateParam> params;
}

public class RuleTemplateParam {
    private String paramName;
    private String paramType;
    private boolean required;
    private String description;
}

public class RuleBinding {
    private String ruleId;
    private String executorType;
    private String builtinExecutorName;
    private String templateCode;
    private Map<String, Object> templateParams;
    private String bindingVersion;
    private boolean enabled;
}
```

`RuleBinding` 是规则通用化的关键模型。`RuleDefinition` 描述“要校验什么业务规则”，`RuleBinding` 描述“这条规则在当前业务场景中如何执行”。同一个 `NOT_NULL` 模板既可以校验订单表的用户 ID，也可以校验客户系统的证件号和手机号，差异只体现在 `templateParams`。

模板参数示例：

```json
{
  "templateCode": "NOT_NULL",
  "templateParams": {
    "tableName": "customer_profile",
    "fields": ["客户编号", "证件号", "手机号"]
  }
}
```

模板类型建议：

| 模板类型 | 用途 |
|---|---|
| `NOT_NULL` | 字段非空校验 |
| `NON_NEGATIVE` | 金额、库存、数量非负校验 |
| `NUMERIC_TYPE` | 数值类型校验 |
| `FIELD_EXPRESSION` | 单表字段等式或比较表达式 |
| `EXISTS_IN_TABLE` | 跨表存在性校验 |
| `FIELD_EQUALS` | 跨表字段一致性校验 |
| `AGGREGATION_EQUALS` | 聚合值与主表字段一致性校验 |
| `DUPLICATE_CHECK` | 重复记录校验 |

执行器选择策略：

1. R001 到 R030 首版仍优先绑定内置执行器，保障演示结果稳定。
2. 对可模板化规则同步维护 `templateCode` 和 `templateParams`，例如 R002 绑定 `NOT_NULL`，R017 绑定 `AGGREGATION_EQUALS`。
3. 后续迁移时可将规则绑定从 `BUILTIN` 切换为 `TEMPLATE`，无需改变前端和报告模型。

### 6.6 ValidationFinding

```java
public class ValidationFinding {
    private String findingId;
    private String ruleId;
    private String ruleName;
    private RuleCategory ruleCategory;
    private Severity severity;
    private String tableName;
    private String recordKey;
    private String fieldName;
    private String actualValue;
    private String expectedValue;
    private String description;
    private String reason;
    private String impact;
    private String suggestion;
    private List<String> scenarioIds;
    private List<Evidence> evidences;
}
```

### 6.7 Evidence

```java
public class Evidence {
    private String evidenceType;
    private String tableName;
    private String recordKey;
    private String fieldName;
    private String actualValue;
    private String expectedValue;
    private String calculation;
    private Map<String, String> relatedValues;
}
```

证据类型建议：

| 类型 | 用途 |
|---|---|
| `FIELD_VALUE` | 单字段异常，例如必填为空、金额非数值 |
| `CALCULATION` | 计算异常，例如明细小计不等于单价乘数量 |
| `RELATION_MISSING` | 关联缺失，例如明细商品ID不存在 |
| `AGGREGATION_MISMATCH` | 聚合不一致，例如订单金额不等于明细汇总 |
| `STATUS_TIME_CONFLICT` | 状态与时间冲突 |

### 6.8 持久化实体与表设计

首版使用 H2 保存系统运行数据，实体字段按 MySQL 兼容口径设计，避免使用 H2 专有类型。建议表结构如下：

| 表名 | 主要内容 |
|---|---|
| `dataset` | 数据集 ID、来源类型、文件名或数据源名、导入时间、状态 |
| `data_table_snapshot` | 数据集内逻辑表、表头 JSON、行数、来源 sheet 或 SQL |
| `data_row_snapshot` | 行号、主键、字段值 JSON，用于异常证据回放 |
| `rule_definition` | 规则编号、名称、分类、适用表、严重等级、描述、伪 SQL；主键为 `dataset_id + rule_id` |
| `rule_template_definition` | 模板编码、模板类型、参数定义 JSON |
| `rule_binding` | 规则编号、执行器类型、内置执行器名、模板编码、模板参数 JSON |
| `validation_job` | 校验任务 ID、数据集 ID、状态、开始结束时间、耗时、AI 开关 |
| `validation_finding` | 异常结果主表字段，包含规则、表、主键、严重等级和描述 |
| `finding_evidence` | 异常证据明细，包含计算过程和关联值 JSON |
| `report_file` | 报告 ID、任务 ID、格式、路径、生成时间 |

持久化约束：

1. JSON 字段在 H2 中使用 `CLOB` 或 `TEXT` 兼容写法，在 MySQL 中映射为 `TEXT`；业务代码通过 Jackson 序列化和反序列化。
2. 主键统一使用字符串 ID，避免数据库自增策略差异。
3. 时间字段使用 `LocalDateTime`，JPA 映射为标准 timestamp。
4. 表结构迁移使用 Flyway 或 Liquibase，迁移脚本同时兼容 H2 和 MySQL。

## 7. 数据源适配与 Excel 演示输入设计

本工具的目标输入是业务系统数据库中的业务表或只读 SQL 查询结果。Excel 输入是 MVP 阶段用于比赛演示、离线复现和样例验证的适配器。无论数据来自 JDBC 还是 Excel，进入规则执行层前都必须转换为统一的 `ValidationDataset`、`DataTable`、`DataRow` 和业务元数据映射。

### 7.1 业务数据库输入设计

数据库输入通过 `JdbcDataSourceAdapter` 建立只读连接，并按业务场景配置抽取数据快照。

业务场景配置至少包含：

| 配置项 | 说明 |
|---|---|
| `scenarioId` | 业务场景编号，例如客户开户、订单履约、库存扣减 |
| `sourceName` | 业务系统或数据源名称 |
| `tables` | 场景涉及的逻辑表集合 |
| `tableName` | 物理表名或只读 SQL 查询名称 |
| `logicalName` | 规则模板使用的逻辑表名 |
| `primaryKeyField` | 异常证据回放使用的主键字段 |
| `fieldMappings` | 物理字段到业务字段的映射 |
| `filterCondition` | 可选过滤条件，例如业务日期、机构号、批次号 |

数据库输入约束：

1. JDBC 连接必须只读，不执行 `INSERT`、`UPDATE`、`DELETE`、`MERGE`、`DDL` 或存储过程。
2. 表输入建议使用白名单配置，禁止前端直接拼接任意表名。
3. SQL 查询结果输入必须显式配置逻辑表名、主键字段和字段映射。
4. 只允许执行经过人工确认的 `SELECT` 查询；AI 生成的 SQL 草案不能自动执行。
5. 读取后的业务数据保存为系统侧快照，后续校验、详情和报告均基于快照，不反复访问业务库。
6. 数据源凭证通过环境变量、外部配置或受控密钥服务注入，不写入源码、文档样例或报告。

### 7.2 Excel 演示输入设计

Excel 适配器用于读取赛题5输入案例，证明数据导入、规则执行、异常分析和报告导出的闭环。Excel 中的 sheet 名称会被转换为与数据库输入相同的逻辑表名和字段映射。

#### 7.2.1 Sheet 映射

系统维护固定 sheet 到逻辑表的映射：

| Sheet | 逻辑名 | 类型 |
|---|---|---|
| `订单表_t_order` | `t_order` | 业务表 |
| `订单明细表_t_order_item` | `t_order_item` | 业务表 |
| `商品表_t_product` | `t_product` | 业务表 |
| `支付表_t_payment` | `t_payment` | 业务表 |
| `库存流水表_t_inventory_log` | `t_inventory_log` | 业务表 |
| `业务规则库` | `rules` | 规则资产 |
| `字段约束说明` | `field_constraints` | 元数据 |
| `关联逻辑说明` | `relations` | 元数据 |
| `校验场景覆盖矩阵` | `scenarios` | 验证设计 |
| `使用说明` | `readme` | 说明 |

#### 7.2.2 单元格读取策略

Apache POI 读取时统一使用 `DataFormatter` 获取展示值，同时保留数值和日期可解析结果。

读取规则：

1. 第一行作为表头。
2. 空行跳过。
3. 表头为空的列跳过。
4. 所有字段以中文表头作为访问 key。
5. MVP 演示文件的业务主键根据表类型识别：
   - `t_order`：订单ID。
   - `t_order_item`：明细ID。
   - `t_product`：商品ID。
   - `t_payment`：支付ID。
   - `t_inventory_log`：流水ID。

#### 7.2.3 导入校验

Excel 导入阶段只做结构校验，不做业务规则校验：

1. 文件扩展名必须为 `.xlsx`。
2. 必须包含 10 个目标 sheet。
3. 5 张业务表必须包含关键主键字段。
4. 规则库必须包含规则编号、规则名称、规则分类、适用表、规则描述、校验逻辑、严重等级。
5. 导入失败返回明确错误，不创建可执行校验任务。

### 7.3 输入源适配设计

系统通过 `DataSourceAdapter` 统一不同输入源，规则执行层只消费 `ValidationDataset` 和 `DataTable`，不感知数据来自 Excel、数据库表还是 SQL 查询结果。

```java
public interface DataSourceAdapter {
    boolean supports(DatasetSourceType sourceType);

    ValidationDataset load(DataSourceLoadRequest request);
}
```

适配器规划：

| 适配器 | 输入类型 | 职责 | 优先级 |
|---|---|---|---|
| `JdbcDataSourceAdapter` | `DATABASE_TABLE` | 通过只读 JDBC 读取配置的业务表，转为统一 `DataTable` | 目标场景核心 |
| `JdbcQueryResultAdapter` | `SQL_QUERY_RESULT` | 执行人工确认后的只读 SQL，将 ResultSet 转为一张逻辑表 | 目标场景增强 |
| `ExcelDataSourceAdapter` | `EXCEL_WORKBOOK` | 使用 Apache POI 读取赛题5 Excel，并解析规则、元数据和业务表 | MVP 演示 |

### 7.4 系统运行库与业务数据库

系统中存在两类数据库，职责必须隔离：

| 数据库类型 | 用途 | 约束 |
|---|---|
| 系统运行库 | 保存数据快照、规则定义、规则绑定、校验任务、异常、报告元数据 | 默认 H2，后续可切换 MySQL |
| 业务数据库 | 被校验对象，提供业务表或只读 SQL 查询结果 | 只读访问，不写入状态，不执行修复 |

兼容要求：

1. H2 与 MySQL 使用同一组 Repository 接口。
2. 表结构迁移脚本使用 Flyway 或 Liquibase 管理，禁止依赖 Hibernate 自动建表作为正式交付方式。
3. 字段类型使用 `VARCHAR`、`TEXT`、`TIMESTAMP`、`INTEGER` 等通用类型。
4. 系统运行库连接信息通过 profile 配置，业务数据库连接信息通过受控配置传入。
5. 业务数据库连接池和系统运行库连接池分离，避免误用系统写权限访问业务库。

## 8. 规则执行设计

### 8.1 规则执行接口

```java
public interface RuleExecutor {
    boolean supports(String ruleId);

    List<ValidationFinding> execute(RuleDefinition rule, RuleContext context);
}
```

`RuleContext` 提供读取数据和辅助计算的方法：

```java
public class RuleContext {
    private ValidationDataset dataset;
    private Map<String, List<DataRow>> rowsByTable;
    private Map<String, RuleBinding> ruleBindings;
    private Map<String, Map<String, DataRow>> rowsByTableAndPrimaryKey;
    private Map<String, Map<String, List<DataRow>>> relationIndexes;
}
```

### 8.2 执行编排

`ValidationOrchestrator` 执行步骤：

1. 根据 `datasetId` 读取 `ValidationDataset`。
2. 创建 `RuleContext`，根据业务元数据和规则绑定构建通用索引。
3. 加载当前业务场景的 `RuleDefinition` 和 `RuleBinding`。
4. 按规则编号稳定排序执行。
5. 规则绑定为模板执行时交给 `TemplateRuleExecutor`；复杂业务规则绑定为内置执行器时交给对应 `BuiltinRuleExecutor`。
6. 收集所有 `ValidationFinding`。
7. 通过 `FindingService` 补充场景编号、证据、原因和建议。
8. 生成 `ValidationSummary`。
9. 通过 Repository 保存任务结果、异常结果和规则执行情况。

### 8.3 规则执行器分组

| 执行器 | 覆盖规则 | 职责 |
|---|---|---|
| `TemplateRuleExecutor` | 可模板化规则 | 根据 `RuleTemplateDefinition` 和 `RuleBinding` 执行业务无关的配置化规则 |
| `OrderFieldRuleExecutor` | R001、R002、R003 | 订单金额、必填、类型校验 |
| `OrderBusinessRuleExecutor` | R004、R005、R006 | 订单金额、优惠金额、实付金额关系校验 |
| `ProductRuleExecutor` | R007、R008、R009、R010 | 商品售价、成本价、库存和上架状态校验 |
| `OrderItemRuleExecutor` | R011、R012 | 明细小计金额和数量校验 |
| `PaymentRuleExecutor` | R013、R014、R029 | 支付金额、零值、重复支付校验 |
| `InventoryRuleExecutor` | R015、R016、R027 | 库存变动连续性、入库数量、变动后库存校验 |
| `OrderItemRelationRuleExecutor` | R017、R018、R019、R030 | 订单明细汇总、商品存在性、价格一致性 |
| `PaymentRelationRuleExecutor` | R020、R021、R023、R024 | 订单支付金额、状态、存在性、用户一致性 |
| `InventoryRelationRuleExecutor` | R022、R028 | 订单库存扣减和下架商品出库校验 |
| `OrderStatusRuleExecutor` | R025、R026 | 订单时间逻辑和状态流转校验 |

### 8.4 规则模板执行设计

`TemplateRuleExecutor` 用于承接业务无关的通用校验逻辑。它不允许写死 `t_order`、`订单金额`、`商品ID` 等订单场景字段，只能通过 `templateCode` 和 `templateParams` 访问逻辑表、字段、关联键、阈值和表达式。首版不强制用模板替代全部 R001 到 R030，但设计和数据模型必须具备模板绑定能力。

模板执行流程：

1. 根据 `ruleId` 查询 `RuleBinding`。
2. 根据 `templateCode` 查询 `RuleTemplateDefinition`。
3. 校验模板参数是否完整，例如表名、字段名、关联键、聚合字段。
4. 从 `RuleContext` 读取业务表和索引。
5. 执行模板逻辑并生成标准 `ValidationFinding`。

规则模板示例：

| 场景 | 推荐模板 | 参数示例 |
|---|---|---|
| 订单金额非负 | `NON_NEGATIVE` | `table=t_order, fields=订单金额,实付金额,优惠金额` |
| 客户资料必填 | `NOT_NULL` | `table=customer_profile, fields=客户编号,证件号,手机号` |
| 支付金额类型 | `NUMERIC_TYPE` | `table=payment_record, fields=支付金额,退款金额` |
| 明细小计金额校验 | `FIELD_EXPRESSION` | `table=t_order_item, expression=小计金额 == 单价 * 数量` |
| 明细-商品关联校验 | `EXISTS_IN_TABLE` | `source=t_order_item, target=t_product, key=商品ID` |
| 订单-明细金额一致性 | `AGGREGATION_EQUALS` | `source=t_order_item, groupBy=订单ID, sum=小计金额, target=t_order.订单金额` |

首批建议优先实现 `NOT_NULL`、`NON_NEGATIVE` 和 `NUMERIC_TYPE`，用订单场景作为验证样例。后续再扩展字段表达式、跨表存在性、字段一致性和聚合一致性模板。

### 8.5 数值与时间处理

为避免 Excel 单元格格式差异影响规则判断，统一提供工具类：

| 工具类 | 职责 |
|---|---|
| `ValueParsers` | 字符串转数字、时间、整数，返回可诊断结果 |
| `MoneyUtils` | 金额比较、加减、乘法和精度处理 |
| `DateTimeUtils` | 日期时间解析和先后关系比较 |
| `RowAccessors` | 通过中文字段名读取单元格值 |

金额使用 `BigDecimal` 计算，比较前统一去除尾部零并使用固定精度策略。时间字段优先识别 Excel 日期单元格，其次按 `yyyy-MM-dd HH:mm:ss` 解析字符串。

### 8.6 异常归并策略

首版不强制去重不同规则命中的同一业务记录，因为一条记录可能同时违反多个业务规则。归并规则如下：

1. 同一规则、同一表、同一记录、同一字段的完全相同异常只保留一条。
2. 不同规则命中的同一记录分别展示。
3. 异常详情页按记录聚合展示相关异常，便于定位业务链路问题。
4. 汇总统计以异常条数为主，同时提供受影响记录数。

## 9. AI 辅助分析设计

AI 大模型在本工具中承担辅助角色，不作为确定性校验结果的唯一依据。AI 能提升规则配置、异常解释和修复分析效率，但所有可执行校验仍由规则模板或内置执行器完成。

AI 能力分为三类：

| 能力 | 用途 | 是否自动执行 |
|---|---|---|
| 规则模板推荐 | 根据业务规则文本、表结构和字段说明推荐 `templateCode` 与字段映射 | 否，需人工确认后保存为 `RuleBinding` |
| 校验 SQL 草案生成 | 根据规则说明和表结构生成只读校验 SQL 草案，辅助测试人员理解核对逻辑 | 否，只展示或人工确认后作为只读查询输入 |
| 异常辅助分析 | 根据异常证据生成原因、影响范围、修复建议和可选修复 SQL 草案 | 否，只作为文本建议 |

### 9.1 AI 异常分析适配接口

```java
public interface AiAnalysisClient {
    AiAnalysisResult analyzeFinding(AiAnalysisRequest request);
}
```

请求模型：

```java
public class AiAnalysisRequest {
    private String ruleId;
    private String ruleName;
    private String ruleDescription;
    private String tableName;
    private String recordKey;
    private String actualValue;
    private String expectedValue;
    private List<Evidence> evidences;
}
```

响应模型：

```java
public class AiAnalysisResult {
    private String reason;
    private String impact;
    private String suggestion;
    private String sqlDraft;
    private boolean generatedByAi;
}
```

### 9.2 AI 规则配置辅助

```java
public interface AiRuleAssistClient {
    AiRuleAssistResult recommendBinding(AiRuleAssistRequest request);
}
```

请求模型：

```java
public class AiRuleAssistRequest {
    private String ruleName;
    private String ruleDescription;
    private String pseudoLogic;
    private List<BusinessTableMapping> tableMappings;
    private List<BusinessFieldMapping> fieldMappings;
    private List<DataRow> sampleRows;
}
```

响应模型：

```java
public class AiRuleAssistResult {
    private String templateCode;
    private Map<String, Object> templateParams;
    private String confidence;
    private String explanation;
    private boolean requiresHumanReview;
}
```

设计约束：

1. AI 推荐结果只进入“待确认”状态，不直接覆盖已启用规则绑定。
2. 用户确认后，系统保存为 `RuleBinding`，后续由 `TemplateRuleExecutor` 确定性执行。
3. 推荐结果必须能回显表名、字段名、关联键和判断条件，便于人工检查。
4. 当规则无法映射到已有模板时，AI 只能给出“建议新增模板或使用内置执行器”的说明。

### 9.3 AI 校验 SQL 草案生成

```java
public interface AiSqlAssistClient {
    AiSqlDraftResult generateCheckSql(AiSqlDraftRequest request);
}
```

请求模型：

```java
public class AiSqlDraftRequest {
    private String ruleDescription;
    private List<BusinessTableMapping> tableMappings;
    private List<BusinessFieldMapping> fieldMappings;
    private List<RelationDefinition> relations;
    private String databaseDialect;
}
```

响应模型：

```java
public class AiSqlDraftResult {
    private String sqlDraft;
    private String explanation;
    private List<String> referencedTables;
    private List<String> referencedFields;
    private boolean readOnly;
    private boolean requiresHumanReview;
}
```

SQL 草案用途：

1. 帮助测试人员理解某条业务规则在数据库中的核对口径。
2. 辅助生成 `SQL_QUERY_RESULT` 类型输入，但必须由人工确认后才能保存或执行。
3. 在异常详情中给出人工核查 SQL 草案，帮助开发或测试复查业务库。

SQL 草案校验要求：

1. 只允许 `SELECT` 查询。
2. 禁止出现 `INSERT`、`UPDATE`、`DELETE`、`MERGE`、`DROP`、`ALTER`、`TRUNCATE`、`CREATE`、`CALL` 等关键字。
3. 必须引用业务场景配置中的白名单表和字段。
4. 必须配置查询超时和最大返回行数。
5. 默认不自动执行，只有用户显式确认并通过只读 SQL 校验后，才能作为查询输入。

### 9.4 降级策略

AI 服务不可用或未配置时，系统使用 `TemplateAnalysisClient`：

1. 根据规则类型生成固定原因模板。
2. 根据严重等级生成影响说明。
3. 根据字段和期望值生成人工核查建议。
4. `generatedByAi` 标记为 `false`。

这样可以保证比赛演示中即使没有外部 AI 服务，也能展示完整原因和建议字段。

### 9.5 AI 安全边界

1. AI 不生成 Java 可执行代码。
2. AI 不直接修改输入 Excel、业务数据库或系统运行库。
3. AI 不直接执行 SQL，生成的校验 SQL 或修复 SQL 草案必须作为文本建议展示，并标注需要人工复核。
4. AI 生成的规则模板映射必须人工确认后才可保存为启用规则绑定。
5. AI 请求不传递数据库密码、API Key、密钥、连接串等敏感信息。
6. 面向生产业务库时，AI 请求默认只传递表结构、字段说明、规则描述和脱敏样例，不传递完整真实业务明细。
7. AI 输出必须保留 `generatedByAi` 标记，报告中明确区分确定性校验结果和 AI 辅助建议。

## 10. REST API 设计

### 10.1 通用响应结构

```json
{
  "success": true,
  "message": "OK",
  "data": {},
  "traceId": "20260501123000123"
}
```

错误响应：

```json
{
  "success": false,
  "message": "缺少必需 sheet：业务规则库",
  "data": null,
  "traceId": "20260501123000124"
}
```

### 10.2 Excel 演示文件上传

```http
POST /api/files/upload
Content-Type: multipart/form-data
```

响应：

```json
{
  "datasetId": "ds-20260501-001",
  "fileName": "赛题5-业务数据准确性验证工具-输入案例.xlsx",
  "businessTableCount": 5,
  "ruleCount": 30,
  "scenarioCount": 15,
  "relationCount": 7
}
```

### 10.3 数据库输入任务

数据库输入接口是目标业务场景的核心契约。MVP 页面可暂不提供完整配置向导，但接口设计需要支持后续接入 MySQL、Oracle、PostgreSQL 或其他只读 JDBC 数据源。

数据库表输入：

```http
POST /api/datasources/database-table
Content-Type: application/json

{
  "scenarioId": "order-fulfillment-check",
  "sourceName": "mysql-readonly-demo",
  "tables": [
    {
      "logicalName": "t_order",
      "tableName": "t_order",
      "primaryKey": "订单ID"
    }
  ],
  "readonly": true
}
```

SQL 查询结果输入：

```http
POST /api/datasources/sql-query
Content-Type: application/json

{
  "scenarioId": "order-payment-check",
  "sourceName": "order-payment-check-view",
  "logicalName": "order_payment_result",
  "primaryKey": "订单ID",
  "sql": "SELECT * FROM order_payment_check_view",
  "readonly": true
}
```

响应：

```json
{
  "datasetId": "ds-20260501-db-001",
  "sourceType": "SQL_QUERY_RESULT",
  "sourceName": "order-payment-check-view",
  "businessTableCount": 1
}
```

### 10.4 启动校验

```http
POST /api/validations
Content-Type: application/json

{
  "datasetId": "ds-20260501-001",
  "enableAiAnalysis": true
}
```

响应：

```json
{
  "jobId": "job-20260501-001",
  "status": "COMPLETED",
  "startedAt": "2026-05-01T12:30:00",
  "finishedAt": "2026-05-01T12:30:03"
}
```

### 10.5 校验总览

```http
GET /api/validations/{jobId}/summary
```

响应核心字段：

```json
{
  "jobId": "job-20260501-001",
  "totalRules": 30,
  "executedRules": 30,
  "findingCount": 42,
  "criticalCount": 35,
  "warningCount": 7,
  "durationMillis": 3120,
  "byTable": {
    "t_order": 12,
    "t_order_item": 8
  },
  "byRuleCategory": {
    "SINGLE_FIELD_CONSTRAINT": 10,
    "MULTI_TABLE_RELATION": 15
  }
}
```

### 10.6 异常列表

```http
GET /api/findings?jobId=job-20260501-001&severity=CRITICAL&tableName=t_order&page=1&pageSize=20
```

响应核心字段：

```json
{
  "total": 42,
  "items": [
    {
      "findingId": "f-001",
      "ruleId": "R006",
      "ruleName": "实付金额与订单金额关系校验",
      "severity": "CRITICAL",
      "tableName": "t_order",
      "recordKey": "ORD006",
      "description": "实付金额不满足订单金额与优惠金额关系",
      "scenarioIds": ["S003"]
    }
  ]
}
```

### 10.7 异常详情

```http
GET /api/findings/{findingId}
```

响应包含：

1. 异常基本信息。
2. 实际值和期望值。
3. 证据列表。
4. 原因解释。
5. 影响范围。
6. 修复建议。

### 10.8 规则列表

```http
GET /api/rules?datasetId=ds-20260501-001
```

响应包含规则编号、规则名称、规则分类、适用表、严重等级、当前规则绑定、模板编码、模板参数摘要、执行状态和命中异常数。

### 10.9 规则模板列表

```http
GET /api/rule-templates
```

响应包含模板编码、模板名称、模板类型、参数定义和已绑定规则数。

```http
GET /api/rules/{ruleId}/binding
```

响应包含该规则当前绑定的内置执行器或模板执行器、模板编码和模板参数。

```http
PUT /api/rules/{datasetId}/{ruleId}/binding
Content-Type: application/json

{
  "executorType": "TEMPLATE",
  "templateCode": "NOT_NULL",
  "templateParams": {
    "tableName": "customer_profile",
    "fields": ["客户编号", "证件号", "手机号"]
  }
}
```

规则绑定更新必须校验模板参数、逻辑表名和字段名是否存在。

### 10.10 AI 辅助接口

规则模板推荐：

```http
POST /api/ai/rule-binding/recommend
Content-Type: application/json

{
  "datasetId": "ds-20260501-001",
  "ruleId": "R002"
}
```

校验 SQL 草案生成：

```http
POST /api/ai/sql-draft
Content-Type: application/json

{
  "datasetId": "ds-20260501-001",
  "ruleId": "R020"
}
```

AI 接口返回的结果默认只进入预览态，必须人工确认后才能保存为规则绑定或只读 SQL 查询输入。

### 10.11 报告导出

```http
POST /api/reports
Content-Type: application/json

{
  "jobId": "job-20260501-001",
  "format": "EXCEL"
}
```

下载：

```http
GET /api/reports/{reportId}/download
```

## 11. 前端设计

### 11.1 技术选型

推荐实现为 Vue 3 + Vite + Element Plus：

1. Vue 3 适合快速搭建数据看板和管理类页面。
2. Element Plus 提供上传、表格、筛选、抽屉、标签、统计卡片等组件，能减少比赛原型开发成本。
3. 前端状态简单，可使用 Pinia 或普通组合式函数管理当前 `datasetId`、`jobId`、筛选条件和详情数据。

React 替代方案：

1. React + Vite + Ant Design 保持同样页面结构和 REST API。
2. 如果团队 React 熟练度更高，可直接替换 Vue 层，不影响后端设计。
3. 本文档后续页面设计以 Vue 组件命名，但不绑定后端契约。

### 11.2 页面路由

| 路由 | 页面 | 职责 |
|---|---|---|
| `/` | `ValidationWorkbenchView` | 主工作台，包含数据源选择、校验总览、异常列表和详情 |
| `/datasources` | `DataSourceConfigView` | 业务数据库只读数据源、业务场景表集合、字段映射配置 |
| `/rules` | `RuleConfigView` | 规则列表、规则绑定、模板参数、AI 推荐预览和命中情况 |
| `/reports` | `ReportHistoryView` | 报告生成、报告下载和历史记录 |

MVP 可以只实现 `/` 一个工作台页面，并保留 Excel 快捷上传入口。后续接入业务数据库时，再将数据源配置和规则配置拆成独立页面或工作台 Tab。

### 11.3 主工作台布局

```text
┌────────────────────────────────────────────┐
│ 顶部栏：数据源/上传Excel / AI开关 / 开始校验 / 导出 │
├────────────────────────────────────────────┤
│ 数据识别摘要：业务场景 数据源 业务表 规则 关联 │
├────────────────────────────────────────────┤
│ 统计卡片：异常总数 严重 警告 已执行规则 耗时 │
├────────────────────────────────────────────┤
│ 规则配置：执行方式 模板参数 AI推荐 SQL草案预览 │
├────────────────────────────────────────────┤
│ 分布图表：按表 / 按规则类型 / 按严重等级     │
├────────────────────────────────────────────┤
│ 左：异常表格 + 筛选       右：异常详情抽屉   │
└────────────────────────────────────────────┘
```

### 11.4 前端组件拆分

| 组件 | 职责 |
|---|---|
| `DataSourcePanel` | 业务数据源选择、Excel 演示上传和导入摘要展示 |
| `ScenarioMappingPanel` | 业务场景、逻辑表、主键字段和字段映射展示 |
| `RuleBindingPanel` | 规则执行方式、模板编码、参数摘要和切换入口 |
| `AiAssistPanel` | AI 推荐规则绑定、校验 SQL 草案和人工确认入口 |
| `ValidationToolbar` | 开始校验、AI 开关、导出报告 |
| `SummaryCards` | 异常总数、严重数、警告数、规则数、耗时 |
| `DistributionCharts` | 按表、规则类型、严重等级统计展示 |
| `FindingFilters` | 规则编号、表名、严重等级、规则类型、场景筛选 |
| `FindingTable` | 异常清单表格 |
| `FindingDetailDrawer` | 异常详情、证据链、原因、影响和建议 |
| `RuleCoverageTable` | 规则列表、绑定方式和命中情况 |
| `ReportExportPanel` | 报告格式选择、生成和下载 |

### 11.5 页面交互流程

1. 用户选择业务数据库只读数据源并配置业务场景表集合，或使用 MVP Excel 上传入口。
2. 系统生成数据快照、业务表映射、字段映射、规则定义和规则绑定。
3. 用户查看规则绑定，必要时使用 AI 推荐模板映射或 SQL 草案，并人工确认。
4. 用户点击开始校验，前端调用 `/api/validations`。
5. 校验完成后调用 `/api/validations/{jobId}/summary` 和 `/api/findings`。
6. 用户修改筛选条件，前端重新请求异常列表。
7. 用户点击异常行，前端调用 `/api/findings/{findingId}` 并打开详情抽屉，查看证据链、AI 原因分析和 SQL 草案。
8. 用户点击导出报告，前端调用 `/api/reports`，再调用下载接口。

## 12. 报告设计

### 12.1 报告格式

首版支持三类报告：

| 格式 | 用途 | 实现方式 |
|---|---|---|
| Markdown | 便于提交、复盘和文档展示 | 字符串模板生成 `.md` |
| Excel | 便于筛选、排序和评委核对 | Apache POI 生成 `.xlsx` |
| HTML | 便于浏览器展示和截图 | 简单 HTML 模板生成 |

### 12.2 Excel 报告 Sheet

| Sheet | 内容 |
|---|---|
| `报告摘要` | 数据源、业务场景、执行时间、规则总数、异常总数、耗时 |
| `异常明细` | 标准异常结果字段 |
| `规则执行情况` | 规则编号、执行方式、模板编码、执行状态和命中数 |
| `按表统计` | 各业务表异常数量 |
| `按场景统计` | S001 到 S015 的异常数量 |
| `修复建议` | 重点异常的原因、影响、建议和 AI 生成的 SQL 草案 |

## 13. 异常处理与日志设计

### 13.1 异常类型

| 异常 | 场景 | 处理方式 |
|---|---|---|
| `InvalidFileException` | 文件为空、非 `.xlsx`、无法读取 | 返回 400 和明确错误 |
| `MissingSheetException` | 缺少必需 sheet | 返回 400，提示缺失 sheet 名 |
| `InvalidHeaderException` | 关键字段缺失 | 返回 400，提示表名和字段名 |
| `InvalidDataSourceException` | 数据源配置缺失、连接失败或非只读 | 返回 400，提示配置项和只读要求 |
| `UnsafeSqlException` | SQL 包含写操作、DDL、存储过程或非白名单表 | 返回 400，不保存为输入源 |
| `InvalidRuleBindingException` | 模板参数缺失、表字段不存在或模板不支持 | 返回 400，提示规则编号和参数名 |
| `RuleExecutionException` | 单条规则执行异常 | 记录日志，标记该规则执行失败，继续执行其他规则 |
| `AiAnalysisException` | AI 调用失败 | 降级为模板解释，不影响校验结果 |
| `ReportGenerateException` | 报告生成失败 | 返回错误并保留校验结果 |

### 13.2 日志内容

日志至少记录：

1. 数据源类型、业务场景编号、快照创建时间和数据量。
2. Excel sheet 或数据库表映射识别结果。
3. 规则加载数量和规则绑定数量。
4. 每条规则执行开始、结束、命中异常数和耗时。
5. AI 分析、规则推荐、SQL 草案生成是否启用及失败降级情况。
6. 报告生成路径和耗时。

日志不记录密钥、访问令牌或真实生产数据。

## 14. 配置设计

`application.yml` 建议保留通用配置，数据库连接放入 profile 文件：

```yaml
server:
  port: 8080

spring:
  profiles:
    active: h2

app:
  storage:
    upload-dir: data/uploads
    report-dir: data/reports
  validation:
    enable-ai-default: false
    stop-on-rule-error: false
  datasource-input:
    jdbc-readonly: true
    allow-sql-query-input: true
    sql-timeout-seconds: 30
    max-result-rows: 10000
  ai:
    enabled: false
    provider: template
    endpoint: ""
    api-key: ""
    allow-sql-draft: true
    allow-rule-binding-recommendation: true
```

`application-h2.yml` 建议配置：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:data-validator;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: true
```

`application-mysql.yml` 建议配置：

```yaml
spring:
  datasource:
    url: ${MYSQL_URL}
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
  flyway:
    enabled: true
```

设计说明：

1. `enable-ai-default` 默认关闭，比赛演示时可在页面手动打开。
2. `stop-on-rule-error` 默认关闭，单条规则失败不阻断整体校验。
3. H2 是 MVP 默认系统运行库，`mem` 模式用于快速演示，H2 file 模式可通过修改 JDBC URL 启用。
4. MySQL profile 用于系统运行库切换，业务数据库只读连接应独立配置。
5. Flyway 或 Liquibase 负责表结构迁移，`ddl-auto` 不用于正式建表。
6. `api-key` 不允许硬编码，真实接入时通过环境变量注入。
7. AI 生成 SQL 草案和规则绑定推荐均可单独关闭。

## 15. 测试设计

### 15.1 单元测试

| 测试对象 | 覆盖内容 |
|---|---|
| `ExcelImportService` | sheet 识别、表头解析、主键识别、缺失 sheet 错误 |
| `JdbcImportService` | 只读连接校验、ResultSet 到 `DataTable` 转换、禁止写 SQL |
| `ValueParsers` | 金额、整数、日期时间、非数值文本解析 |
| 各 `RuleExecutor` | R001 到 R030 的正常和异常样本 |
| `TemplateRuleExecutor` | 非空、非负、数值类型、字段表达式、跨表存在性、聚合一致性模板，验证模板不依赖订单业务字段 |
| `RuleBindingService` | 模板参数校验、执行方式切换、表字段不存在时报错 |
| `AiRuleAssistClient` | 规则模板推荐结果结构、人工确认标记、降级处理 |
| `AiSqlAssistClient` | 只读 SQL 草案生成、危险关键字拦截、白名单字段校验 |
| `FindingService` | 异常标准化、场景映射、证据补充 |
| Repository 层 | H2 下数据集、规则、异常、报告元数据的保存与查询 |
| `ReportService` | 报告摘要、异常明细和规则统计生成 |

### 15.2 集成测试

使用赛题5 Excel 输入案例执行完整链路：

1. 上传 Excel。
2. 解析出 5 张业务表、30 条规则、15 个场景和 7 条关联逻辑。
3. 将数据集、规则定义和导入任务保存到 H2。
4. 启动校验。
5. 将校验任务、异常结果、规则执行情况和报告元数据保存到 H2。
6. 查询汇总结果。
7. 查询异常列表和详情。
8. 导出报告。
9. 使用 H2 Repository 重新读取任务结果，确认页面查询数据来自持久化快照。

MySQL profile 验证作为后续集成测试：

1. 使用同一组迁移脚本初始化 MySQL 测试库。
2. 切换 `spring.profiles.active=mysql`。
3. 执行 Excel 导入、校验、异常查询和报告元数据查询。
4. 确认业务服务、规则执行器和前端 API 不需要修改。

业务数据库只读输入集成测试：

1. 使用 H2 或 MySQL 准备一组模拟业务表，例如客户资料表和支付流水表。
2. 配置逻辑表名、主键字段、字段映射和只读过滤条件。
3. 通过 `JdbcDataSourceAdapter` 创建数据快照。
4. 为客户资料必填、支付金额非负、支付金额数值类型绑定通用模板。
5. 启动校验并确认异常结果不依赖订单业务表名。
6. 尝试提交包含 `UPDATE` 或非白名单表的 SQL，确认被拒绝。
7. 调用 AI SQL 草案接口，确认结果只进入预览态，不自动执行。

### 15.3 前端验证

前端至少验证：

1. 文件上传成功后显示识别摘要。
2. 数据源和业务场景摘要能展示逻辑表、字段映射和规则数量。
3. 规则绑定区能展示执行方式、模板编码、参数摘要和 AI 推荐预览。
4. 开始校验后显示统计卡片。
5. 筛选条件能正确刷新异常列表。
6. 点击异常行能打开详情抽屉。
7. 导出报告按钮能触发报告生成和下载。

## 16. 实施优先级

### 16.1 第一阶段：后端离线核验闭环

1. 搭建 Spring Boot 工程。
2. 集成 H2、Spring Data JPA 和数据库迁移脚本。
3. 实现领域模型、持久化实体和 Repository。
4. 实现 Excel 导入和 sheet 解析。
5. 实现数据集、规则、任务、异常和报告元数据保存。
6. 实现 R001 到 R030 内置规则。
7. 实现异常结果标准化和汇总统计。
8. 实现报告导出。
9. 提供 Swagger 或接口文档辅助联调。

### 16.2 第二阶段：前端演示页面

1. 搭建 Vue 3 + Vite 工程。
2. 实现文件上传和识别摘要。
3. 实现校验总览和异常列表。
4. 实现异常详情抽屉。
5. 实现规则覆盖和报告导出入口。

### 16.3 第三阶段：规则绑定与通用模板

1. 落地 `RuleTemplateDefinition`、`RuleTemplateParam` 和 `RuleBinding` 管理能力。
2. 实现 `TemplateRuleExecutor` 的非空、非负和数值类型模板。
3. 为 R001、R002、R003、R008、R013 等规则补充模板绑定。
4. 增加规则绑定查询、执行方式切换和参数摘要 API。
5. 保留内置规则执行器作为复杂业务规则兜底。

### 16.4 第四阶段：AI 辅助分析与 SQL 草案

1. 实现模板化原因和建议。
2. 封装 `AiAnalysisClient`，在异常详情和报告中展示 AI 生成标记。
3. 增加 `AiRuleAssistClient`，根据规则文本和字段元数据推荐模板绑定。
4. 增加 `AiSqlAssistClient`，生成只读校验 SQL 草案和人工核查 SQL 草案。
5. 增加 AI 开关、失败降级、SQL 安全校验和人工确认机制。

### 16.5 第五阶段：业务数据库只读输入

1. 实现 `JdbcDataSourceAdapter` 和 `JdbcQueryResultAdapter` 的只读输入能力。
2. 增加数据库输入和 SQL 查询结果输入的后端 API。
3. 支持业务场景、逻辑表、主键字段、字段映射和过滤条件配置。
4. 将业务数据库数据抽取为系统侧快照，校验和报告均基于快照执行。
5. 增加数据库输入和 SQL 查询结果输入的前端配置向导。

### 16.6 第六阶段：演示打磨

1. 固化样例文件路径和演示脚本。
2. 优化页面视觉层级和筛选体验。
3. 补充运行日志和截图材料。
4. 按 S001 到 S015 校验场景逐项核对展示效果。

## 17. 设计边界

本设计明确以下边界：

1. 不直接修改业务数据库，外部数据库输入只允许只读查询。
2. 不自动执行修复 SQL，AI 生成的校验 SQL 或修复 SQL 草案只作为人工建议。
3. 不自动执行 AI 生成的 SQL，必须经过人工确认和只读校验后才能作为查询输入。
4. 不支持任意自然语言规则直接生成并执行代码。
5. 不把订单、商品、支付等赛题字段写死为长期模型，MVP 中的订单场景只作为演示样例。
6. 不引入用户权限、多租户、任务调度和审批流。
7. 不承诺处理大规模生产数据，生产级接入需补充分页抽样、脱敏、审计和权限控制。
8. MySQL 可作为系统运行库，也可作为被校验业务库；两类连接必须隔离配置。

## 18. 后续扩展方向

1. 将更多内置规则迁移到模板配置，并提供页面化规则模板维护能力。
2. 支持更多数据库类型、跨库查询视图和受控抽样策略。
3. 支持规则版本管理和规则执行历史对比。
4. 支持异常确认、忽略、修复状态流转。
5. 支持从业务规则文本自动推荐模板、字段映射和校验 SQL 草案。
6. 支持生成异常反例数据，用于测试数据构造和回归验证。

## 19. 总结

本设计以 Spring Boot + JDBC 只读适配器 + Apache POI + H2 Database + Spring Data JPA 为后端核心，以 Vue 3 + Vite + Element Plus 为推荐前端实现，建设一个面向业务数据库场景的数据准确性验证工具。赛题5 Excel 输入案例用于 MVP 演示和离线复现，长期目标是对接业务系统数据库中的场景表集合或 SQL 查询结果，并将其转换为统一数据快照。

系统通过标准异常模型和系统侧快照保障证据链可追溯，通过规则绑定和通用模板避免校验逻辑绑定单一业务域，通过内置执行器兜底复杂业务规则，通过 AI 适配层辅助生成规则模板映射、校验 SQL 草案、异常原因和修复建议。后续编码应在保留现有 Excel 演示闭环的基础上，优先推进规则模板通用化、AI 辅助能力和业务数据库只读输入。
