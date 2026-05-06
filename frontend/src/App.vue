<template>
  <main class="page-shell">
    <header class="topbar">
      <div>
        <h1>业务数据准确性验证工具</h1>
        <p>赛题5 · Excel 导入 · 规则校验 · 异常证据链 · 报告导出</p>
      </div>
      <div class="actions">
        <label class="file-button">
          <input type="file" accept=".xlsx" @change="onFileChange" />
          选择 Excel
        </label>
        <button :disabled="!dataset || loading" @click="validate">开始校验</button>
        <button :disabled="!job || loading" @click="exportReport">导出报告</button>
      </div>
    </header>

    <section class="status-line" v-if="message || error">
      <span class="message" v-if="message">{{ message }}</span>
      <span class="error" v-if="error">{{ error }}</span>
    </section>

    <section class="summary-grid">
      <article class="metric">
        <span>业务表</span>
        <strong>{{ dataset?.businessTableCount ?? '-' }}</strong>
      </article>
      <article class="metric">
        <span>规则数</span>
        <strong>{{ dataset?.ruleCount ?? '-' }}</strong>
      </article>
      <article class="metric">
        <span>异常总数</span>
        <strong>{{ summary?.findingCount ?? '-' }}</strong>
      </article>
      <article class="metric danger">
        <span>严重</span>
        <strong>{{ summary?.criticalCount ?? '-' }}</strong>
      </article>
      <article class="metric warn">
        <span>警告</span>
        <strong>{{ summary?.warningCount ?? '-' }}</strong>
      </article>
      <article class="metric">
        <span>耗时 ms</span>
        <strong>{{ summary?.durationMillis ?? '-' }}</strong>
      </article>
    </section>

    <section class="template-workbench">
      <div class="section-head">
        <div>
          <h2>规则模板化工作台</h2>
          <p>模板覆盖 {{ templateCoverageStats.templateBound }} / {{ templateCoverageStats.total }} · 已推荐 {{ recommendationStats.total }} · 模型 {{ recommendationStats.ai }} · 本地 {{ recommendationStats.local }}</p>
        </div>
        <div class="section-actions">
          <button
            :disabled="!selectedRule || recommendationLoading || loading"
            @click="loadRuleRecommendation(selectedRule)">
            推荐当前规则
          </button>
          <button
            class="secondary-button"
            :disabled="filteredRules.length === 0 || recommendationLoading || loading"
            @click="loadVisibleRuleRecommendations">
            批量推荐可见规则
          </button>
        </div>
      </div>

      <div class="rule-filter-bar">
        <label>
          规则检索
          <input v-model.trim="ruleFilters.keyword" placeholder="R006 / 金额 / 商品" />
        </label>
        <label>
          执行形态
          <select v-model="ruleFilters.executorType">
            <option value="">全部</option>
            <option value="TEMPLATE">模板</option>
            <option value="BUILTIN">内置</option>
          </select>
        </label>
        <label>
          推荐状态
          <select v-model="ruleFilters.recommendationStatus">
            <option value="">全部</option>
            <option value="recommended">已推荐</option>
            <option value="ai">模型生成</option>
            <option value="local">本地推荐</option>
            <option value="failed">推荐失败</option>
            <option value="notRecommended">未推荐</option>
          </select>
        </label>
        <div v-if="recommendationBatchProgress.total" class="batch-progress">
          {{ recommendationBatchProgress.done }} / {{ recommendationBatchProgress.total }}
        </div>
      </div>

      <div class="template-workbench-grid">
        <aside class="rule-selector-panel">
          <div class="panel-head compact">
            <h3>规则选择</h3>
            <span>{{ filteredRules.length }} 条</span>
          </div>
          <div class="rule-list prominent">
            <button
              v-for="rule in filteredRules"
              :key="rule.ruleId"
              :class="['rule-item selectable', selectedRule?.ruleId === rule.ruleId ? 'active' : '']"
              @click="selectRule(rule.ruleId)">
              <div class="rule-main">
                <strong>{{ rule.ruleId }}</strong>
                <span>{{ rule.ruleName }}</span>
              </div>
              <div class="rule-binding">
                <span :class="['tag', rule.executorType === 'TEMPLATE' ? 'template' : 'builtin']">
                  {{ labelExecutor(rule.executorType) }}
                </span>
                <span v-if="rule.templateCode" class="template-code">{{ rule.templateCode }}</span>
              </div>
              <span :class="['recommendation-state', recommendationClass(rule)]">
                {{ labelRecommendationStatus(rule) }}
              </span>
            </button>
            <p v-if="filteredRules.length === 0" class="empty">暂无匹配规则</p>
          </div>
        </aside>

        <section class="binding-panel">
          <div class="panel-head compact">
            <h3>{{ selectedRule ? `${selectedRule.ruleId} ${selectedRule.ruleName}` : '绑定对比' }}</h3>
            <div v-if="selectedRule" class="rule-actions">
              <button
                class="mini-button wide"
                :disabled="!selectedRule.templateCode || loading"
                @click="switchRuleBinding(selectedRule)">
                {{ selectedRule.executorType === 'TEMPLATE' ? '切回内置' : '切到模板' }}
              </button>
              <button
                class="mini-button wide"
                :disabled="!selectedRecommendation || loading"
                @click="applyRuleRecommendation(selectedRule)">
                应用推荐
              </button>
            </div>
          </div>
          <template v-if="selectedRule">
            <p v-if="recommendationErrors[selectedRule.ruleId]" class="assist-warning strong">
              {{ recommendationErrors[selectedRule.ruleId] }}
            </p>
            <div class="recommendation-compare featured">
              <div class="binding-preview">
                <strong>当前绑定</strong>
                <span>{{ labelExecutor(selectedRule.executorType) }} · {{ selectedRule.templateCode || '内置执行器' }}</span>
                <div
                  v-for="item in paramEntries(selectedRule.templateParams)"
                  :key="`current-${selectedRule.ruleId}-${item.key}`"
                  class="param-row">
                  <b>{{ item.key }}</b>
                  <span>{{ item.value }}</span>
                </div>
                <p v-if="paramEntries(selectedRule.templateParams).length === 0" class="param-empty">无模板参数</p>
              </div>
              <div class="binding-preview recommended">
                <strong>AI 推荐绑定</strong>
                <span v-if="selectedRecommendation">
                  TEMPLATE · {{ selectedRecommendation.templateCode }} · {{ selectedRecommendation.generatedByAi ? '模型生成' : '本地推荐' }}
                </span>
                <span v-else>等待推荐结果</span>
                <template v-if="selectedRecommendation">
                  <div
                    v-for="item in paramEntries(selectedRecommendation.templateParams)"
                    :key="`recommended-${selectedRule.ruleId}-${item.key}`"
                    class="param-row">
                    <b>{{ item.key }}</b>
                    <span>{{ item.value }}</span>
                  </div>
                </template>
                <p v-else class="param-empty">点击推荐当前规则生成模板参数</p>
              </div>
            </div>
            <section v-if="selectedRecommendation" class="recommendation-box prominent">
              <div class="rule-binding">
                <span class="tag template">{{ selectedRecommendation.templateCode }}</span>
                <span class="template-code">{{ selectedRecommendation.source }} · {{ selectedRecommendation.generatedByAi ? '模型生成' : '本地推荐' }}</span>
              </div>
              <p class="param-summary">{{ summarizeParams(selectedRecommendation.templateParams) }}</p>
              <p class="recommendation-text">{{ selectedRecommendation.explanation }}</p>
              <p
                v-for="warning in selectedRecommendation.warnings"
                :key="warning"
                class="assist-warning">
                {{ warning }}
              </p>
            </section>
          </template>
          <p v-else class="empty">上传 Excel 后选择规则</p>
        </section>

        <aside class="template-capability-panel">
          <div class="panel-head compact">
            <h3>模板能力矩阵</h3>
            <span>{{ templateCoverageStats.templateReady }} 个可模板化</span>
          </div>
          <div class="capability-list">
            <div v-for="item in templateCoverageStats.byTemplate" :key="item.code" class="capability-item">
              <div>
                <strong>{{ item.code }}</strong>
                <span>{{ item.count }} 条规则</span>
              </div>
              <div class="coverage-bar">
                <span :style="{ width: item.percent + '%' }"></span>
              </div>
            </div>
          </div>
          <dl class="compact-dl">
            <dt>模板执行</dt>
            <dd>{{ templateCoverageStats.templateBound }} 条</dd>
            <dt>内置执行</dt>
            <dd>{{ templateCoverageStats.builtinBound }} 条</dd>
            <dt>推荐失败</dt>
            <dd>{{ recommendationStats.failed }} 条</dd>
          </dl>
        </aside>
      </div>
    </section>

    <section class="workspace">
      <section class="table-panel">
        <div class="panel-head">
          <h2>异常疑点清单</h2>
          <span>{{ findings.length }} 条</span>
        </div>
        <div class="finding-filter-bar">
          <label>
            严重等级
            <select v-model="filters.severity" @change="loadFindings">
              <option value="">全部</option>
              <option value="CRITICAL">严重</option>
              <option value="WARNING">警告</option>
            </select>
          </label>
          <label>
            业务表
            <select v-model="filters.tableName" @change="loadFindings">
              <option value="">全部</option>
              <option value="t_order">t_order</option>
              <option value="t_order_item">t_order_item</option>
              <option value="t_product">t_product</option>
              <option value="t_payment">t_payment</option>
              <option value="t_inventory_log">t_inventory_log</option>
            </select>
          </label>
          <label>
            规则编号
            <input v-model.trim="filters.ruleId" placeholder="例如 R006" @keyup.enter="loadFindings" />
          </label>
          <button class="secondary-button" :disabled="!job" @click="loadFindings">刷新列表</button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>规则</th>
                <th>等级</th>
                <th>表</th>
                <th>主键</th>
                <th>描述</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in findings" :key="item.findingId" @click="selectFinding(item.findingId)">
                <td>{{ item.ruleId }}</td>
                <td><span :class="['tag', item.severity]">{{ labelSeverity(item.severity) }}</span></td>
                <td>{{ item.tableName }}</td>
                <td>{{ item.recordKey }}</td>
                <td>{{ item.description }}</td>
              </tr>
              <tr v-if="findings.length === 0">
                <td colspan="5" class="empty">暂无异常，请先上传并校验 Excel</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <aside class="detail-panel">
        <h2>异常详情</h2>
        <template v-if="detail">
          <dl>
            <dt>规则</dt>
            <dd>{{ detail.finding.ruleId }} {{ detail.finding.ruleName }}</dd>
            <dt>记录</dt>
            <dd>{{ detail.finding.tableName }} / {{ detail.finding.recordKey }}</dd>
            <dt>实际值</dt>
            <dd>{{ detail.finding.actualValue }}</dd>
            <dt>期望值</dt>
            <dd>{{ detail.finding.expectedValue }}</dd>
            <dt>原因</dt>
            <dd>{{ detail.finding.reason }}</dd>
            <dt>影响</dt>
            <dd>{{ detail.finding.impact }}</dd>
            <dt>建议</dt>
            <dd>{{ detail.finding.suggestion }}</dd>
          </dl>
          <div class="detail-actions">
            <button class="mini-button wide" :disabled="loading" @click="loadAiAnalysis">AI 分析</button>
            <button class="mini-button wide" :disabled="loading" @click="loadValidationSqlDraft">只读校验 SQL</button>
            <button class="mini-button wide" :disabled="loading" @click="loadManualReviewSqlDraft">人工核查 SQL</button>
          </div>
          <section v-if="aiAnalysis" class="assist-box">
            <h3>AI 辅助分析</h3>
            <dl>
              <dt>来源</dt>
              <dd>{{ aiAnalysis.source }} · {{ aiAnalysis.generatedByAi ? '模型生成' : '本地降级' }}</dd>
              <dt>原因</dt>
              <dd>{{ aiAnalysis.reason }}</dd>
              <dt>影响</dt>
              <dd>{{ aiAnalysis.impact }}</dd>
              <dt>建议</dt>
              <dd>{{ aiAnalysis.suggestion }}</dd>
              <dt>证据摘要</dt>
              <dd>{{ aiAnalysis.evidenceSummary }}</dd>
            </dl>
            <p v-for="warning in aiAnalysis.warnings" :key="warning" class="assist-warning">{{ warning }}</p>
          </section>
          <section v-if="sqlDraft" class="assist-box">
            <h3>{{ labelDraftType(sqlDraft.draftType) }}</h3>
            <p class="assist-meta">{{ sqlDraft.source }} · {{ sqlDraft.generatedByAi ? '模型生成' : '本地降级' }}</p>
            <pre>{{ sqlDraft.sql }}</pre>
            <p v-for="warning in sqlDraft.warnings" :key="warning" class="assist-warning">{{ warning }}</p>
          </section>
          <h3>证据</h3>
          <div v-for="evidence in detail.evidences" :key="evidence.id" class="evidence">
            {{ evidence.evidenceType }} · {{ evidence.fieldName }} · {{ evidence.calculation }}
          </div>
        </template>
        <p v-else class="empty">点击异常行查看证据链</p>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import {
  analyzeFinding,
  createReport,
  draftValidationSql,
  fetchFindingDetail,
  fetchFindings,
  fetchRules,
  fetchSummary,
  recommendRuleBinding,
  reportDownloadUrl,
  startValidation,
  updateRuleBinding,
  uploadWorkbook
} from './api/client'

