# 赛题5 AI 规则模板通用推荐与执行器拓展设计

## 1. 背景与当前状态

当前系统已经形成“数据导入、规则校验、异常详情、AI 辅助、报告导出”的基础闭环，并补齐了规则绑定推荐和模板执行能力：

1. AI 能力已覆盖异常分析、只读 SQL 草案、规则模板绑定推荐。
2. 规则覆盖区已支持“AI 推荐、预览、应用推荐”，应用时复用现有规则绑定更新接口。
3. 后端已支持以下模板执行：
   - `NOT_NULL`：字段非空。
   - `NON_NEGATIVE`：字段非负。
   - `NUMERIC_TYPE`：字段数值类型。
   - `FIELD_EXPRESSION`：单表字段表达式。
   - `ROW_EXPRESSION`：结构化行表达式。
   - `EXISTS_IN_TABLE`：跨表存在性。
   - `FIELD_EQUALS`：跨表字段一致。
   - `JOIN_ASSERT`：结构化跨表 join 断言。
   - `AGGREGATION_EQUALS`：分组汇总一致。
   - `AGGREGATE_ASSERT`：结构化聚合断言。
   - `DUPLICATE_ASSERT`：结构化分组次数断言。
   - `DUPLICATE_CHECK`：重复记录检查。
4. `FIELD_EXPRESSION` 已支持多条件表达式，并已将异常详情调整为“计算明细 + 失败条件”。
5. 绑定接口已对模板参数做表名、字段名和表达式可解析性校验。

当前主要不足：

1. 统一 DSL 的主要执行形态已落地，默认导入绑定已优先使用一等模板。
2. `FIELD_EQUALS`、`AGGREGATION_EQUALS`、`DUPLICATE_CHECK` 仍作为兼容模板存在，后续需要逐步降级为历史兼容入口。
3. R017、R020、R030 的语义推荐和默认导入绑定已可落到 `AGGREGATE_ASSERT`。
4. R030 的跨表指标级聚合能力已在执行器层验证，但还需要进入 30 条规则语义映射回归清单。
5. R019、R024 默认导入绑定已迁移到 `JOIN_ASSERT`，R029 默认导入绑定已迁移到 `DUPLICATE_ASSERT`。
6. 生产化业务库仍需要从演示 H2 形态进一步拆分为系统库和业务只读库。

## 2. 拓展目标

本阶段拓展目标是把“规则模板推荐”从特例驱动升级为通用语义映射驱动：

1. 建立本地规则语义映射层，集中处理规则文本到模板候选的推断。
2. 将 AI 可应用推荐范围扩展到所有已可执行模板。
3. 推荐结果必须通过模板白名单、字段存在性、参数结构和可执行性校验。
4. 保持 AI 推荐只作为候选建议，必须人工确认后才保存为规则绑定。
5. 将业务数据源优先调整为数据库读取，赛题 Excel 中的业务表仅作为一次性 SQL seed 来源。
6. 新增结构化 `ROW_EXPRESSION` 模板，用通用 DSL 表达单行字段间计算关系。
7. 优化模板执行后的异常详情展示，让测试人员能直接理解“错在哪里”。

## 3. 总体设计

### 3.0 业务数据源数据库化

后续业务数据源最终面向数据库。当前阶段先把赛题 Excel 中的业务表转换为 SQL 初始化脚本，在应用启动时通过 Flyway 加载到 H2 演示业务库；Excel 上传入口只继续解析规则、场景覆盖矩阵、字段约束、关联说明等非业务资产。

```text
赛题 Excel 业务 sheet
        |
        v
一次性转换为 Flyway SQL seed
        |
        v
H2 演示业务表 t_order / t_order_item / t_product / t_payment / t_inventory_log
        |
        v
BusinessTableDataProvider 读取为标准 DataTable / DataRow
        |
        v
内置规则执行器 + 模板执行器 + ROW_EXPRESSION DSL 执行器
```

该调整的目标不是让 H2 成为最终生产业务库，而是尽早让校验链路面向数据库形态：业务表数据从数据库读取，规则资产从 Excel 或后续规则配置入口读取。后续接入 MySQL 等真实业务库时，只需要替换业务表读取层的数据源或查询配置，规则绑定、模板校验和执行器接口保持稳定。

第一阶段保留系统运行库和演示业务库共用当前 H2 连接，减少配置复杂度。后续生产化时再拆分系统库 DataSource 与业务库只读 DataSource，避免系统写权限误用于业务库。

### 3.1 推荐链路

后续推荐链路统一调整为：

```text
规则定义 + 伪 SQL + 适用表 + 数据集字段快照
        |
        v
本地规则语义映射器生成候选推荐
        |
        v
AI 开启时请求模型生成候选推荐
        |
        v
统一模板白名单与参数可执行性校验
        |
        +-- 校验通过：返回 AI 推荐
        |
        +-- 校验失败：返回本地语义映射推荐，并附 warning
```

