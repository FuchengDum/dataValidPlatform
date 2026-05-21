<template>
  <div class="rules-page">
    <div class="page-header">
      <div class="breadcrumb">
        <span @click="$router.push('/')">首页</span>
        <span class="separator">/</span>
        <span @click="$router.push('/upload')">数据上传</span>
        <span class="separator">/</span>
        <span class="current">规则配置</span>
      </div>
      <h1 class="page-title">规则配置</h1>
      <p class="page-desc">配置数据校验规则，AI 智能推荐模板参数</p>
    </div>

    <div v-if="!store.state.dataset" class="no-data">
      <div class="no-data-icon">📁</div>
      <h3>请先上传数据文件</h3>
      <p>在配置规则之前，需要先上传您的 Excel 数据文件</p>
      <button class="primary-btn" @click="$router.push('/upload')">
        前往上传
      </button>
    </div>

    <div v-else class="rules-content">
      <div class="stats-bar">
        <div class="stat-item">
          <span class="stat-label">规则总数</span>
          <span class="stat-value">{{ store.state.rules.length }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">模板绑定</span>
          <span class="stat-value">{{ templateBoundCount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">已推荐</span>
          <span class="stat-value">{{ recommendedCount }}</span>
        </div>
      </div>

      <div class="filter-bar">
        <div class="filter-item">
          <label>规则搜索</label>
          <input v-model="filters.keyword" placeholder="输入规则ID或名称" />
        </div>
        <div class="filter-item">
          <label>执行类型</label>
          <select v-model="filters.executorType">
            <option value="">全部</option>
            <option value="TEMPLATE">模板</option>
            <option value="BUILTIN">内置</option>
          </select>
        </div>
        <div class="filter-item">
          <label>推荐状态</label>
          <select v-model="filters.recommendationStatus">
            <option value="">全部</option>
            <option value="recommended">已推荐</option>
            <option value="ai">AI生成</option>
            <option value="local">本地推荐</option>
            <option value="failed">推荐失败</option>
            <option value="notRecommended">未推荐</option>
          </select>
        </div>
        <div class="filter-actions">
          <button class="secondary-btn" :disabled="filteredRules.length === 0 || store.state.loading" @click="batchRecommend">
            批量推荐
          </button>
        </div>
      </div>

      <div class="rules-layout">
        <div class="rules-list">
          <div class="list-header">
            <h3>规则列表</h3>
            <span class="count">{{ filteredRules.length }} 条规则</span>
          </div>
          <div class="rules-scroll">
            <div v-for="rule in filteredRules" :key="rule.ruleId"
                 class="rule-item"
                 :class="{ active: store.getters.selectedRule?.ruleId === rule.ruleId }"
                 @click="selectRule(rule.ruleId)">
              <div class="rule-header">
                <div class="rule-id">{{ rule.ruleId }}</div>
                <div class="rule-badges">
                  <span :class="['badge', rule.executorType === 'TEMPLATE' ? 'template' : 'builtin']">
                    {{ labelExecutor(rule.executorType) }}
                  </span>
                  <span :class="['badge rec-badge', recommendationClass(rule)]">
                    {{ labelRecommendationStatus(rule) }}
                  </span>
                </div>
              </div>
              <div class="rule-name">{{ rule.ruleName }}</div>
              <div v-if="rule.templateCode" class="rule-template">
                模板: {{ rule.templateCode }}
              </div>
            </div>
          </div>
        </div>

        <div class="rule-detail">
          <div v-if="store.getters.selectedRule" class="detail-content">
            <div class="detail-header">
              <h3>{{ store.getters.selectedRule.ruleId }} {{ store.getters.selectedRule.ruleName }}</h3>
              <div class="detail-actions">
                <button class="secondary-btn small" :disabled="!store.getters.selectedRule.templateCode || store.state.loading" @click="switchBinding">
                  {{ store.getters.selectedRule.executorType === 'TEMPLATE' ? '切回内置' : '启用模板' }}
                </button>
                <button class="primary-btn small" :disabled="store.state.loading" @click="recommendRule">
                  AI 推荐
                </button>
              </div>
            </div>

            <div v-if="recommendationErrors[store.getters.selectedRule.ruleId]" class="error-box">
              {{ recommendationErrors[store.getters.selectedRule.ruleId] }}
            </div>

            <div class="binding-compare">
              <div class="binding-card">
                <div class="card-header">
                  <span class="card-title">当前配置</span>
                </div>
                <div class="card-content">
                  <div class="binding-type">
                    {{ labelExecutor(store.getters.selectedRule.executorType) }}
                    <span v-if="store.getters.selectedRule.templateCode">
                      · {{ store.getters.selectedRule.templateCode }}
                    </span>
                  </div>
                  <div v-if="paramEntries(store.getters.selectedRule.templateParams).length > 0" class="params-list">
                    <div v-for="param in paramEntries(store.getters.selectedRule.templateParams)" :key="param.key" class="param-item">
                      <span class="param-key">{{ param.key }}</span>
                      <span class="param-value">{{ param.value }}</span>
                    </div>
                  </div>
                  <div v-else class="no-params">无模板参数</div>
                </div>
              </div>

              <div class="binding-card recommended">
                <div class="card-header">
                  <span class="card-title">AI 推荐</span>
                  <span v-if="selectedRecommendation" class="ai-tag">✨</span>
                </div>
                <div class="card-content">
                  <div v-if="selectedRecommendation" class="binding-type">
                    {{ selectedRecommendation.templateCode }}
                    <span class="source-tag">
                      {{ selectedRecommendation.generatedByAi ? 'AI生成' : '本地推荐' }}
                    </span>
                  </div>
                  <div v-else class="waiting-rec">
                    点击「AI 推荐」获取模板参数
                  </div>
                  <div v-if="selectedRecommendation && paramEntries(selectedRecommendation.templateParams).length > 0" class="params-list">
                    <div v-for="param in paramEntries(selectedRecommendation.templateParams)" :key="param.key" class="param-item">
                      <span class="param-key">{{ param.key }}</span>
                      <span class="param-value">{{ param.value }}</span>
                    </div>
                  </div>
                  <div v-if="selectedRecommendation" class="rec-explanation">
                    {{ selectedRecommendation.explanation }}
                  </div>
                </div>
              </div>
            </div>

            <div v-if="selectedRecommendation" class="apply-section">
              <button class="primary-btn" :disabled="store.state.loading" @click="applyRecommendation">
                应用推荐配置
              </button>
            </div>
          </div>
          <div v-else class="no-selection">
            <div class="no-selection-icon">📋</div>
            <p>请从左侧选择一条规则查看详情</p>
          </div>
        </div>
      </div>

      <div class="template-matrix">
        <h3>模板能力矩阵</h3>
        <div class="matrix-grid">
          <div v-for="item in templateCoverage" :key="item.code" class="matrix-item">
            <div class="matrix-header">
              <span class="template-code">{{ item.code }}</span>
              <span class="template-count">{{ item.count }} 条规则</span>
            </div>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: item.percent + '%' }"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="page-actions">
        <button class="secondary-btn" @click="$router.push('/upload')">
          返回上传
        </button>
        <button class="primary-btn" @click="$router.push('/validation')">
          下一步：开始校验
        </button>
      </div>
    </div>

    <StatusMessage />
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import store from '../store'
import { fetchRules, recommendRuleBinding, updateRuleBinding } from '../api/client'
import StatusMessage from '../components/StatusMessage.vue'

const filters = reactive({
  keyword: '',
  executorType: '',
  recommendationStatus: ''
})

const recommendations = reactive({})
const recommendationErrors = reactive({})

const templateCodes = [
  'ROW_EXPRESSION',
  'RELATION_EXISTS',
  'JOIN_ASSERT',
  'AGGREGATE_ASSERT',
  'DUPLICATE_ASSERT'
]

const filteredRules = computed(() => {
  return store.state.rules.filter((rule) => {
    const text = `${rule.ruleId} ${rule.ruleName}`.toLowerCase()
    if (filters.keyword && !text.includes(filters.keyword.toLowerCase())) return false
    if (filters.executorType && rule.executorType !== filters.executorType) return false
    return matchRecommendationStatus(rule)
  })
})

const selectedRecommendation = computed(() => {
  if (!store.getters.selectedRule) return null
  return recommendations[store.getters.selectedRule.ruleId] || null
})

const templateBoundCount = computed(() => {
  return store.state.rules.filter(r => r.executorType === 'TEMPLATE').length
})

const recommendedCount = computed(() => {
  return Object.keys(recommendations).length
})

const templateCoverage = computed(() => {
  return templateCodes.map((code) => {
    const count = store.state.rules.filter((rule) => rule.templateCode === code).length
    return {
      code,
      count,
      percent: store.state.rules.length ? Math.round((count / store.state.rules.length) * 100) : 0
    }
  })
})

function selectRule(ruleId) {
  store.actions.setSelectedRuleId(ruleId)
}

function labelExecutor(type) {
  return type === 'TEMPLATE' ? '模板' : '内置'
}

function labelRecommendationStatus(rule) {
  if (recommendationErrors[rule.ruleId]) return '推荐失败'
  const rec = recommendations[rule.ruleId]
  if (!rec) return '未推荐'
  return rec.generatedByAi ? 'AI生成' : '本地推荐'
}

function recommendationClass(rule) {
  if (recommendationErrors[rule.ruleId]) return 'failed'
  const rec = recommendations[rule.ruleId]
  if (!rec) return 'idle'
  return rec.generatedByAi ? 'ai' : 'local'
}

function matchRecommendationStatus(rule) {
  const rec = recommendations[rule.ruleId]
  const hasError = Boolean(recommendationErrors[rule.ruleId])
  switch (filters.recommendationStatus) {
    case 'recommended':
      return Boolean(rec)
    case 'ai':
      return Boolean(rec?.generatedByAi)
    case 'local':
      return Boolean(rec && !rec.generatedByAi)
    case 'failed':
      return hasError
    case 'notRecommended':
      return !rec && !hasError
    default:
      return true
  }
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

async function recommendRule() {
  if (!store.getters.selectedRule) return
  
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    const result = await recommendRuleBinding(store.state.dataset.datasetId, store.getters.selectedRule.ruleId)
    recommendations[store.getters.selectedRule.ruleId] = result
    delete recommendationErrors[store.getters.selectedRule.ruleId]
    store.actions.setMessage('规则模板推荐已生成')
  } catch (err) {
    recommendationErrors[store.getters.selectedRule.ruleId] = err.message || '推荐失败'
    store.actions.setError(err.message || '推荐失败')
  } finally {
    store.actions.setLoading(false)
  }
}

async function batchRecommend() {
  if (filteredRules.value.length === 0) return
  
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  let success = 0
  for (const rule of filteredRules.value) {
    try {
      const result = await recommendRuleBinding(store.state.dataset.datasetId, rule.ruleId)
      recommendations[rule.ruleId] = result
      delete recommendationErrors[rule.ruleId]
      success++
    } catch (err) {
      recommendationErrors[rule.ruleId] = err.message || '推荐失败'
    }
  }
  
  store.actions.setMessage(`批量推荐完成：成功 ${success} 条，失败 ${filteredRules.value.length - success} 条`)
  store.actions.setLoading(false)
}

async function switchBinding() {
  if (!store.getters.selectedRule?.templateCode) return
  
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    const nextType = store.getters.selectedRule.executorType === 'TEMPLATE' ? 'BUILTIN' : 'TEMPLATE'
    await updateRuleBinding(store.state.dataset.datasetId, store.getters.selectedRule.ruleId, {
      executorType: nextType,
      templateCode: store.getters.selectedRule.templateCode,
      templateParams: store.getters.selectedRule.templateParams || {}
    })
    
    const rules = await fetchRules(store.state.dataset.datasetId)
    store.actions.setRules(rules)
    store.actions.setMessage(nextType === 'TEMPLATE' ? '已切换为模板执行' : '已切换为内置执行')
  } catch (err) {
    store.actions.setError(err.message || '操作失败')
  } finally {
    store.actions.setLoading(false)
  }
}

async function applyRecommendation() {
  if (!store.getters.selectedRule || !selectedRecommendation.value) return
  
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    await updateRuleBinding(store.state.dataset.datasetId, store.getters.selectedRule.ruleId, {
      executorType: 'TEMPLATE',
      templateCode: selectedRecommendation.value.templateCode,
      templateParams: selectedRecommendation.value.templateParams || {}
    })
    
    delete recommendations[store.getters.selectedRule.ruleId]
    delete recommendationErrors[store.getters.selectedRule.ruleId]
    
    const rules = await fetchRules(store.state.dataset.datasetId)
    store.actions.setRules(rules)
    store.actions.setMessage('已应用推荐配置')
  } catch (err) {
    store.actions.setError(err.message || '操作失败')
  } finally {
    store.actions.setLoading(false)
  }
}
</script>