const dataset = ref(null)
const job = ref(null)
const summary = ref(null)
const findings = ref([])
const rules = ref([])
const detail = ref(null)
const aiAnalysis = ref(null)
const sqlDraft = ref(null)
const recommendations = reactive({})
const recommendationErrors = reactive({})
const selectedRuleId = ref('')
const message = ref('')
const error = ref('')
const loading = ref(false)
const recommendationLoading = ref(false)
const filters = reactive({
  severity: '',
  tableName: '',
  ruleId: ''
})
const ruleFilters = reactive({
  keyword: '',
  executorType: '',
  recommendationStatus: ''
})
const recommendationBatchProgress = reactive({
  done: 0,
  total: 0
})
const templateCodes = [
  'ROW_EXPRESSION',
  'RELATION_EXISTS',
  'JOIN_ASSERT',
  'AGGREGATE_ASSERT',
  'DUPLICATE_ASSERT'
]

const filteredRules = computed(() => {
  const keyword = ruleFilters.keyword.toLowerCase()
  return rules.value.filter((rule) => {
    const text = `${rule.ruleId} ${rule.ruleName} ${rule.templateCode || ''}`.toLowerCase()
    if (keyword && !text.includes(keyword)) return false
    if (ruleFilters.executorType && rule.executorType !== ruleFilters.executorType) return false
    return matchRecommendationStatus(rule)
  })
})