本地语义映射器不只是兜底，还承担安全基准作用。AI 推荐只能在通过相同校验后替换本地候选。

### 3.2 内部语义映射结果

后续可新增内部结果类型，用于承载本地映射和 AI 校验后的候选：

```java
public class RuleTemplateSemanticMatch {
    private String templateCode;
    private Map<String, Object> templateParams;
    private String confidence;
    private String matchedReason;
    private List<String> warnings;
    private boolean applicable;
}
```

该类型不作为前端公共 API 暴露。对外仍转换为现有推荐响应结构：

```java
public class RuleBindingRecommendationResult {
    private String templateCode;
    private Map<String, Object> templateParams;
    private String confidence;
    private String explanation;
    private boolean requiresHumanReview;
    private String source;
    private boolean generatedByAi;
    private List<String> warnings;
}
```

### 3.3 通用语义映射策略

语义映射器根据规则名称、描述、伪 SQL、适用表和字段快照生成模板候选。简单规则继续映射到现有模板；字段间计算关系优先映射到结构化 `ROW_EXPRESSION`，避免后端为每类业务金额关系写特例。

| 语义类型 | 推荐模板 | 识别信号 | 必要参数 |
|---|---|---|---|
| 字段非空 | `NOT_NULL` | 非空、不能为空、必填、not null | `tableName`, `fields` |
| 字段非负 | `NON_NEGATIVE` | 非负、不得小于 0、>= 0、金额/数量下限 | `tableName`, `fields` |
| 数值类型 | `NUMERIC_TYPE` | 数值、金额、数量、类型、numeric | `tableName`, `fields` |
| 单表字段表达式 | `FIELD_EXPRESSION` | 已有自由文本表达式绑定 | `tableName`, `expression` |
| 单行结构化表达式 | `ROW_EXPRESSION` | 等于、不得大于、小于等于、计算关系、字段间算术 | `tableName`, `conditions` |
| 跨表存在性 | `EXISTS_IN_TABLE` | 必须存在于、关联记录、主外键、商品/订单/支付引用 | `source`, `target`, `key` |
| 跨表字段一致 | `JOIN_ASSERT` | 应与、必须等于、两表字段一致 | `source`, `target`, `keys`, `assert` |
| 汇总一致 | `AGGREGATION_EQUALS` | 小计之和、汇总、合计、sum、明细到主表 | `source`, `target`, `groupBy`, `sum`, `targetField` |
| 重复检查 | `DUPLICATE_ASSERT` | 唯一、重复、不得重复、唯一组合、最多/至少 N 次 | `table`, `groupBy`, `assert` |

R006 金额关系不再作为孤立硬编码规则处理。后续应通过结构化 `ROW_EXPRESSION` 映射生成：

```json
{
  "tableName": "t_order",
  "conditions": [
    {
      "left": { "field": "实付金额" },
      "operator": "==",
      "right": {
        "op": "-",
        "left": { "field": "订单金额" },
        "right": { "field": "优惠金额" }
      }
    },
    {
      "left": { "field": "实付金额" },
      "operator": "<=",
      "right": { "field": "订单金额" }
    }
  ]
}
```

该 DSL 来自规则文本、伪 SQL 和字段元数据。若其他业务表出现类似“应收金额 = 合同金额 - 减免金额，且应收金额不大于合同金额”的规则，AI 只需要输出相同结构、不同字段名的 AST，后端执行器不需要新增业务特例。

### 3.4 赛题 30 条规则的通用抽象

对 R001 到 R030 的重新梳理目标不是把 30 条规则逐条模板化，而是抽取可迁移到其他数据库业务表的规则方法。后续推荐器应优先识别规则属于哪一种通用方法，再生成对应 DSL 参数。

| 通用方法 | 职责 | 可覆盖规则类型 |
|---|---|---|
| `FIELD_CHECK` | 单字段或多字段基础约束，包括非空、非负、数值类型、正整数、枚举值等。 | 字段必填、金额下限、库存下限、数量合法性 |
| `ROW_ASSERT` | 单表逐行断言，支持 `where` 条件和 `assert` 条件，表达字段间计算、状态约束、时间先后、条件分支。 | 金额关系、状态金额关系、库存连续性、时间逻辑 |
| `RELATION_EXISTS` | 跨表关联存在性，支持单字段或多字段 join、目标过滤条件、反向不存在校验。 | 主外键存在、支付记录存在、库存流水存在 |
| `JOIN_ASSERT` | 跨表 join 后的字段或表达式一致性断言。 | 用户一致、价格一致、下架商品不应出库 |
| `AGGREGATE_ASSERT` | 分组聚合后与目标字段或另一组聚合结果比较。 | 明细汇总、支付汇总、指标汇总一致 |
| `DUPLICATE_ASSERT` | 分组唯一性或次数约束，支持过滤条件和 `count` 比较。 | 重复支付、唯一组合、最多/至少 N 次 |

