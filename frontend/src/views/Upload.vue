<template>
  <div class="upload-page">
    <div class="page-header">
      <div class="breadcrumb">
        <span @click="$router.push('/')">首页</span>
        <span class="separator">/</span>
        <span class="current">数据上传</span>
      </div>
      <h1 class="page-title">数据上传</h1>
      <p class="page-desc">上传您的 Excel 数据文件，系统将自动解析业务表结构</p>
    </div>

    <div class="upload-container">
      <div class="upload-area" :class="{ 'has-file': store.state.dataset, 'dragover': isDragover }"
           @dragover.prevent="isDragover = true"
           @dragleave.prevent="isDragover = false"
           @drop.prevent="handleDrop">
        <div v-if="!store.state.dataset" class="upload-content">
          <div class="upload-icon">📤</div>
          <h3>点击或拖拽文件到此处上传</h3>
          <p>支持 .xlsx 格式文件</p>
          <label class="upload-btn">
            <input type="file" accept=".xlsx" @change="onFileChange" :disabled="store.state.loading">
            选择文件
          </label>
        </div>
        <div v-else class="file-info">
          <div class="file-icon">📊</div>
          <div class="file-details">
            <h3>文件已上传</h3>
            <p>业务表数量: {{ store.state.dataset.businessTableCount }}</p>
            <p>规则数量: {{ store.state.dataset.ruleCount }}</p>
          </div>
          <button class="remove-btn" @click="removeFile" :disabled="store.state.loading">
            重新上传
          </button>
        </div>
      </div>

      <div v-if="store.state.dataset" class="next-steps">
        <div class="step-item" :class="{ active: true }">
          <div class="step-number">1</div>
          <div class="step-content">
            <h4>数据上传</h4>
            <p>已完成</p>
          </div>
          <div class="step-check">✓</div>
        </div>
        <div class="step-arrow">→</div>
        <div class="step-item">
          <div class="step-number">2</div>
          <div class="step-content">
            <h4>规则配置</h4>
            <p>配置校验规则</p>
          </div>
        </div>
        <div class="step-arrow">→</div>
        <div class="step-item">
          <div class="step-number">3</div>
          <div class="step-content">
            <h4>数据校验</h4>
            <p>开始校验</p>
          </div>
        </div>
      </div>

      <div v-if="store.state.dataset" class="action-buttons">
        <button class="secondary-btn" @click="$router.push('/rules')">
          前往规则配置
        </button>
        <button class="primary-btn" @click="$router.push('/validation')">
          直接开始校验
        </button>
      </div>
    </div>

    <StatusMessage />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import store from '../store'
import { uploadWorkbook, fetchRules } from '../api/client'
import StatusMessage from '../components/StatusMessage.vue'

const router = useRouter()
const isDragover = ref(false)

async function onFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  await processFile(file)
}

async function handleDrop(event) {
  isDragover.value = false
  const file = event.dataTransfer.files?.[0]
  if (!file) return
  await processFile(file)
}

async function processFile(file) {
  store.actions.setLoading(true)
  store.actions.setMessage('')
  store.actions.setError('')
  
  try {
    const result = await uploadWorkbook(file)
    store.actions.setDataset(result)
    store.actions.setMessage('Excel 导入完成')
    
    const rules = await fetchRules(result.datasetId)
    store.actions.setRules(rules)
  } catch (err) {
    store.actions.setError(err.message || '上传失败')
  } finally {
    store.actions.setLoading(false)
  }
}

function removeFile() {
  store.actions.clearState()
}
</script>

<style scoped>
.upload-page {
  padding: 32px 28px;
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 40px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #64748b;
  margin-bottom: 16px;
}

.breadcrumb span:first-child {
  cursor: pointer;
}

.breadcrumb span:first-child:hover {
  color: #2563eb;
}

.breadcrumb .current {
  color: #1f2937;
  font-weight: 500;
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

.upload-container {
  background: #fff;
  border-radius: 16px;
  padding: 48px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

.upload-area {
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  padding: 60px 40px;
  text-align: center;
  transition: all 0.3s ease;
  background: #f8fafc;
}

.upload-area:hover,
.upload-area.dragover {
  border-color: #667eea;
  background: #f0f4ff;
}

.upload-area.has-file {
  border-style: solid;
  border-color: #22c55e;
  background: #f0fdf4;
}

.upload-icon {
  font-size: 64px;
  margin-bottom: 24px;
}

.upload-content h3 {
  font-size: 20px;
  margin: 0 0 8px;
  color: #1f2937;
}

.upload-content p {
  font-size: 14px;
  color: #64748b;
  margin: 0 0 24px;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  padding: 0 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.upload-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.3);
}

.upload-btn input {
  display: none;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 24px;
  text-align: left;
}

.file-icon {
  font-size: 64px;
}

.file-details {
  flex: 1;
}

.file-details h3 {
  font-size: 20px;
  margin: 0 0 12px;
  color: #166534;
}

.file-details p {
  font-size: 14px;
  color: #64748b;
  margin: 4px 0;
}

.remove-btn {
  height: 40px;
  padding: 0 24px;
  border: 2px solid #ef4444;
  background: #fff;
  color: #ef4444;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.remove-btn:hover {
  background: #fef2f2;
}

.next-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 48px;
  padding: 32px;
  background: #f8fafc;
  border-radius: 12px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  background: #fff;
  border-radius: 10px;
  border: 2px solid #e5e7eb;
}

.step-item.active {
  border-color: #22c55e;
  background: #f0fdf4;
}

.step-number {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
}

.step-item.active .step-number {
  background: #22c55e;
}

.step-content h4 {
  font-size: 14px;
  margin: 0 0 4px;
  color: #1f2937;
}

.step-content p {
  font-size: 12px;
  color: #64748b;
  margin: 0;
}

.step-check {
  font-size: 20px;
  color: #22c55e;
}

.step-arrow {
  font-size: 24px;
  color: #cbd5e1;
}

.action-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-top: 32px;
}

.primary-btn,
.secondary-btn {
  height: 48px;
  padding: 0 32px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.primary-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
}

.primary-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.3);
}

.secondary-btn {
  background: #fff;
  color: #374151;
  border: 2px solid #e5e7eb;
}

.secondary-btn:hover {
  border-color: #667eea;
  color: #667eea;
}
</style>