const selectedRule = computed(() => {
  return filteredRules.value.find((rule) => rule.ruleId === selectedRuleId.value) || filteredRules.value[0] || null
})

const selectedRecommendation = computed(() => {
  if (!selectedRule.value) return null
  return recommendations[selectedRule.value.ruleId] || null
})

const recommendationStats = computed(() => {
  return rules.value.reduce((stats, rule) => {
    const recommendation = recommendations[rule.ruleId]
    if (recommendation) {
      stats.total += 1
      if (recommendation.generatedByAi) {
        stats.ai += 1
      } else {
        stats.local += 1
      }
    }
    if (recommendationErrors[rule.ruleId]) {
      stats.failed += 1
    }
    return stats
  }, { total: 0, ai: 0, local: 0, failed: 0 })
})

const templateCoverageStats = computed(() => {
  const total = rules.value.length
  const byTemplate = templateCodes.map((code) => {
    const count = rules.value.filter((rule) => rule.templateCode === code).length
    return {
      code,
      count,
      percent: total ? Math.round((count / total) * 100) : 0
    }
  })
  return {
    total,
    templateReady: rules.value.filter((rule) => rule.templateCode).length,
    templateBound: rules.value.filter((rule) => rule.executorType === 'TEMPLATE').length,
    builtinBound: rules.value.filter((rule) => rule.executorType !== 'TEMPLATE').length,
    byTemplate
  }
})