该分层可以覆盖当前赛题规则，也能迁移到客户、合同、账单、资金、物流、库存等其他业务库。AI 推荐时不应直接猜“字段类型模板”，而应先判断规则是否表达了业务关系；只要规则包含字段间计算、状态过滤、跨表 join、聚合或重复语义，就应选择对应的关系型 DSL。

### 3.5 30 条规则映射清单

| 规则 | 通用方法 | 推荐 DSL / 模板方向 | 通用化说明 |
|---|---|---|---|
| R001 | `FIELD_CHECK.NON_NEGATIVE` | `table=t_order`, `fields=[订单金额,实付金额,优惠金额]` | 任意金额字段非负约束。 |
| R002 | `FIELD_CHECK.NOT_NULL` | `table=t_order`, `fields=[用户ID,订单状态,下单时间,收货地址]` | 任意业务必填字段。 |
| R003 | `FIELD_CHECK.NUMERIC_TYPE` | `table=t_order`, `fields=[订单金额,实付金额]` | 数据库强类型后主要用于字符串型源、导入源或元数据校验。 |
| R004 | `ROW_ASSERT` | `assert 优惠金额 <= 订单金额 * 0.5` | 通用比例上限规则。 |
| R005 | `ROW_ASSERT` | `where 订单状态 == 已支付`, `assert 订单金额 != 0` | 状态驱动的字段值约束。 |
| R006 | `ROW_ASSERT` | `assert 实付金额 == 订单金额 - 优惠金额 && 实付金额 <= 订单金额` | 通用字段间金额关系。 |
| R007 | `ROW_ASSERT` | `where 上架状态 == 上架`, `assert 售价 >= 成本价` | 状态过滤后的字段大小关系。 |
| R008 | `FIELD_CHECK.NON_NEGATIVE` | `table=t_product`, `fields=[库存数量]` | 任意数量/库存非负。 |
| R009 | `ROW_ASSERT` | `where 上架状态 == 上架`, `assert 库存数量 > 0` | 状态过滤后的正数约束。 |
| R010 | `ROW_ASSERT` | `where 上架状态 == 上架`, `assert 成本价 != 0 && 售价 != 0` | 状态过滤后的零值约束。 |
| R011 | `ROW_ASSERT` | `assert 小计金额 == 单价 * 数量`, `tolerance=0.01` | 通用乘法计算关系。 |
| R012 | `FIELD_CHECK.POSITIVE_INTEGER` | `field=数量`, `assert 数量 > 0 && 数量 == floor(数量)` | 强类型数据库下可退化为正数校验。 |
| R013 | `FIELD_CHECK.NON_NEGATIVE` | `table=t_payment`, `fields=[支付金额,退款金额]` | 支付类金额非负。 |
| R014 | `ROW_ASSERT` | `where 支付状态 == 支付成功`, `assert 支付金额 != 0` | 状态驱动的零值约束。 |
| R015 | `ROW_ASSERT` | `assert 变动后库存 == 变动前库存 + if(变动类型==入库, 变动数量, 0-变动数量)` | 条件分支计算关系。 |
| R016 | `ROW_ASSERT` | `where 变动类型 in [入库,出库]`, `assert 变动数量 > 0` | 枚举状态下的正数约束。 |
| R017 | `AGGREGATE_ASSERT` | `sum(t_order_item.小计金额) by 订单ID == t_order.订单金额` | 明细到主表汇总一致。 |
| R018 | `RELATION_EXISTS` | `t_order_item.商品ID exists in t_product.商品ID` | 外键/引用存在性。 |
| R019 | `JOIN_ASSERT` | join 商品ID 后 `t_order_item.单价 == t_product.售价`, `tolerance=0.01` | 跨表字段一致或近似一致。 |
| R020 | `AGGREGATE_ASSERT` | `sum(t_payment.支付金额) by 订单ID == t_order.实付金额` | 支付流水汇总到订单。 |
| R021 | `RELATION_EXISTS` / `ANTI_EXISTS` | 已支付类订单必须存在支付成功记录；已取消订单不应存在未退款支付成功记录。 | 条件存在性与反存在性。 |
| R022 | `RELATION_EXISTS` | 已支付订单明细必须存在匹配 `订单ID + 商品ID + 数量` 的出库记录。 | 复合 key 与条件存在性。 |
| R023 | `RELATION_EXISTS` | `t_payment.订单ID exists in t_order.订单ID` | 反向引用存在性。 |
| R024 | `JOIN_ASSERT` | join 订单ID 后 `t_payment.用户ID == t_order.用户ID` | 跨表主体一致。 |
| R025 | `ROW_ASSERT` | 待支付无支付时间；已支付类订单 `支付时间 >= 下单时间`。 | 状态驱动的时间逻辑。 |
| R026 | `JOIN_ASSERT` / `ANTI_EXISTS` | 已取消订单不应存在 `退款金额=0 && 支付状态=支付成功` 的支付记录。 | 条件 join 后反向违规记录。 |
| R027 | `FIELD_CHECK.NON_NEGATIVE` | `table=t_inventory_log`, `fields=[变动后库存]` | 结果库存非负。 |
| R028 | `JOIN_ASSERT` / `ANTI_EXISTS` | join 商品ID 后 `上架状态=已下架 && 变动类型=出库` 不应存在。 | 跨表条件禁用组合。 |
| R029 | `DUPLICATE_ASSERT` | `where 支付状态 == 支付成功`, `groupBy=[订单ID]`, `count <= 1` | 条件分组次数约束。 |
| R030 | `AGGREGATE_ASSERT` | 按日期分别汇总订单金额和明细小计金额后比较。 | 指标层多表聚合一致。 |

