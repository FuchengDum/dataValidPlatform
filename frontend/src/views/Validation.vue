<template>
  <div class="validation-page">
    <div class="page-header">
      <div class="breadcrumb">
        <span @click="$router.push('/')">首页</span>
        <span class="separator">/</span>
        <span @click="$router.push('/upload')">数据上传</span>
        <span class="separator">/</span>
        <span @click="$router.push('/rules')">规则配置</span>
        <span class="separator">/</span>
        <span class="current">数据校验</span>
      </div>
      <h1 class="page-title">数据校验</h1>
      <p class="page-desc">启动数据校验，实时查看校验结果</p>
    </div>

    <div v-if="!store.state.dataset" class="no-data">
      <div class="no-data-icon">📁</div>
      <h3>请先上传数据文件</h3>
      <p>在校验数据之前，需要先上传您的 Excel 数据文件</p>
      <button class="primary-btn" @click="$router.push('/upload')">
        前往上传
      </button>
    </div>

    <div v-else class="validation-content">
      <div class="start-section" v-if="!store.state.job">
        <div class="start-card">
          <div class="start-icon">▶️</div>
          <h2>准备就绪</h2>
          <p>已加载 {{ store.state.rules.length }} 条校验规则</p>
          <p>{{ store.state.dataset.businessTableCount }} 个业务表待校验</p>
          <button class="primary-btn large" @click="startValidation" :disabled="store.state.loading">
            <span v-if="store.state.loading">校验中...</span>
            <span v-else>开始校验</span>
          </button>
        </div>
      </div>

      <div v-else class="results-section">
        <div class="summary-cards">
          <div class="summary-card">
            <div class="card-icon">📊</div>
            <div class="card-content">
              <div class="card-label">异常总数</div>
              <div class="card-value">{{ store.state.summary?.findingCount ?? 0 }}</div>
            </div>
          </div>
          <div class="summary-card danger">
            <div class="card-icon">🔴</div>
            <div class="card-content">
              <div class="card-label">严重异常</div>
              <div class="card-value">{{ store.state.summary?.criticalCount ?? 0 }}</div>
            </div>
          </div>
          <div class="summary-card warning">
            <div class="card-icon">🟡</div>
            <div class="card-content">
              <div class="card-label">警告异常</div>
              <div class="card-value">{{ store.state.summary?.warningCount ?? 0 }}</div>
            </div>
          </div>
          <div class="summary-card">
            <div class="card-icon">⏱️</div>
            <div class="card-content">
              <div class="card-label">校验耗时</div>
              <div class="card-value">{{ store.state.summary?.durationMillis ?? 0 }} ms</div>
            </div>
          </div>
        </div>

        <div class="quick-stats">
          <div class="stat-box">
            <div class="stat-box-label">业务表</div>
            <div class="stat-box-value">{{ store.state.dataset.businessTableCount }}</div>
          </div>
          <div class="stat-box">
            <div class="stat-box-label">规则数</div>
            <div class="stat-box-value">{{ store.state.rules.length }}</div>
          </div>
        </div>

        <div class="actions-row">
          <button class="secondary-btn" @click="startValidation" :disabled="store.state.loading">
            重新校验
          </button>
          <button class="primary-btn" @click="$router.push('/findings')">
            查看异常详情
          </button>
        </div>
      </div>

      <div class="nav-links">
        <button class="link-btn" @click="$router.push('/rules')">
          ← 返回规则配置
        </button>
      </div>
    </div>

    <StatusMessage />
  </div>
</template>

<script setup>
import store from '../store'
import { startValidation as apiStartValidation, fetchSummary } from '../api/client'
import StatusMessage from '../components/StatusMessage.vue'

async function startValidation() {
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    const job = await apiStartValidation(store.state.dataset.datasetId, false)
    store.actions.setJob(job)
    store.actions.setMessage('校验完成')
    
    const summary = await fetchSummary(job.jobId)
    store.actions.setSummary(summary)
  } catch (err) {
    store.actions.setError(err.message || '校验失败')
  } finally {
    store.actions.setLoading(false)
  }
}
</script>

<style scoped>
.validation-page {
  padding: 32px 28px;
  max-width: 1100px;
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
  padding: 12px 32px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  height: 40px;
  /* height: 44px; */
  padding: 0px 32px;
}

.primary-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.3);
}

.primary-btn:disabled,
.secondary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  padding: 0 32px;
}

.primary-btn.large {
  padding: 9px 48px;
  font-size: 18px;
  height: 40px;
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
}

.secondary-btn:hover:not(:disabled) {
  border-color: #667eea;
  color: #667eea;
}

.start-section {
  display: flex;
  justify-content: center;
}

.start-card {
  background: #fff;
  border-radius: 16px;
  padding: 64px 48px;
  text-align: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  max-width: 500px;
  width: 100%;
}

.start-icon {
  font-size: 72px;
  margin-bottom: 24px;
}

.start-card h2 {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 12px;
  color: #1f2937;
}

.start-card p {
  font-size: 16px;
  color: #64748b;
  margin: 8px 0;
}

.results-section {
  margin-bottom: 32px;
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.summary-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.summary-card.danger {
  border-left: 4px solid #ef4444;
}

.summary-card.warning {
  border-left: 4px solid #f59e0b;
}

.card-icon {
  font-size: 40px;
}

.card-content {
  flex: 1;
}

.card-label {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 4px;
}

.card-value {
  font-size: 32px;
  font-weight: 700;
  color: #1f2937;
}

.summary-card.danger .card-value {
  color: #ef4444;
}

.summary-card.warning .card-value {
  color: #f59e0b;
}

.quick-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.stat-box {
  flex: 1;
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  text-align: center;
}

.stat-box-label {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 8px;
}

.stat-box-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
}

.actions-row {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.nav-links {
  display: flex;
  justify-content: flex-start;
}

.link-btn {
  background: none;
  border: none;
  color: #667eea;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  padding: 8px 0;
}

.link-btn:hover {
  text-decoration: underline;
}
</style>