watch(rules, (nextRules) => {
  if (!nextRules.length) {
    selectedRuleId.value = ''
    return
  }
  if (!nextRules.some((rule) => rule.ruleId === selectedRuleId.value)) {
    selectedRuleId.value = nextRules[0].ruleId
  }
})

watch(filteredRules, (nextRules) => {
  if (nextRules.length && !nextRules.some((rule) => rule.ruleId === selectedRuleId.value)) {
    selectedRuleId.value = nextRules[0].ruleId
  }
})

async function run(action, successMessage) {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await action()
    message.value = successMessage
    return result
  } catch (err) {
    error.value = err.message || '操作失败'
    return null
  } finally {
    loading.value = false
  }
}

async function onFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const result = await run(() => uploadWorkbook(file), 'Excel 导入完成')
  if (result) {
    dataset.value = result
    job.value = null
    summary.value = null
    findings.value = []
    detail.value = null
    clearRecommendations()
    rules.value = await fetchRules(result.datasetId)
    selectedRuleId.value = rules.value[0]?.ruleId || ''
  }
}

async function validate() {
  const result = await run(() => startValidation(dataset.value.datasetId, false), '校验完成')
  if (result) {
    job.value = result
    summary.value = await fetchSummary(result.jobId)
    await loadFindings()
  }
}