阶段性落地状态：

1. `ROW_EXPRESSION` 已作为 `ROW_ASSERT` 的当前执行形态，支持 `when`、条件分支和字段间计算。
2. `RELATION_EXISTS` 已作为关系存在 DSL 的当前执行形态，支持单 key、复合 key、`sourceWhere`、`targetWhere`、`sourceExists` 前置关联过滤，以及 `expectExists=false` 的反向不存在校验。
3. R021、R022、R026、R028 已可通过 `RELATION_EXISTS` 统一表达，不再依赖规则编号特例。
4. `AGGREGATE_ASSERT` 已作为聚合断言 DSL 的当前执行形态，支持分组汇总与目标字段或另一组汇总结果比较。
5. `JOIN_ASSERT` 已作为跨表 join 断言 DSL 的当前执行形态，支持 `keys`、`sourceWhere`、`targetWhere`、字段/表达式断言和数值容差。
6. `FIELD_EQUALS` 仍作为旧版跨表字段一致兼容模板保留。
7. `DUPLICATE_ASSERT` 已作为分组次数断言 DSL 的当前执行形态，支持过滤后按 `groupBy` 分组并比较 `count`。
8. `DUPLICATE_CHECK` 仍作为旧版重复组合检查兼容模板保留。

### 3.6 后续统一 DSL 方向

现有模板可以继续保留，但长期应向统一 DSL 收敛。推荐结果不直接依赖业务规则编号，而是输出以下几类结构化断言。

字段级约束：

```json
{
  "type": "FIELD_CHECK",
  "table": "t_order",
  "checks": [
    { "field": "订单金额", "op": ">=", "value": 0 },
    { "field": "实付金额", "op": ">=", "value": 0 }
  ]
}
```

单表行断言：

```json
{
  "type": "ROW_ASSERT",
  "table": "t_order",
  "where": {
    "left": { "field": "订单状态" },
    "op": "==",
    "right": { "value": "已支付" }
  },
  "assert": {
    "left": { "field": "订单金额" },
    "op": "!=",
    "right": { "value": 0 }
  }
}
```

条件分支行断言：

```json
{
  "type": "ROW_ASSERT",
  "table": "t_inventory_log",
  "assert": {
    "left": { "field": "变动后库存" },
    "op": "==",
    "right": {
      "op": "+",
      "left": { "field": "变动前库存" },
      "right": {
        "if": {
          "left": { "field": "变动类型" },
          "op": "==",
          "right": { "value": "入库" }
        },
        "then": { "field": "变动数量" },
        "else": {
          "op": "-",
          "left": { "value": 0 },
          "right": { "field": "变动数量" }
        }
      }
    }
  }
}
```

跨表关联断言：

```json
{
  "type": "JOIN_ASSERT",
  "source": "t_payment",
  "target": "t_order",
  "join": [
    { "sourceField": "订单ID", "targetField": "订单ID" }
  ],
  "assert": {
    "left": { "sourceField": "用户ID" },
    "op": "==",
    "right": { "targetField": "用户ID" }
  }
}
```

关系存在断言：

```json
{
  "type": "RELATION_EXISTS",
  "source": "t_order_item",
  "target": "t_inventory_log",
  "keys": [
    { "sourceField": "订单ID", "targetField": "关联订单ID" },
    { "sourceField": "商品ID", "targetField": "商品ID" },
    { "sourceField": "数量", "targetField": "变动数量" }
  ],
  "sourceExists": {
    "target": "t_order",
    "keys": [
      { "sourceField": "订单ID", "targetField": "订单ID" }
    ],
    "targetWhere": {
      "left": { "field": "订单状态" },
      "operator": "in",
      "right": ["已支付", "已发货", "已完成"]
    }
  },
  "targetWhere": {
    "left": { "field": "变动类型" },
    "operator": "==",
    "right": { "value": "出库" }
  },
  "expectExists": true
}
```

