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
   - `EXISTS_IN_TABLE`：跨表存在性。
   - `FIELD_EQUALS`：跨表字段一致。
   - `AGGREGATION_EQUALS`：分组汇总一致。
   - `DUPLICATE_CHECK`：重复记录检查。
4. `FIELD_EXPRESSION` 已支持多条件表达式，并已将异常详情调整为“计算明细 + 失败条件”。
5. 绑定接口已对模板参数做表名、字段名和表达式可解析性校验。

当前主要不足：

1. AI 推荐范围仍偏保守，尚未把所有已可执行模板纳入可应用推荐。
2. R006 金额关系仍存在规则特例，长期看不利于扩展更多业务规则。
3. AI 推荐校验和绑定校验存在继续抽象统一的空间。
4. 跨表、聚合、重复类模板的异常详情还可以进一步贴近业务语义。
5. 前端推荐预览目前以参数摘要为主，缺少当前绑定与推荐绑定的对比。

## 2. 拓展目标

本阶段拓展目标是把“规则模板推荐”从特例驱动升级为通用语义映射驱动：

1. 建立本地规则语义映射层，集中处理规则文本到模板候选的推断。
2. 将 AI 可应用推荐范围扩展到所有已可执行模板。
3. 推荐结果必须通过模板白名单、字段存在性、参数结构和可执行性校验。
4. 保持 AI 推荐只作为候选建议，必须人工确认后才保存为规则绑定。
5. 不新增新的模板执行类型，优先把已有模板纳入通用推荐闭环。
6. 优化模板执行后的异常详情展示，让测试人员能直接理解“错在哪里”。

## 3. 总体设计

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

语义映射器根据规则名称、描述、伪 SQL、适用表和字段快照生成模板候选。第一阶段采用规则化映射，不引入复杂 DSL。

| 语义类型 | 推荐模板 | 识别信号 | 必要参数 |
|---|---|---|---|
| 字段非空 | `NOT_NULL` | 非空、不能为空、必填、not null | `tableName`, `fields` |
| 字段非负 | `NON_NEGATIVE` | 非负、不得小于 0、>= 0、金额/数量下限 | `tableName`, `fields` |
| 数值类型 | `NUMERIC_TYPE` | 数值、金额、数量、类型、numeric | `tableName`, `fields` |
| 单表字段表达式 | `FIELD_EXPRESSION` | 等于、不得大于、小于等于、计算关系、字段间算术 | `tableName`, `expression` |
| 跨表存在性 | `EXISTS_IN_TABLE` | 必须存在于、关联记录、主外键、商品/订单/支付引用 | `source`, `target`, `key` |
| 跨表字段一致 | `FIELD_EQUALS` | 应与、必须等于、两表字段一致 | `source`, `target`, `key`, `sourceField`, `targetField` |
| 汇总一致 | `AGGREGATION_EQUALS` | 小计之和、汇总、合计、sum、明细到主表 | `source`, `target`, `groupBy`, `sum`, `targetField` |
| 重复检查 | `DUPLICATE_CHECK` | 唯一、重复、不得重复、唯一组合 | `tableName`, `groupBy` |

R006 金额关系不再作为孤立硬编码规则处理。后续应通过“单表字段表达式”映射生成：

```text
实付金额 == 订单金额 - 优惠金额 && 实付金额 <= 订单金额
```

该表达式来自字段名和规则文本中的金额关系语义。若规则文本只表达其中一段关系，则只推荐可确认的条件，并将缺失条件放入 warning；对于 R006 这类已有明确业务规则的场景，可以通过规则文本或伪 SQL 中的完整条件识别出两段条件。

## 4. AI 推荐范围扩展

### 4.1 模板白名单

AI 推荐白名单扩展为全部已可执行模板：

```text
NOT_NULL
NON_NEGATIVE
NUMERIC_TYPE
FIELD_EXPRESSION
EXISTS_IN_TABLE
FIELD_EQUALS
AGGREGATION_EQUALS
DUPLICATE_CHECK
```

模型 prompt 需要明确每类模板的参数结构：

1. 字段级模板：`tableName`, `fields`。
2. `FIELD_EXPRESSION`：`tableName`, `expression`。
3. `EXISTS_IN_TABLE`：`source`, `target`, `key`。
4. `FIELD_EQUALS`：`source`, `target`, `key`, `sourceField`, `targetField`。
5. `AGGREGATION_EQUALS`：`source`, `target`, `groupBy`, `sum`, `targetField`, 可选 `targetKey`。
6. `DUPLICATE_CHECK`：`tableName`, `groupBy`。

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