async function loadFindings() {
  if (!job.value) return
  const result = await run(() => fetchFindings(job.value.jobId, filters), '异常列表已刷新')
  if (result) {
    findings.value = result.items
  }
}

async function selectFinding(findingId) {
  const result = await run(() => fetchFindingDetail(findingId), '异常详情已加载')
  if (result) {
    detail.value = result
    aiAnalysis.value = null
    sqlDraft.value = null
  }
}

async function exportReport() {
  const result = await run(() => createReport(job.value.jobId, 'MARKDOWN'), '报告已生成')
  if (result) {
    window.open(reportDownloadUrl(result.reportId), '_blank')
  }
}

async function switchRuleBinding(rule) {
  if (!dataset.value || !rule.templateCode) return
  const nextExecutorType = rule.executorType === 'TEMPLATE' ? 'BUILTIN' : 'TEMPLATE'
  const result = await run(() => updateRuleBinding(dataset.value.datasetId, rule.ruleId, {
    executorType: nextExecutorType,
    templateCode: rule.templateCode,
    templateParams: rule.templateParams || {}
  }), nextExecutorType === 'TEMPLATE' ? '已切换为模板执行' : '已切换为内置执行')
  if (result) {
    rules.value = await fetchRules(dataset.value.datasetId)
    selectedRuleId.value = rule.ruleId
  }
}

async function loadRuleRecommendation(rule) {
  if (!dataset.value || !rule) return
  selectedRuleId.value = rule.ruleId
  recommendationLoading.value = true
  error.value = ''
  message.value = ''
  try {
    await recommendOneRule(rule)
    message.value = '规则模板推荐已生成'
  } catch (err) {
    error.value = err.message || '规则模板推荐失败'
  } finally {
    recommendationLoading.value = false
  }
}

async function loadVisibleRuleRecommendations() {
  if (!dataset.value || filteredRules.value.length === 0) return
  const targetRules = [...filteredRules.value]
  let successCount = 0
  recommendationLoading.value = true
  recommendationBatchProgress.done = 0
  recommendationBatchProgress.total = targetRules.length
  error.value = ''
  message.value = ''
  try {
    for (const rule of targetRules) {
      try {
        await recommendOneRule(rule)
        successCount += 1
      } catch (_) {
        // 失败信息已记录到对应规则，批量流程继续推进。
      } finally {
        recommendationBatchProgress.done += 1
      }
    }
    message.value = `批量推荐完成：成功 ${successCount} 条，失败 ${targetRules.length - successCount} 条`
  } finally {
    recommendationLoading.value = false
  }
}

async function applyRuleRecommendation(rule) {
  if (!dataset.value || !recommendations[rule.ruleId]) return
  const recommendation = recommendations[rule.ruleId]
  const result = await run(() => updateRuleBinding(dataset.value.datasetId, rule.ruleId, {
    executorType: 'TEMPLATE',
    templateCode: recommendation.templateCode,
    templateParams: recommendation.templateParams || {}
  }), '已应用规则模板推荐')
  if (result) {
    delete recommendations[rule.ruleId]
    delete recommendationErrors[rule.ruleId]
    rules.value = await fetchRules(dataset.value.datasetId)
    selectedRuleId.value = rule.ruleId
  }
}