聚合断言：

```json
{
  "type": "AGGREGATE_ASSERT",
  "source": "t_order_item",
  "target": "t_order",
  "groupBy": [
    { "sourceField": "订单ID", "targetField": "订单ID" }
  ],
  "aggregate": { "fn": "SUM", "field": "小计金额" },
  "assert": {
    "op": "==",
    "targetField": "订单金额",
    "tolerance": 0.01
  }
}
```

重复次数断言：

```json
{
  "type": "DUPLICATE_ASSERT",
  "table": "t_payment",
  "where": {
    "left": { "field": "支付状态" },
    "op": "==",
    "right": { "value": "支付成功" }
  },
  "groupBy": ["订单ID"],
  "assert": { "count": "<= 1" }
}
```

当前统一 DSL 落地分为三个层次：

1. 已一等化：`RELATION_EXISTS`、`JOIN_ASSERT`、`AGGREGATE_ASSERT`、`DUPLICATE_ASSERT`。
2. 当前执行形态：`ROW_EXPRESSION` 承担 `ROW_ASSERT`。
3. 兼容入口：`FIELD_EQUALS`、`AGGREGATION_EQUALS`、`DUPLICATE_CHECK`。

后续不应删除旧模板，而应先让推荐器和默认绑定优先输出一等 DSL；旧模板继续作为兼容入口，待规则资产和测试覆盖稳定后再逐步降级为内部兼容适配。

## 4. AI 推荐范围扩展

### 4.1 模板白名单

AI 推荐白名单扩展为全部已可执行模板：

```text
NOT_NULL
NON_NEGATIVE
NUMERIC_TYPE
FIELD_EXPRESSION
ROW_EXPRESSION
EXISTS_IN_TABLE
RELATION_EXISTS
FIELD_EQUALS
JOIN_ASSERT
AGGREGATION_EQUALS
AGGREGATE_ASSERT
DUPLICATE_ASSERT
DUPLICATE_CHECK
```

模型 prompt 需要明确每类模板的参数结构：

1. 字段级模板：`tableName`, `fields`。
2. `FIELD_EXPRESSION`：`tableName`, `expression`。
3. `ROW_EXPRESSION`：`tableName`, `conditions`，条件中包含 `left`、`operator`、`right`。
4. `EXISTS_IN_TABLE`：`source`, `target`, `key`。
5. `RELATION_EXISTS`：`source`, `target`, `keys`, `expectExists`，可选 `sourceWhere`, `targetWhere`, `sourceExists`。
6. `FIELD_EQUALS`：`source`, `target`, `key`, `sourceField`, `targetField`。
7. `JOIN_ASSERT`：`source`, `target`, `keys`, `assert`，可选 `sourceWhere`, `targetWhere`。
8. `AGGREGATION_EQUALS`：`source`, `target`, `groupBy`, `sum`, `targetField`, 可选 `targetKey`。
9. `AGGREGATE_ASSERT`：`source`, `target`, `groupBy`, `aggregate`, `assert`；`assert` 支持 `targetField` 或目标侧 `aggregate`。
10. `DUPLICATE_ASSERT`：`table`, `groupBy`, `assert`，可选 `where`；`assert` 支持 `{op,count}` 或 `{count:"<= 1"}`。
11. `DUPLICATE_CHECK`：`tableName`, `groupBy`。

### 4.2 AI 与本地映射优先级

推荐优先级如下：

1. 先生成本地语义映射推荐。
2. AI 未开启或无响应时，直接返回本地推荐。
3. AI 有响应时，解析并执行统一校验。
4. AI 推荐校验通过，返回 AI 推荐。
5. AI 推荐校验失败，返回本地推荐，并追加 warning。
6. 本地语义映射也无法生成可应用模板时，返回不可应用推荐说明，提示继续使用内置执行器或补充模板能力。

## 5. 参数安全校验

模板推荐接口和规则绑定接口应尽量复用同一套校验口径，避免“推荐可展示但应用失败”或“应用成功但执行无效”。

### 5.1 通用校验

所有模板都必须校验：

1. `templateCode` 在已执行模板白名单内。
2. 所有表名存在于当前数据集快照。
3. 所有字段名存在于对应表头。
4. 参数结构满足模板要求。
5. 推荐结果不包含当前数据集以外的表或字段。

### 5.2 模板专项校验

1. `FIELD_EXPRESSION`
   - 表名存在。
   - 表达式非空。
   - 表达式引用字段存在。
   - 表达式能被当前表达式执行器解析。

2. `EXISTS_IN_TABLE`
   - `source`、`target` 均存在。
   - `key` 同时存在于 source 和 target。

3. `FIELD_EQUALS`
   - `source`、`target` 均存在。
   - `key` 同时存在于 source 和 target。
   - `sourceField` 存在于 source。
   - `targetField` 存在于 target。

