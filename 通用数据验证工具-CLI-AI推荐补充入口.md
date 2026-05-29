# 通用数据验证工具-CLI-AI推荐补充入口

本文档说明 `recommend` 的使用边界。当前阶段的原则是：规则片段库优先，AI 或本地语义推荐作为补充入口，只生成可审阅候选，不自动接入门禁，也不自动覆盖正式规则包。

## 推荐使用顺序

1. 先查 `通用数据验证工具-CLI-规则片段库.md`，能用片段表达的规则，优先复制片段并替换表名、字段名和 `ruleId`。
2. 复制后先运行 `lint --rules ... --metadata ...`，让 lint 指出字段、表名或模板结构问题。
3. 片段库无法覆盖时，再运行 `recommend --rules ... --metadata ... --candidate-rules ...` 生成候选。
4. 人工审阅推荐 JSON 的 `diff`、`confidence`、`warningCategories`、`warningDetails` 和 `candidateGenerated`。
5. 只有确认候选符合业务语义后，才把候选规则复制或合并到正式 `rules.yml`。

## CLI 入口

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="recommend --rules ../examples/generic-validation/rules.yml --metadata ../examples/generic-validation/source.yml --output ../examples/generic-validation/reports/recommendations.json --candidate-rules ../examples/generic-validation/reports/rules.recommended.yml"
```

调试 AI 输入输出：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="recommend --rules ../examples/generic-validation/rules.yml --metadata ../examples/generic-validation/source.yml --output ../examples/generic-validation/reports/recommendations.json --debug-ai ../examples/generic-validation/reports/ai-debug"
```

## 输出解释

推荐 JSON 的关键字段：

1. `templateCode` 和 `templateParams`：推荐的模板和参数。
2. `confidence`：推荐置信度，`LOW` 不会生成可执行候选规则。
3. `source` 和 `generatedByAi`：区分本地语义推荐和 AI 推荐。
4. `requiresHumanReview`：始终提醒需要人工确认。
5. `warningCategories` 和 `warningDetails`：说明字段缺失、模板不支持、AI 降级、安全拒绝等风险。
6. `candidateGenerated`：是否写入 `--candidate-rules` 指定的候选规则包。
7. `diff`：原始规则、原模板、推荐模板、推荐参数和推荐原因，供人工审阅。

## 候选生成门禁

以下情况只输出推荐 JSON，不写入可执行候选规则：

1. 低置信度推荐。
2. AI 推荐字段不存在或元数据缺失。
3. AI 推荐模板不在白名单中，或弱化本地高置信语义映射。
4. AI 推荐触发只读、安全或危险操作拒绝。
5. 推荐结果缺少 `templateCode` 或 `templateParams`。

即使发生上述情况，工具仍会尽量返回本地语义推荐，方便人工判断下一步应该补字段、改规则描述，还是继续手写规则。

## 不做事项

1. 不自动覆盖正式 `rules.yml`。
2. 不自动把候选规则接入质量门禁。
3. 不把 AI 输出直接视为业务事实。
4. 不默认连接生产数据库做推荐。
