<template>
  <div class="app-container">
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo">
          <span class="logo-icon">🔍</span>
          <span class="logo-text">数据校验工具</span>
        </div>
        <p class="logo-subtitle">业务数据准确性验证</p>
      </div>
      
      <nav class="sidebar-nav">
        <router-link 
          v-for="item in navItems" 
          :key="item.path"
          :to="item.path" 
          class="nav-item"
          :class="{ active: $route.path === item.path }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-text">{{ item.label }}</span>
          <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
        </router-link>
      </nav>
      
      <div class="sidebar-footer">
        <div v-if="store.state.dataset" class="dataset-info">
          <div class="dataset-label">当前数据</div>
          <div class="dataset-details">
            <span class="dataset-stat">📊 {{ store.state.dataset.businessTableCount }} 表</span>
            <span class="dataset-stat">📋 {{ store.state.rules.length }} 规则</span>
          </div>
        </div>
        <div class="divider"></div>
        <button v-if="store.state.dataset" class="reset-btn" @click="resetAll">
          开始新校验
        </button>
      </div>
    </aside>
    
    <main class="main-content">
      <div class="content-inner">
        <router-view></router-view>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import store from './store'

const router = useRouter()

const navItems = computed(() => [
  { path: '/', icon: '🏠', label: '首页' },
  { path: '/upload', icon: '📤', label: '数据上传', badge: store.state.dataset ? '✓' : null },
  { path: '/rules', icon: '⚙️', label: '规则配置', badge: store.state.rules.length > 0 ? store.state.rules.length : null },
  { path: '/validation', icon: '✅', label: '数据校验', badge: store.state.job ? '✓' : null },
  { path: '/findings', icon: '⚠️', label: '异常分析', badge: store.state.findings.length > 0 ? store.state.findings.length : null },
  { path: '/report', icon: '📄', label: '报告导出' }
])

function resetAll() {
  store.actions.clearState()
  router.push('/')
}
</script>

<style scoped>
.app-container {
  display: flex;
  min-height: 100vh;
  background: #f5f7fb;
}

.sidebar {
  width: 260px;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  position: fixed;
  left: 0;
  top: 0;
  height: 100vh;
  z-index: 100;
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.1);
}

.sidebar-header {
  padding: 28px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.logo-icon {
  font-size: 32px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.logo-subtitle {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
}

.sidebar-nav {
  flex: 1;
  padding: 20px 16px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 10px;
  color: #94a3b8;
  text-decoration: none;
  margin-bottom: 6px;
  transition: all 0.2s ease;
  position: relative;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
}

.nav-item.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.3);
}

.nav-icon {
  font-size: 20px;
}

.nav-text {
  flex: 1;
  font-size: 15px;
  font-weight: 500;
}

.nav-badge {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.nav-item.active .nav-badge {
  background: rgba(255, 255, 255, 0.3);
}

.sidebar-footer {
  padding: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.dataset-info {
  margin-bottom: 16px;
}

.dataset-label {
  font-size: 12px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.dataset-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dataset-stat {
  font-size: 13px;
  color: #cbd5e1;
}

.divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
  margin: 16px 0;
}

.reset-btn {
  width: 100%;
  height: 40px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: transparent;
  color: #94a3b8;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.reset-btn:hover {
  border-color: #ef4444;
  color: #fecaca;
  background: rgba(239, 68, 68, 0.1);
}

.main-content {
  flex: 1;
  margin-left: 260px;
}

.content-inner {
  min-height: 100vh;
}
</style>