4. `JOIN_ASSERT`
   - `source`、`target` 均存在。
   - `keys` 或 `join` 至少包含一组 `{sourceField,targetField}`，字段必须分别存在于 source 和 target。
   - `assert.left`、`assert.right` 支持 `sourceField`、`targetField`、字面量或 `+`、`-`、`*`、`/` 二元表达式。
   - `assert.op` 支持 `==`、`!=`、`>`、`>=`、`<`、`<=`。
   - 可选 `tolerance` 必须是数值。
   - 可选 `sourceWhere`、`targetWhere` 使用行表达式谓词校验字段合法性。

5. `AGGREGATION_EQUALS`
   - `source`、`target` 均存在。
   - `groupBy` 存在于 source。
   - `targetKey` 为空时默认等于 `groupBy`，并必须存在于 target。
   - `sum` 存在于 source。
   - `targetField` 存在于 target。

6. `DUPLICATE_CHECK`
   - `tableName` 存在。
   - `groupBy` 至少包含一个字段。
   - `groupBy` 中所有字段都存在于目标表。

7. `DUPLICATE_ASSERT`
   - `table` 存在。
   - `groupBy` 至少包含一个字段。
   - `groupBy` 中所有字段都存在于目标表。
   - `assert.op` 支持 `==`、`!=`、`>`、`>=`、`<`、`<=`。
   - `assert.count` 必须是数值，也可写成 `"<op> <count>"` 字符串。
   - 可选 `where` 使用行表达式谓词校验字段合法性。

8. `AGGREGATE_ASSERT`
   - `source`、`target` 均存在。
   - `groupBy` 支持单字段或 `{sourceField,targetField}` 数组，字段必须分别存在于 source 和 target。
   - `aggregate.fn` 当前支持 `SUM`、`COUNT`；`SUM` 必须提供存在于 source 的 `field`。
   - `assert.op` 支持 `==`、`!=`、`>`、`>=`、`<`、`<=`。
   - `assert.targetField` 或 `assert.aggregate` 必须提供一个；目标字段或目标聚合字段必须存在于 target。
   - 可选 `sourceWhere`、`targetWhere` 使用行表达式谓词校验字段合法性。

9. `ROW_EXPRESSION`
   - `tableName` 存在。
   - `conditions` 至少包含一个条件。
   - `operator` 只能使用 `==`、`!=`、`>`、`>=`、`<`、`<=`。
   - 表达式节点只能是字段、字面量或 `+`、`-`、`*`、`/` 二元运算。
   - 所有字段必须存在于目标表。
   - 字段关系只做通用 AST 校验，不在后端写业务字段特例。

## 6. 异常详情展示优化

保持 API 字段结构不变，仅优化模板执行器生成的 `actualValue`、`expectedValue` 和 `description`。

| 模板 | actualValue 建议 | expectedValue 建议 | description 建议 |
|---|---|---|---|
| `FIELD_EXPRESSION` | `左侧字段=实际值` | `右侧表达式=计算值` | `表达式条件不成立` |
| `ROW_EXPRESSION` | `左侧表达式=计算值` | `右侧表达式=计算值` | `行表达式条件不成立` |
| `AGGREGATION_EQUALS` | `目标字段=实际值` | `来源汇总=计算值` | `聚合结果不一致` |
| `AGGREGATE_ASSERT` | `目标字段或目标汇总=实际值` | `来源汇总=计算值` | `聚合结果不一致` |
| `JOIN_ASSERT` | `source 表达式=实际值` | `target 表达式=期望值` | `关联断言不成立` |
| `FIELD_EQUALS` | `source.sourceField=实际值` | `target.targetField=期望值` | `关联字段值不一致` |
| `EXISTS_IN_TABLE` | `source.key=实际值` | `target.key 中存在对应记录` | `关联记录不存在` |
| `DUPLICATE_ASSERT` | `分组字段=分组值；count=实际次数` | `count op 期望次数` | `分组次数断言不成立` |
| `DUPLICATE_CHECK` | `字段组合=组合值` | `唯一组合` | `存在重复记录` |

优化目标是让异常详情页直接展示业务判断过程，而不是只展示单个字段值。

## 7. 前端展示增强

前端继续复用现有推荐和绑定接口，不新增外部 API。

后续增强点：

1. 推荐预览拆分展示模板参数，而不是只展示单行摘要。
2. 展示当前绑定与推荐绑定对比：
   - 当前执行方式。
   - 当前模板编码。
   - 当前模板参数。
   - 推荐模板编码。
   - 推荐模板参数。
3. warning 使用醒目但不阻断的样式展示。
4. “应用推荐”仍只在用户点击后调用绑定更新接口。
5. 应用成功后刷新规则列表并清除该规则的推荐预览。

## 8. 开发计划

### 8.0 业务数据源数据库化

状态：已完成演示库阶段，后续进入生产化拆分阶段。

