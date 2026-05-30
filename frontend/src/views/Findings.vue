<template>
  <div class="findings-page">
    <div class="page-header">
      <div class="breadcrumb">
        <span @click="$router.push('/')">首页</span>
        <span class="separator">/</span>
        <span @click="$router.push('/upload')">数据上传</span>
        <span class="separator">/</span>
        <span @click="$router.push('/rules')">规则配置</span>
        <span class="separator">/</span>
        <span @click="$router.push('/validation')">数据校验</span>
        <span class="separator">/</span>
        <span class="current">异常分析</span>
      </div>
      <h1 class="page-title">异常分析</h1>
      <p class="page-desc">查看异常详情，分析问题原因</p>
    </div>

    <div v-if="!store.state.job" class="no-data">
      <div class="no-data-icon">📋</div>
      <h3>请先执行数据校验</h3>
      <p>在查看异常之前，需要先执行数据校验</p>
      <button class="primary-btn" @click="$router.push('/validation')">
        前往校验
      </button>
    </div>

    <div v-else class="findings-content">
      <div class="filter-bar">
        <div class="filter-item">
          <label>严重等级</label>
          <select v-model="filters.severity" @change="loadFindings">
            <option value="">全部</option>
            <option value="CRITICAL">严重</option>
            <option value="WARNING">警告</option>
          </select>
        </div>
        <div class="filter-item">
          <label>业务表</label>
          <select v-model="filters.tableName" @change="loadFindings">
            <option value="">全部</option>
            <option value="t_order">t_order</option>
            <option value="t_order_item">t_order_item</option>
            <option value="t_product">t_product</option>
            <option value="t_payment">t_payment</option>
            <option value="t_inventory_log">t_inventory_log</option>
          </select>
        </div>
        <div class="filter-item">
          <label>规则编号</label>
          <input v-model="filters.ruleId" placeholder="例如 R006" @keyup.enter="loadFindings" />
        </div>
        <div class="filter-actions">
          <button class="secondary-btn refrsh" @click="loadFindings">
            刷新列表
          </button>
        </div>
      </div>

      <div class="findings-layout">
        <div class="findings-list">
          <div class="list-header">
            <h3>异常列表</h3>
            <span class="count">{{ store.state.findings.length }} 条异常</span>
          </div>
          <div class="list-content">
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
                <tr v-for="item in store.state.findings" :key="item.findingId"
                    :class="{ active: store.getters.selectedFinding?.findingId === item.findingId }"
                    @click="selectFinding(item.findingId)">
                  <td class="rule-cell">{{ item.ruleId }}</td>
                  <td>
                    <span :class="['severity-badge', item.severity]">
                      {{ labelSeverity(item.severity) }}
                    </span>
                  </td>
                  <td class="table-cell">{{ item.tableName }}</td>
                  <td class="key-cell">{{ item.recordKey }}</td>
                  <td class="desc-cell">{{ item.description }}</td>
                </tr>
                <tr v-if="store.state.findings.length === 0">
                  <td colspan="5" class="empty-cell">
                    暂无异常数据
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="finding-detail">
          <div v-if="detail" class="detail-content">
            <div class="detail-header">
              <h3>异常详情</h3>
            </div>

            <div class="detail-info">
              <div class="info-row">
                <span class="info-label">规则</span>
                <span class="info-value">{{ detail.finding.ruleId }} {{ detail.finding.ruleName }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">记录</span>
                <span class="info-value">{{ detail.finding.tableName }} / {{ detail.finding.recordKey }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">实际值</span>
                <span class="info-value">{{ detail.finding.actualValue }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">期望值</span>
                <span class="info-value">{{ detail.finding.expectedValue }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">原因</span>
                <span class="info-value">{{ detail.finding.reason }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">影响</span>
                <span class="info-value">{{ detail.finding.impact }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">建议</span>
                <span class="info-value">{{ detail.finding.suggestion }}</span>
              </div>
            </div>

            <div class="detail-actions">
              <button class="secondary-btn" :disabled="store.state.loading" @click="loadAiAnalysis">
                AI 分析
              </button>
              <button class="secondary-btn" :disabled="store.state.loading" @click="loadValidationSql">
                校验 SQL
              </button>
              <button class="secondary-btn" :disabled="store.state.loading" @click="loadManualReviewSql">
                人工核查 SQL
              </button>
            </div>

            <div v-if="aiAnalysis" class="ai-section">
              <div class="section-header">
                <span class="section-icon">🤖</span>
                <h4>AI 辅助分析</h4>
              </div>
              <div class="ai-content">
                <div class="ai-source">
                  {{ aiAnalysis.source }} · {{ aiAnalysis.generatedByAi ? 'AI生成' : '本地降级' }}
                </div>
                <div class="ai-row">
                  <span class="ai-label">原因</span>
                  <span class="ai-value">{{ aiAnalysis.reason }}</span>
                </div>
                <div class="ai-row">
                  <span class="ai-label">影响</span>
                  <span class="ai-value">{{ aiAnalysis.impact }}</span>
                </div>
                <div class="ai-row">
                  <span class="ai-label">建议</span>
                  <span class="ai-value">{{ aiAnalysis.suggestion }}</span>
                </div>
                <div class="ai-row">
                  <span class="ai-label">证据摘要</span>
                  <span class="ai-value">{{ aiAnalysis.evidenceSummary }}</span>
                </div>
                <div v-if="aiAnalysis.warnings?.length" class="ai-warnings">
                  <div v-for="warning, i in aiAnalysis.warnings" :key="i" class="warning-item">
                    {{ warning }}
                  </div>
                </div>
              </div>
            </div>

            <div v-if="sqlDraft" class="sql-section">
              <div class="section-header">
                <span class="section-icon">📝</span>
                <h4>{{ labelDraftType(sqlDraft.draftType) }}</h4>
              </div>
              <div class="sql-content">
                <div class="sql-source">
                  {{ sqlDraft.source }} · {{ sqlDraft.generatedByAi ? 'AI生成' : '本地降级' }}
                </div>
                <pre class="sql-code">{{ sqlDraft.sql }}</pre>
                <div v-if="sqlDraft.warnings?.length" class="sql-warnings">
                  <div v-for="warning, i in sqlDraft.warnings" :key="i" class="warning-item">
                    {{ warning }}
                  </div>
                </div>
              </div>
            </div>

            <div class="evidence-section">
              <div class="section-header">
                <span class="section-icon">🔍</span>
                <h4>证据链</h4>
              </div>
              <div class="evidence-list">
                <div v-for="evidence in detail.evidences" :key="evidence.id" class="evidence-item">
                  <div class="evidence-type">{{ evidence.evidenceType }}</div>
                  <div class="evidence-field">{{ evidence.fieldName }}</div>
                  <div class="evidence-calc">{{ evidence.calculation }}</div>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="no-selection">
            <div class="no-selection-icon">👆</div>
            <p>请从左侧选择一条异常查看详情</p>
          </div>
        </div>
      </div>

      <div class="page-actions">
        <button class="secondary-btn" @click="$router.push('/validation')">
          返回校验
        </button>
        <button class="primary-btn" @click="$router.push('/report')">
          下一步：导出报告
        </button>
      </div>
    </div>

    <StatusMessage />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import store from '../store'
import { 
  fetchFindings, 
  fetchFindingDetail, 
  analyzeFinding, 
  draftValidationSql 
} from '../api/client'
import StatusMessage from '../components/StatusMessage.vue'

const filters = reactive({
  severity: '',
  tableName: '',
  ruleId: ''
})

const detail = ref(null)
const aiAnalysis = ref(null)
const sqlDraft = ref(null)

onMounted(() => {
  if (store.state.job) {
    loadFindings()
  }
})

// 监听 job 的变化，重新加载数据
watch(() => store.state.job, (newJob) => {
  if (newJob) {
    loadFindings()
  }
}, { immediate: true })

async function loadFindings() {
  if (!store.state.job) return
  
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    const result = await fetchFindings(store.state.job.jobId, filters)
    store.actions.setFindings(result.items)
  } catch (err) {
    store.actions.setError(err.message || '加载失败')
  } finally {
    store.actions.setLoading(false)
  }
}

async function selectFinding(findingId) {
  store.actions.setSelectedFindingId(findingId)
  detail.value = null
  aiAnalysis.value = null
  sqlDraft.value = null
  
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    const result = await fetchFindingDetail(findingId)
    detail.value = result
  } catch (err) {
    store.actions.setError(err.message || '加载失败')
  } finally {
    store.actions.setLoading(false)
  }
}

async function loadAiAnalysis() {
  if (!detail.value) return
  
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    const result = await analyzeFinding(detail.value.finding.findingId)
    aiAnalysis.value = result
    store.actions.setMessage('AI 分析已生成')
  } catch (err) {
    store.actions.setError(err.message || 'AI 分析失败')
  } finally {
    store.actions.setLoading(false)
  }
}

async function loadValidationSql() {
  await loadSql('VALIDATION_CHECK')
}

async function loadManualReviewSql() {
  await loadSql('MANUAL_REVIEW')
}

async function loadSql(draftType) {
  if (!detail.value) return
  
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    const finding = detail.value.finding
    const result = await draftValidationSql({
      draftType,
      tableName: finding.tableName,
      fieldName: finding.fieldName,
      actualValue: finding.actualValue,
      expectedValue: finding.expectedValue,
      recordKey: finding.recordKey
    })
    sqlDraft.value = result
    store.actions.setMessage(draftType === 'MANUAL_REVIEW' ? '人工核查 SQL 已生成' : '校验 SQL 已生成')
  } catch (err) {
    store.actions.setError(err.message || 'SQL 生成失败')
  } finally {
    store.actions.setLoading(false)
  }
}

function labelSeverity(severity) {
  return severity === 'CRITICAL' ? '严重' : '警告'
}

function labelDraftType(draftType) {
  return draftType === 'MANUAL_REVIEW' ? '人工核查 SQL' : '校验 SQL'
}
</script>

<style scoped>
.findings-page {
  padding: 32px 28px;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 32px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #64748b;
  margin-bottom: 16px;
}

.breadcrumb span {
  cursor: pointer;
}

.breadcrumb span:hover {
  color: #2563eb;
}

.breadcrumb .current {
  color: #1f2937;
  font-weight: 500;
  cursor: default;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 8px;
  color: #1f2937;
}

.page-desc {
  font-size: 16px;
  color: #64748b;
  margin: 0;
}

.no-data {
  background: #fff;
  border-radius: 16px;
  padding: 80px 40px;
  text-align: center;
}

.no-data-icon {
  font-size: 80px;
  margin-bottom: 24px;
}

.no-data h3 {
  font-size: 24px;
  margin: 0 0 8px;
  color: #1f2937;
}

.no-data p {
  font-size: 14px;
  color: #64748b;
  margin: 0 0 32px;
}

.primary-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
  border-radius: 10px;
  height: 40px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.primary-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.3);
}