4. `AGGREGATION_EQUALS`
   - `source`、`target` 均存在。
   - `groupBy` 存在于 source。
   - `targetKey` 为空时默认等于 `groupBy`，并必须存在于 target。
   - `sum` 存在于 source。
   - `targetField` 存在于 target。

5. `DUPLICATE_CHECK`
   - `tableName` 存在。
   - `groupBy` 至少包含一个字段。
   - `groupBy` 中所有字段都存在于目标表。

## 6. 异常详情展示优化

保持 API 字段结构不变，仅优化模板执行器生成的 `actualValue`、`expectedValue` 和 `description`。

| 模板 | actualValue 建议 | expectedValue 建议 | description 建议 |
|---|---|---|---|
| `FIELD_EXPRESSION` | `左侧字段=实际值；右侧表达式=计算值` | 失败条件文本 | `表达式条件不成立` |
| `AGGREGATION_EQUALS` | `目标字段=实际值；来源汇总=计算值` | `目标字段 == 来源表.sumField 汇总值` | `聚合结果不一致` |
| `FIELD_EQUALS` | `source.sourceField=实际值；target.targetField=期望值` | `sourceField == target.targetField` | `关联字段值不一致` |
| `EXISTS_IN_TABLE` | `source.key=实际值` | `target.key 中存在对应记录` | `关联记录不存在` |
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

### 8.1 语义映射层

1. 新增本地规则语义映射器，集中处理规则文本到模板候选的推断。
2. 将当前 R006 金额关系从 `AiAssistService` 特例迁移到语义映射规则。
3. 映射结果返回模板编码、参数、置信度、匹配原因和 warning。
4. 本地映射器作为 AI 不可用或 AI 校验失败时的兜底。

### 8.2 AI 推荐范围扩展

1. 扩展 AI 推荐模板白名单到全部已执行模板。
2. 更新 AI 推荐 prompt，明确每类模板参数结构。
3. AI 返回结果统一转换为候选推荐，再进入模板校验。
4. AI 返回不支持模板、缺字段、缺表、参数不可执行时降级本地推荐。

### 8.3 参数可执行性校验

1. 抽象模板参数校验能力，供推荐接口和绑定接口复用。
2. 保留绑定接口的强校验，确保不可执行参数不能保存。
3. 推荐接口使用同一校验逻辑判断 AI 候选是否可应用。
4. 失败 warning 尽量说明具体原因，例如“字段不存在: 商品ID”或“表达式格式不支持”。

### 8.4 异常详情展示优化

1. 为跨表、聚合和重复检查模板补充更清晰的异常详情。
2. 保持前端字段名不变，避免 API 兼容性影响。
3. 每类模板补充对应单测，锁定展示口径。

### 8.5 前端预览增强

1. 推荐结果按模板参数结构展示。
2. 当前绑定和推荐绑定并排或分组展示。
3. warning 和来源信息保持可见。
4. 应用推荐流程保持人工确认，不自动保存。

## 9. 测试计划

### 9.1 后端单测

1. 语义映射器覆盖字段非空、非负、数值类型、字段表达式、跨表存在、跨表字段一致、聚合一致、重复检查。
2. R006 通过通用金额关系映射生成完整 `FIELD_EXPRESSION`。
3. AI 返回合法 `EXISTS_IN_TABLE` 推荐时，返回 `generatedByAi=true`。
4. AI 返回合法 `FIELD_EQUALS` 推荐时，返回 `generatedByAi=true`。
5. AI 返回合法 `AGGREGATION_EQUALS` 推荐时，返回 `generatedByAi=true`。
6. AI 返回合法 `DUPLICATE_CHECK` 推荐时，返回 `generatedByAi=true`。
7. AI 返回不存在表名、字段名、key 或不可执行表达式时，降级本地推荐并带 warning。
8. 绑定接口拒绝不可执行模板参数。
9. 各模板异常详情展示符合新口径。

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
2. 不新增模板执行类型，先把已有可执行模板纳入推荐闭环。
3. AI 推荐只作为候选建议，不能自动覆盖规则绑定。
4. 后续新增内部语义映射类型不影响前端响应结构。
5. 字段元数据继续使用当前数据集快照中的表头信息。
6. 规则语义映射第一阶段采用规则化策略，不引入可配置 DSL。
7. 推荐能力优先服务赛题5演示规则，但实现边界应保持通用，不再围绕单个规则编号堆叠特例。
