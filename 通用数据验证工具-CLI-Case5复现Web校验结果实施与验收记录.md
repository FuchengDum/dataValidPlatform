# 通用数据验证工具-CLI-Case5复现Web校验结果实施与验收记录

本文是 `通用数据验证工具-CLI-后续演进计划.md` 中“阶段 14：CLI 使用 V3 业务表复现 Web 校验结果”的专项实施记录。后续路线、阶段状态、AI 配置和通用规则扩展方式以主演进计划为准；本文只保留 Case5/V3 复现链路的实现细节、关键决策和验收依据。

## Summary

目标是让 `bin/data-validator run --config examples/case5-seed/validator.yml` 读取 V3 seed 初始化的 5 张业务表，并用 YAML 模板规则执行 R001-R030。Web Excel 导入场景保留内置模板绑定，不读取 YAML 规则配置；CLI 继续保留 YAML 化规则执行方式。

## Implementation Status

- 已新增 Case5 CLI 样例目录 `examples/case5-seed/`，包含 `validator.yml`、`source.yml`、`rules.yml` 和 `expected-result.md`。
- 已新增 Case5 YAML 规则资源 `backend/src/main/resources/case5/rules.yml`，并通过测试保证它与 CLI 样例规则内容一致。
- 已扩展模板执行能力，支持 `isInteger`、`NUMERIC_TYPE.allowBlank: false`、中文严重等级和中文规则分类。
- 已调整 Web Excel 导入默认绑定：优先使用 `ExcelImportService` 内置模板参数，保存为 `executorType=TEMPLATE`；不从 Case5 YAML 读取模板参数。
- 已保留旧内置 R001-R030 执行分支作为兜底，避免历史数据绑定直接失效。

## Key Decisions

- 分路径验收口径：CLI 使用 Case5 YAML 执行 V3 seed 规则包，Web Excel 导入使用内置模板绑定。两条路径分别验证规则来源和执行结果，不再要求异常集合完全一致。
- 规则真值：以用户提供的 30 条规则 SQL/伪代码为准；当规则描述和 SQL/伪代码冲突时，SQL/伪代码优先。
- Web 行为：Web 默认绑定优先走代码内置模板参数，不读取 YAML；无法表达为模板的规则继续走内置 R001-R030 兜底。
- CLI 入口：不新增 `case5` 命令，只使用 `run --config`。
- YAML 真源：`examples/case5-seed/rules.yml` 给 CLI 演示使用，`backend/src/main/resources/case5/rules.yml` 保留为打包资源与一致性样例，测试保证两份内容一致；Web Excel 导入不读取该 YAML。

## Implementation Changes

- 新增 Case5 CLI 样例：
  - `examples/case5-seed/validator.yml`
  - `examples/case5-seed/source.yml`
  - `examples/case5-seed/rules.yml`
  - `examples/case5-seed/expected-result.md`
- `source.yml` 使用 JDBC + H2 `INIT=RUNSCRIPT FROM 'classpath:db/migration/V3__seed_case5_business_tables.sql'` 装载 V3 seed。
- 允许 `source.yml` 使用只读 SQL 派生规则字段：
  - `t_order` 派生 `日期`
  - `t_order_item` 通过关联 `t_order` 派生 `日期`、`订单状态`、`订单存在`
  - `t_payment` 通过关联 `t_order` 派生 `订单存在`
- 扩展通用模板能力：
  - `RowExpressionEvaluator` 支持 `isInteger`
  - `TemplateBindingValidator` 支持校验 `isInteger`
  - `NUMERIC_TYPE` 支持 `allowBlank: false`，Case5 中空值按异常处理
- 通用规则包支持中文别名：
  - 严重等级支持 `严重` / `警告`
  - 规则分类支持 `单表校验-字段约束`、`单表校验-业务规则`、`多表关联核对`、`指标一致性校验`
- Web 导入默认绑定：
  - `ExcelImportService` 使用 `templateFor(...)` 和 `defaultTemplateParams(...)` 生成内置模板绑定
  - 可表达为模板的规则默认保存为 `executorType=TEMPLATE`
  - Web Excel 导入不读取 `case5/rules.yml`
  - 保留 `ValidationService` 内置分支作为历史兜底

## CLI 复现方式

```bash
bin/data-validator lint --config examples/case5-seed/validator.yml
bin/data-validator run --config examples/case5-seed/validator.yml --json --no-report
```

预期结果：

- `lint` 返回 `0`。
- `run` 执行 30 条规则。
- 数据摘要包含 5 张表、70 行数据。
- 当前 V3 seed 产生 68 条异常，其中 60 条严重、8 条警告。
- 因为存在严重异常，`run` 返回退出码 `2`；这表示发现质量问题，不表示工具执行失败。

## 新增规则说明

后续新增业务场景或新规则时，统一参考 `通用数据验证工具-CLI-后续演进计划.md` 中阶段 14 的“新增规则免改代码方式”。本专项不再维护独立路线，避免与主演进计划出现口径分叉。

当前免改代码能力适用于已有模板能表达的规则，包括字段非空、非负、数值类型、行表达式、关联存在性、关联断言、聚合核对和重复校验。如果新业务规则需要全新的执行语义，应先评估是否能拆成已有模板；确实不能表达时，需要新增通用模板能力，而不是把 Case5 规则写死进代码。

## AI 推荐说明

AI 模型只用于 CLI `recommend` 生成候选规则模板，`lint` 和 `run` 不依赖 AI。Case5 的 R001-R030 正式规则仍以 `examples/case5-seed/rules.yml` 为准，AI 生成结果只作为人工审阅候选，不会自动覆盖正式 `rules.yml`。

AI API Key、`LOCAL_AI_*` 环境变量和 `--debug-ai` prompt/response 落盘说明统一维护在 `通用数据验证工具-CLI-后续演进计划.md` 的 AI 推荐章节。

## Test Plan

- 单元测试：
  - `isInteger` 覆盖整数、小数、空值、非数值
  - `allowBlank: false` 覆盖数值空值报错
  - 中文严重等级和中文规则分类可被解析
- CLI 集成测试：
  - `lint --config examples/case5-seed/validator.yml` 成功
  - `run --config examples/case5-seed/validator.yml --json --no-report` 执行 30 条规则
  - R030 使用派生 `日期` 字段完成按日期聚合校验
- Web/CLI 分路径测试：
  - Web 上传 Excel 后使用内置模板绑定
  - CLI 执行 Case5 YAML 规则包
  - 两条路径保留各自规则来源，避免 Web 导入依赖 YAML 文件
- 资源一致性测试：
  - `backend/src/main/resources/case5/rules.yml` 与 `examples/case5-seed/rules.yml` 内容一致
- 验收命令：
  - `cd backend`
  - `mvn test`
  - `cd ..`
  - `bin/data-validator lint --config examples/case5-seed/validator.yml`
  - `bin/data-validator run --config examples/case5-seed/validator.yml --json --no-report`

## Assumptions

- R001-R030 规则包人工固化，不依赖运行时 AI 生成。
- AI `recommend` 只作为后续新增规则的候选生成工具，不能自动覆盖正式 `rules.yml`。
- 不修改 `V3__seed_case5_business_tables.sql` 表结构。
- 旧 Web 内置规则与 30 条规则文本存在差异时，本阶段以 30 条规则文本为准。
- 本文不作为后续阶段路线来源，后续新增能力统一维护在 `通用数据验证工具-CLI-后续演进计划.md`。