1. 已新增 Flyway seed 脚本，创建并初始化赛题业务表。
2. 已调整 Excel 导入：不再要求业务 sheet，不再写业务行 JSON 快照。
3. 已新增 `BusinessTableDataProvider`，从数据库业务表读取并转换为 `DataTable`。
4. 已让 `ValidationService` 通过 provider 获取业务表，规则定义和规则绑定仍从系统表读取。
5. 已让新数据集的 `data_table_snapshot` 保存业务表元数据和行数，供推荐校验和前端展示使用。
6. 后续生产化时拆分系统库 DataSource 与业务库只读 DataSource，并增加业务库连接配置和只读权限校验。

### 8.1 语义映射层

状态：主干已完成，`JOIN_ASSERT` 与 `DUPLICATE_ASSERT` 映射已补齐，后续转向默认绑定迁移。

1. 已新增本地规则语义映射器，集中处理规则文本到模板候选的推断。
2. 已将 R006 金额关系迁移到 `ROW_EXPRESSION` 结构化映射。
3. 已让映射结果返回模板编码、参数、置信度、匹配原因和 warning。
4. 已让本地映射器作为 AI 不可用或 AI 校验失败时的兜底。
5. 已将 R019、R024 等跨表字段一致规则从 `FIELD_EQUALS` 迁移到 `JOIN_ASSERT` 推荐。
6. 已将 R029 从 `DUPLICATE_CHECK` 迁移到 `DUPLICATE_ASSERT` 推荐。
7. 将 R030 纳入 30 条规则语义映射回归，确认指标级聚合规则能落到 `AGGREGATE_ASSERT`。

### 8.2 AI 推荐范围扩展

状态：已完成当前可执行模板范围，后续随一等 DSL 新增继续扩展。

1. 已扩展 AI 推荐模板白名单到全部当前可执行模板。
2. 已更新 AI 推荐 prompt，明确每类模板参数结构。
3. 已让 AI 返回结果统一转换为候选推荐，再进入模板校验。
4. 已支持 AI 返回不支持模板、缺字段、缺表、参数不可执行时降级本地推荐。
5. 已为 `JOIN_ASSERT` 同步扩展 AI 白名单、prompt、解析校验和降级测试。
6. 已为 `DUPLICATE_ASSERT` 同步扩展 AI 白名单、prompt、解析校验和降级测试。

### 8.3 参数可执行性校验

状态：已完成共享校验主干，`DUPLICATE_ASSERT` 已纳入可执行参数校验。

1. 已抽象模板参数校验能力，供推荐接口和绑定接口复用。
2. 已保留绑定接口的强校验，确保不可执行参数不能保存。
3. 已让推荐接口使用同一校验逻辑判断 AI 候选是否可应用。
4. 失败 warning 已尽量说明具体原因，例如“字段不存在: 商品ID”或“表达式格式不支持”。
5. 已补充 `JOIN_ASSERT` 的 join key、source/target 过滤、断言表达式字段归属校验。
6. 已补充 `DUPLICATE_ASSERT` 的 `count` 比较、分组字段、过滤条件和阈值合法性校验。

### 8.4 异常详情展示优化

状态：已完成现有模板主干，`JOIN_ASSERT` 和 `DUPLICATE_ASSERT` 已按统一 DSL 口径补齐异常详情。

1. 已为跨表、聚合和重复检查模板补充更清晰的异常详情。
2. 已保持前端字段名不变，避免 API 兼容性影响。
3. 已为现有模板补充对应单测，锁定展示口径。
4. 已补充 `JOIN_ASSERT` 的 join key、左右表达式、关联记录缺失等异常详情。
5. 已补充 `DUPLICATE_ASSERT` 的分组 key、实际次数、期望次数比较等异常详情。

### 8.5 前端预览增强

状态：已完成基础对比展示，后续优化复杂参数可读性。

1. 已按模板参数结构展示推荐结果。
2. 已展示当前绑定和推荐绑定对比。
3. 已保持 warning 和来源信息可见。
4. 已保持应用推荐流程人工确认，不自动保存。
5. 下一阶段可对 `conditions`、`keys`、`aggregate`、`assert` 等复杂参数做结构化折叠展示，避免只显示 JSON 字符串。

### 8.6 统一 DSL 二阶段一等化

状态：进行中。

1. 已完成 `AGGREGATE_ASSERT` 一等执行形态，支持来源聚合与目标字段或目标聚合比较。
2. 已完成 `JOIN_ASSERT` 一等模板：
   - 参数：`source`、`target`、`keys`、可选 `sourceWhere`、`targetWhere`、`assert`。
   - 能力：join 后比较左右字段或表达式，支持容差。
   - 迁移：R019、R024 已优先从 `FIELD_EQUALS` 迁移；R026、R028 继续由 `RELATION_EXISTS` 承担反存在语义，后续可评估是否补充 `JOIN_ASSERT expect=false`。