.primary-btn:disabled,
.secondary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.secondary-btn {
  background: #fff;
  color: #374151;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  padding: 0px 32px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  height: 40px;
}

.secondary-btn:hover:not(:disabled) {
  border-color: #667eea;
  color: #667eea;
}

.filter-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 24px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-item label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.filter-item input,
.filter-item select {
  height: 40px;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  transition: all 0.3s ease;
}

.filter-item input:focus,
.filter-item select:focus {
  outline: none;
  border-color: #667eea;
}

.filter-actions {
  display: flex;
  align-items: flex-end;
}

.findings-layout {
  display: grid;
  grid-template-columns: 1fr 520px;
  gap: 24px;
  margin-bottom: 32px;
}

.findings-list {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.list-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: #1f2937;
}

.count {
  font-size: 14px;
  color: #64748b;
}

.list-content {
  max-height: 700px;
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

thead {
  position: sticky;
  top: 0;
  background: #f8fafc;
  z-index: 1;
}

th {
  padding: 14px 16px;
  text-align: left;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  border-bottom: 2px solid #e5e7eb;
}

td {
  padding: 14px 16px;
  font-size: 14px;
  color: #374151;
  border-bottom: 1px solid #f1f5f9;
}

tbody tr {
  cursor: pointer;
  transition: background 0.2s ease;
}

tbody tr:hover {
  background: #f8fafc;
}

tbody tr.active {
  background: #f0f4ff;
}

.rule-cell {
  font-weight: 600;
  color: #1f2937;
}

.table-cell,
.key-cell {
  font-family: ui-monospace, monospace;
  font-size: 13px;
}

.desc-cell {
  color: #64748b;
}

.severity-badge {
  display: inline-flex;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.severity-badge.CRITICAL {
  background: #fee2e2;
  color: #b91c1c;
}

.severity-badge.WARNING {
  background: #fef3c7;
  color: #92400e;
}

.empty-cell {
  text-align: center;
  padding: 60px 20px;
  color: #94a3b8;
}

.finding-detail {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  max-height: 800px;
  overflow-y: auto;
}

.detail-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.detail-header h3 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: #1f2937;
}

.detail-info {
  margin-bottom: 24px;
}

.info-row {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.info-label {
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
}

.info-value {
  font-size: 14px;
  color: #1f2937;
  line-height: 1.6;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 24px;
}

.detail-actions .secondary-btn {
  padding: 0px 20px;
  font-size: 14px;
}

.ai-section,
.sql-section,
.evidence-section {
  margin-bottom: 24px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 10px;
}

.ai-section {
  border-left: 4px solid #22c55e;
}

.sql-section {
  border-left: 4px solid #3b82f6;
}

.evidence-section {
  border-left: 4px solid #f59e0b;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.section-icon {
  font-size: 24px;
}

.section-header h4 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: #1f2937;
}

.ai-content,
.sql-content {
  font-size: 14px;
}

.ai-source,
.sql-source {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 12px;
}

.ai-row {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #e2e8f0;
}

.ai-label {
  font-weight: 600;
  color: #64748b;
}

.ai-value {
  color: #1f2937;
  line-height: 1.6;
}

.ai-warnings,
.sql-warnings {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e2e8f0;
}

.warning-item {
  color: #d97706;
  font-size: 13px;
  padding: 4px 0;
}

.sql-code {
  background: #1e293b;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 8px;
  font-family: ui-monospace, monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  margin: 0;
}

.evidence-list {
  display: grid;
  gap: 12px;
}

.evidence-item {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px 16px;
  display: grid;
  grid-template-columns: 1fr 1fr 130px;
  gap: 12px;
  align-items: center;
}

.evidence-type {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.evidence-field {
  font-family: ui-monospace, monospace;
  font-size: 13px;
  color: #667eea;
}

.evidence-calc {
  font-size: 13px;
  color: #64748b;
  word-break: break-word;
}

.no-selection {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 500px;
  color: #94a3b8;
}

.no-selection-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.page-actions {
  display: flex;
  gap: 16px;
  justify-content: flex-end;
}
</style>
