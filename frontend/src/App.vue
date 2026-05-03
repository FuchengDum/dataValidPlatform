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

    <section class="workspace">
      <aside class="side-panel">
        <h2>筛选</h2>
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
        <button :disabled="!job" @click="loadFindings">刷新列表</button>

        <h2>规则覆盖</h2>
        <div class="rule-list">
          <div v-for="rule in rules" :key="rule.ruleId" class="rule-item">
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
            <p v-if="rule.templateParamSummary" class="param-summary">{{ rule.templateParamSummary }}</p>
            <div class="rule-actions">
              <button
                class="mini-button"
                :disabled="!rule.templateCode || loading"
                @click="switchRuleBinding(rule)">
                {{ rule.executorType === 'TEMPLATE' ? '切回内置' : '切到模板' }}
              </button>
              <button
                class="mini-button"
                :disabled="!dataset || loading"
                @click="loadRuleRecommendation(rule)">
                AI 推荐
              </button>
            </div>
            <section v-if="recommendations[rule.ruleId]" class="recommendation-box">
              <div class="rule-binding">
                <span class="tag template">{{ recommendations[rule.ruleId].templateCode }}</span>
                <span class="template-code">
                  {{ recommendations[rule.ruleId].source }} · {{ recommendations[rule.ruleId].generatedByAi ? '模型生成' : '本地推荐' }}
                </span>
              </div>
              <p class="param-summary">{{ summarizeParams(recommendations[rule.ruleId].templateParams) }}</p>
              <p class="recommendation-text">{{ recommendations[rule.ruleId].explanation }}</p>
              <p
                v-for="warning in recommendations[rule.ruleId].warnings"
                :key="warning"
                class="assist-warning">
                {{ warning }}
              </p>
              <button
                class="mini-button wide"
                :disabled="loading"
                @click="applyRuleRecommendation(rule)">
                应用推荐
              </button>
            </section>
          </div>
        </div>
      </aside>

      <section class="table-panel">
        <div class="panel-head">
          <h2>异常疑点清单</h2>
          <span>{{ findings.length }} 条</span>
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
import { reactive, ref } from 'vue'
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
const message = ref('')
const error = ref('')
const loading = ref(false)
const filters = reactive({
  severity: '',
  tableName: '',
  ruleId: ''
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
  }
}

async function loadRuleRecommendation(rule) {
  if (!dataset.value) return
  const result = await run(() => recommendRuleBinding(dataset.value.datasetId, rule.ruleId), '规则模板推荐已生成')
  if (result) {
    recommendations[rule.ruleId] = result
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
    rules.value = await fetchRules(dataset.value.datasetId)
  }
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

function labelDraftType(draftType) {
  return draftType === 'MANUAL_REVIEW' ? '人工核查 SQL 草案' : '只读校验 SQL 草案'
}

function summarizeParams(params = {}) {
  return Object.entries(params)
    .map(([key, value]) => `${key}=${Array.isArray(value) ? value.join('/') : value}`)
    .join(', ')
}

function clearRecommendations() {
  Object.keys(recommendations).forEach((key) => delete recommendations[key])
}
</script>
