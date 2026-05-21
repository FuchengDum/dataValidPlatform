<template>
  <div class="report-page">
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
        <span @click="$router.push('/findings')">异常分析</span>
        <span class="separator">/</span>
        <span class="current">报告导出</span>
      </div>
      <h1 class="page-title">报告导出</h1>
      <p class="page-desc">生成并导出校验报告</p>
    </div>

    <div v-if="!store.state.job" class="no-data">
      <div class="no-data-icon">📋</div>
      <h3>请先执行数据校验</h3>
      <p>在导出报告之前，需要先执行数据校验</p>
      <button class="primary-btn" @click="$router.push('/validation')">
        前往校验
      </button>
    </div>

    <div v-else class="report-content">
      <div class="report-preview">
        <div class="preview-header">
          <h2>校验报告预览</h2>
        </div>
        <div class="preview-content">
          <div class="report-summary">
            <div class="summary-item">
              <span class="summary-label">业务表数</span>
              <span class="summary-value">{{ store.state.dataset?.businessTableCount ?? 0 }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">规则数</span>
              <span class="summary-value">{{ store.state.rules.length }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">异常总数</span>
              <span class="summary-value">{{ store.state.summary?.findingCount ?? 0 }}</span>
            </div>
            <div class="summary-item danger">
              <span class="summary-label">严重异常</span>
              <span class="summary-value">{{ store.state.summary?.criticalCount ?? 0 }}</span>
            </div>
            <div class="summary-item warning">
              <span class="summary-label">警告异常</span>
              <span class="summary-value">{{ store.state.summary?.warningCount ?? 0 }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">校验耗时</span>
              <span class="summary-value">{{ store.state.summary?.durationMillis ?? 0 }} ms</span>
            </div>
          </div>

          <div class="report-section">
            <h3>📊 概览</h3>
            <p>本次校验共检查 {{ store.state.rules.length }} 条规则，覆盖 {{ store.state.dataset?.businessTableCount }} 个业务表。</p>
            <p>共发现 {{ store.state.summary?.findingCount ?? 0 }} 条异常，其中严重异常 {{ store.state.summary?.criticalCount ?? 0 }} 条，警告异常 {{ store.state.summary?.warningCount ?? 0 }} 条。</p>
          </div>

          <div class="report-section">
            <h3>⚠️ 异常分布</h3>
            <div class="distribution-cards">
              <div class="dist-card danger">
                <div class="dist-value">{{ store.state.summary?.criticalCount ?? 0 }}</div>
                <div class="dist-label">严重异常</div>
              </div>
              <div class="dist-card warning">
                <div class="dist-value">{{ store.state.summary?.warningCount ?? 0 }}</div>
                <div class="dist-label">警告异常</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="export-section">
        <div class="export-header">
          <h3>导出选项</h3>
        </div>
        <div class="export-options">
          <div class="export-option">
            <div class="option-icon">📄</div>
            <div class="option-info">
              <div class="option-title">Markdown 格式</div>
              <div class="option-desc">标准的 Markdown 文档，易于阅读和编辑</div>
            </div>
            <button class="primary-btn" @click="exportReport('MARKDOWN')" :disabled="store.state.loading">
              导出
            </button>
          </div>
          <div class="export-option coming-soon">
            <div class="option-icon">📊</div>
            <div class="option-info">
              <div class="option-title">Excel 格式</div>
              <div class="option-desc">表格格式，便于进一步数据分析</div>
            </div>
            <button class="secondary-btn" disabled>
              即将推出
            </button>
          </div>
          <div class="export-option coming-soon">
            <div class="option-icon">📝</div>
            <div class="option-info">
              <div class="option-title">PDF 格式</div>
              <div class="option-desc">打印格式，适合正式报告</div>
            </div>
            <button class="secondary-btn" disabled>
              即将推出
            </button>
          </div>
        </div>
      </div>

      <div class="page-actions">
        <button class="secondary-btn" @click="$router.push('/findings')">
          返回异常分析
        </button>
        <button class="link-btn" @click="$router.push('/')">
          开始新的校验
        </button>
      </div>
    </div>

    <StatusMessage />
  </div>
</template>

<script setup>
import store from '../store'
import { createReport, reportDownloadUrl } from '../api/client'
import StatusMessage from '../components/StatusMessage.vue'

async function exportReport(format) {
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    const result = await createReport(store.state.job.jobId, format)
    window.open(reportDownloadUrl(result.reportId), '_blank')
    store.actions.setMessage('报告已生成并开始下载')
  } catch (err) {
    store.actions.setError(err.message || '报告生成失败')
  } finally {
    store.actions.setLoading(false)
  }
}
</script>

<style scoped>
.report-page {
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
  height: 40px;
  padding: 0px 32px;
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
  padding: 12px 32px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.report-content {
  display: grid;
  gap: 24px;
}

.report-preview {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.preview-header {
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.preview-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: #fff;
}

.preview-content {
  padding: 24px;
}

.report-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.summary-item {
  padding: 16px;
  background: #f8fafc;
  border-radius: 10px;
  text-align: center;
}

.summary-item.danger {
  background: #fef2f2;
}

.summary-item.warning {
  background: #fefce8;
}

.summary-label {
  display: block;
  font-size: 14px;
  color: #64748b;
  margin-bottom: 8px;
}

.summary-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
}

.summary-item.danger .summary-value {
  color: #ef4444;
}

.summary-item.warning .summary-value {
  color: #f59e0b;
}

.report-section {
  margin-bottom: 28px;
}

.report-section h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 16px;
  color: #1f2937;
}

.report-section p {
  font-size: 15px;
  color: #374151;
  line-height: 1.8;
  margin: 8px 0;
}

.distribution-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.dist-card {
  padding: 24px;
  border-radius: 12px;
  text-align: center;
  border: 2px solid #e5e7eb;
}

.dist-card.danger {
  border-color: #fecaca;
  background: #fef2f2;
}

.dist-card.warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.dist-value {
  font-size: 48px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}

.dist-card.danger .dist-value {
  color: #ef4444;
}

.dist-card.warning .dist-value {
  color: #f59e0b;
}

.dist-label {
  font-size: 16px;
  color: #64748b;
}

.export-section {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.export-header {
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.export-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: #1f2937;
}

.export-options {
  padding: 24px;
  display: grid;
  gap: 16px;
}

.export-option {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 10px;
  border: 2px solid #e5e7eb;
  transition: all 0.3s ease;
}

.export-option:not(.coming-soon):hover {
  border-color: #667eea;
}

.option-icon {
  font-size: 40px;
}

.option-info {
  flex: 1;
}

.option-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.option-desc {
  font-size: 14px;
  color: #64748b;
}

.page-actions {
  display: flex;
  gap: 16px;
  justify-content: flex-end;
  align-items: center;
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
