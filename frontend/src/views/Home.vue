<template>
  <div class="home-page">
    <div class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">业务数据准确性验证工具</h1>
        <p class="hero-subtitle">Excel 数据导入 · 智能规则校验 · 异常证据链分析 · 报告一键导出</p>
      </div>
    </div>

    <div class="features-section">
      <div class="features-grid">
        <div class="feature-card" @click="$router.push('/upload')">
          <div class="feature-icon">📁</div>
          <h3>数据上传</h3>
          <p>快速导入 Excel 数据，自动解析业务表结构</p>
          <div class="feature-step">1</div>
        </div>

        <div class="feature-card" @click="$router.push('/rules')">
          <div class="feature-icon">⚙️</div>
          <h3>规则配置</h3>
          <p>AI 智能推荐规则模板，灵活配置校验规则</p>
          <div class="feature-step">2</div>
        </div>

        <div class="feature-card" @click="$router.push('/validation')">
          <div class="feature-icon">✅</div>
          <h3>数据校验</h3>
          <p>一键启动全量校验，实时查看校验进度</p>
          <div class="feature-step">3</div>
        </div>

        <div class="feature-card" @click="$router.push('/findings')">
          <div class="feature-icon">🔍</div>
          <h3>异常分析</h3>
          <p>深度分析异常原因，查看完整证据链</p>
          <div class="feature-step">4</div>
        </div>

        <div class="feature-card" @click="$router.push('/report')">
          <div class="feature-icon">📊</div>
          <h3>报告导出</h3>
          <p>生成专业校验报告，支持多种格式下载</p>
          <div class="feature-step">5</div>
        </div>
      </div>
    </div>

    <div class="quick-actions">
      <button class="primary-btn large" @click="$router.push('/upload')">
        <span class="btn-icon">🚀</span>
        开始使用
      </button>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.home-page {
  padding: 40px 28px;
  max-width: 1200px;
  margin: 0 auto;
}

.hero-section {
  text-align: center;
  padding: 60px 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  margin-bottom: 48px;
  box-shadow: 0 10px 40px rgba(102, 126, 234, 0.2);
}

.hero-content {
  color: #fff;
}

.hero-title {
  font-size: 42px;
  font-weight: 700;
  margin: 0 0 16px;
  letter-spacing: -0.5px;
}

.hero-subtitle {
  font-size: 18px;
  opacity: 0.95;
  margin: 0;
}

.features-section {
  margin-bottom: 48px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
}

.feature-card {
  background: #fff;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  padding: 32px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.feature-card:hover {
  border-color: #667eea;
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(102, 126, 234, 0.15);
}

.feature-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.feature-card h3 {
  font-size: 20px;
  margin: 0 0 12px;
  color: #1f2937;
}

.feature-card p {
  font-size: 14px;
  color: #64748b;
  margin: 0;
  line-height: 1.6;
}

.feature-step {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
}

.quick-actions {
  text-align: center;
}

.primary-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 16px 48px;
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.primary-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 12px 32px rgba(102, 126, 234, 0.3);
}

.primary-btn.large {
  padding: 12px 56px;
  font-size: 20px;
  height: auto;
}

.btn-icon {
  font-size: 24px;
}
</style>