<style scoped>
.rules-page {
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
  padding: 0px 32px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.primary-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.3);
}

.primary-btn:disabled,
.secondary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.secondary-btn {
  background: #fff;
  color: #374151;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px 32px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.secondary-btn:hover:not(:disabled) {
  border-color: #667eea;
  color: #667eea;
}

.secondary-btn.small,
.primary-btn.small {
  padding: 8px 20px;
  font-size: 14px;
}

.stats-bar {
  display: flex;
  gap: 24px;
  background: #fff;
  border-radius: 12px;
  padding: 24px 32px;
  margin-bottom: 24px;
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 14px;
  color: #64748b;
  margin-bottom: 8px;
}

.stat-value {
  display: block;
  font-size: 32px;
  font-weight: 700;
  color: #1f2937;
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

.rules-layout {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 24px;
  margin-bottom: 32px;
}

.rules-list {
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

.rules-scroll {
  max-height: 600px;
  overflow-y: auto;
  padding: 12px;
}

.rule-item {
  padding: 16px;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.rule-item:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.rule-item.active {
  border-color: #667eea;
  background: #f0f4ff;
}

.rule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.rule-id {
  font-size: 14px;
  font-weight: 700;
  color: #1f2937;
}

.rule-badges {
  display: flex;
  gap: 6px;
}

.badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.badge.template {
  background: #dcfce7;
  color: #166534;
}

.badge.builtin {
  background: #dbeafe;
  color: #0369a1;
}

.badge.rec-badge.idle {
  background: #f1f5f9;
  color: #64748b;
}

.badge.rec-badge.ai {
  background: #f0fdf4;
  color: #166534;
}

.badge.rec-badge.local {
  background: #fef3c7;
  color: #92400e;
}

.badge.rec-badge.failed {
  background: #fee2e2;
  color: #b91c1c;
}

.rule-name {
  font-size: 14px;
  color: #374151;
  margin-bottom: 4px;
}

.rule-template {
  font-size: 12px;
  color: #64748b;
}

.rule-detail {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  min-height: 400px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.detail-header h3 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: #1f2937;
}

.detail-actions {
  display: flex;
  gap: 12px;
}

.error-box {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 12px 16px;
  color: #b91c1c;
  margin-bottom: 24px;
}

.binding-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 24px;
}

.binding-card {
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
}

.binding-card.recommended {
  border-color: #bbf7d0;
  background: #f7fef9;
}

.card-header {
  padding: 16px 20px;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.binding-card.recommended .card-header {
  background: #f0fdf4;
  border-bottom-color: #bbf7d0;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.ai-tag {
  font-size: 16px;
}

.card-content {
  padding: 20px;
}

.binding-type {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 16px;
}

.source-tag {
  font-size: 12px;
  color: #166534;
  background: #dcfce7;
  padding: 2px 8px;
  border-radius: 4px;
  margin-left: 8px;
}

.waiting-rec,
.no-params {
  font-size: 14px;
  color: #94a3b8;
}

.params-list {
  margin-bottom: 16px;
}

.param-item {
  display: grid;
  grid-template-columns: 120px 1fr;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}

.param-key {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}

.param-value {
  font-size: 13px;
  color: #1f2937;
  word-break: break-word;
}

.rec-explanation {
  font-size: 13px;
  color: #374151;
  line-height: 1.6;
}

.apply-section {
  text-align: center;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.no-selection {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  color: #94a3b8;
}

.no-selection-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.template-matrix {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 32px;
}

.template-matrix h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 20px;
  color: #1f2937;
}

.matrix-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.matrix-item {
  padding: 16px;
  background: #f8fafc;
  border-radius: 10px;
}

.matrix-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.template-code {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.template-count {
  font-size: 12px;
  color: #64748b;
}

.progress-bar {
  height: 8px;
  background: #e5e7eb;
  border-radius: 999px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  transition: width 0.5s ease;
}

.page-actions {
  display: flex;
  gap: 16px;
  justify-content: flex-end;
}
</style>