3. 已完成 `DUPLICATE_ASSERT` 一等模板：
   - 参数：`table`、`groupBy`、可选 `where`、`assert`。
   - 能力：支持 `count == N`、`count <= N`、`count >= N`、唯一组合和至少一次等分组次数断言。
   - 迁移：R029 已从 `DUPLICATE_CHECK` 迁移到 `DUPLICATE_ASSERT` 推荐。
4. 推荐器已优先输出一等 DSL，旧模板继续保留为兼容执行入口。

### 8.7 默认绑定迁移

状态：已完成。

1. 已将 R017、R020、R030 的默认模板绑定从 `AGGREGATION_EQUALS` 迁移到 `AGGREGATE_ASSERT`。
2. 已为 R017、R020、R030 补充默认 `aggregate`、`groupBy`、`assert` 参数；其中 R020 默认带 `sourceWhere 支付状态 == 支付成功`，与内置规则口径一致。
3. 已将 R019、R024 的默认模板绑定从 `FIELD_EQUALS` 迁移到 `JOIN_ASSERT`。
4. 已将 R029 的默认模板绑定从 `DUPLICATE_CHECK` 迁移到 `DUPLICATE_ASSERT`。
5. 保留旧模板执行器，确保历史数据集和已有绑定不受影响。

## 9. 测试计划

### 9.1 后端单测

1. 语义映射器覆盖字段非空、非负、数值类型、行表达式、跨表存在、跨表字段一致、聚合断言、重复检查。
2. R006 通过通用金额关系映射生成完整 `ROW_EXPRESSION`。
3. R015 通过通用条件分支行表达式生成完整 `ROW_EXPRESSION`。
4. 补齐 30 条规则映射回归测试，确认每条规则都能落到 `FIELD_CHECK`、`ROW_ASSERT`、`RELATION_EXISTS`、`JOIN_ASSERT`、`AGGREGATE_ASSERT` 或 `DUPLICATE_ASSERT`；其中 R030 必须覆盖指标级跨表聚合比较。
5. H2 seed 表能通过 `BusinessTableDataProvider` 读取为 `DataTable`，主键、表头、行数和字段值与业务表一致。
6. Excel 缺少业务 sheet 时仍可导入规则资产，且新导入数据集不写业务行 JSON 快照。
7. AI 返回合法 `ROW_EXPRESSION`、`EXISTS_IN_TABLE`、`FIELD_EQUALS`、`JOIN_ASSERT`、`AGGREGATION_EQUALS`、`AGGREGATE_ASSERT`、`DUPLICATE_ASSERT`、`DUPLICATE_CHECK` 推荐时，返回 `generatedByAi=true`。
8. AI 返回不存在表名、字段名、key、非法 AST 或不可执行表达式时，降级本地推荐并带 warning。
9. 绑定接口拒绝不可执行模板参数，并接受合法 `ROW_EXPRESSION`、`RELATION_EXISTS`、`JOIN_ASSERT`、`AGGREGATE_ASSERT`、`DUPLICATE_ASSERT`。
10. 各模板异常详情展示符合新口径。
11. `FIELD_EXPRESSION` 和 `ROW_EXPRESSION` 多条件只返回第一条失败条件。
12. 已新增导入回归测试，确认 R017、R020、R030 默认使用 `AGGREGATE_ASSERT`，R019、R024 默认使用 `JOIN_ASSERT`，R029 默认使用 `DUPLICATE_ASSERT`，且默认参数可通过共享模板校验。

### 9.2 前端验证

1. `npm run build`。
2. 推荐预览可以展示模板编码、来源、参数、说明和 warning。
3. 应用推荐后规则列表刷新。
4. 当前绑定与推荐绑定对比展示不影响原有校验、异常详情和报告功能。

### 9.3 后端验证

1. `mvn test`。
2. `git diff --check`。

## 10. 约束与假设

1. 不新增外部 API，继续复用 `POST /api/ai/rule-binding/recommend` 和 `PUT /api/rules/{datasetId}/{ruleId}/binding`。
2. 本阶段只新增通用 `ROW_EXPRESSION`，不新增面向单个业务含义的专用模板。
3. AI 推荐只作为候选建议，不能自动覆盖规则绑定。
4. 后续新增内部语义映射类型不影响前端响应结构。
5. 字段元数据继续使用当前数据集快照中的表头信息。
6. 规则语义映射第一阶段采用规则化策略和结构化 `ROW_EXPRESSION` AST，不引入用户可编辑的完整规则语言。
7. 推荐能力优先服务赛题5演示规则，但实现边界应保持通用，不再围绕单个规则编号堆叠特例。
8. 业务 seed 表按业务字段类型建表；读取为 `DataTable` 时统一归一化为字符串视图，执行器再按需解析数值、时间和枚举。