async function recommendOneRule(rule) {
  try {
    const result = await recommendRuleBinding(dataset.value.datasetId, rule.ruleId)
    recommendations[rule.ruleId] = result
    delete recommendationErrors[rule.ruleId]
    return result
  } catch (err) {
    const messageText = err.message || '规则模板推荐失败'
    recommendationErrors[rule.ruleId] = messageText
    throw new Error(`${rule.ruleId}: ${messageText}`)
  }
}

function selectRule(ruleId) {
  selectedRuleId.value = ruleId
}

async function loadAiAnalysis() {
  if (!detail.value) return
  const result = await run(() => analyzeFinding(detail.value.finding.findingId), 'AI 分析已生成')
  if (result) {
    aiAnalysis.value = result
  }
}

async function loadValidationSqlDraft() {
  await loadSqlDraft('VALIDATION_CHECK')
}

async function loadManualReviewSqlDraft() {
  await loadSqlDraft('MANUAL_REVIEW')
}

async function loadSqlDraft(draftType) {
  if (!detail.value) return
  const finding = detail.value.finding
  const result = await run(() => draftValidationSql({
    draftType,
    tableName: finding.tableName,
    fieldName: finding.fieldName,
    actualValue: finding.actualValue,
    expectedValue: finding.expectedValue,
    recordKey: finding.recordKey
  }), draftType === 'MANUAL_REVIEW' ? '人工核查 SQL 草案已生成' : '只读校验 SQL 草案已生成')
  if (result) {
    sqlDraft.value = result
  }
}

function labelSeverity(severity) {
  return severity === 'CRITICAL' ? '严重' : '警告'
}

function labelExecutor(executorType) {
  return executorType === 'TEMPLATE' ? '模板' : '内置'
}

function labelRecommendationStatus(rule) {
  if (recommendationErrors[rule.ruleId]) return '推荐失败'
  const recommendation = recommendations[rule.ruleId]
  if (!recommendation) return '未推荐'
  return recommendation.generatedByAi ? '模型生成' : '本地推荐'
}

function recommendationClass(rule) {
  if (recommendationErrors[rule.ruleId]) return 'failed'
  const recommendation = recommendations[rule.ruleId]
  if (!recommendation) return 'idle'
  return recommendation.generatedByAi ? 'ai' : 'local'
}

function matchRecommendationStatus(rule) {
  const recommendation = recommendations[rule.ruleId]
  const hasError = Boolean(recommendationErrors[rule.ruleId])
  switch (ruleFilters.recommendationStatus) {
    case 'recommended':
      return Boolean(recommendation)
    case 'ai':
      return Boolean(recommendation?.generatedByAi)
    case 'local':
      return Boolean(recommendation && !recommendation.generatedByAi)
    case 'failed':
      return hasError
    case 'notRecommended':
      return !recommendation && !hasError
    default:
      return true
  }
}

function labelDraftType(draftType) {
  return draftType === 'MANUAL_REVIEW' ? '人工核查 SQL 草案' : '只读校验 SQL 草案'
}

function summarizeParams(params = {}) {
  return Object.entries(params)
    .map(([key, value]) => `${key}=${stringifyParam(value)}`)
    .join(', ')
}

function paramEntries(params = {}) {
  return Object.entries(params || {}).map(([key, value]) => ({
    key,
    value: stringifyParam(value)
  }))
}

function stringifyParam(value) {
  if (Array.isArray(value)) {
    return value.map((item) => stringifyParam(item)).join(' / ')
  }
  if (value && typeof value === 'object') {
    return JSON.stringify(value)
  }
  return value ?? ''
}

function clearRecommendations() {
  Object.keys(recommendations).forEach((key) => delete recommendations[key])
  Object.keys(recommendationErrors).forEach((key) => delete recommendationErrors[key])
  recommendationBatchProgress.done = 0
  recommendationBatchProgress.total = 0
}
</script>
