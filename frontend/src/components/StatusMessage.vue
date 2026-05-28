<template>
  <div v-if="store.state.message || store.state.error" class="status-bar">
    <div v-if="store.state.message" class="message">
      <span class="icon">✓</span>
      <span>{{ store.state.message }}</span>
    </div>
    <div v-if="store.state.error" @click="closeMes()" class="error">
      <span class="icon">✕</span>
      <span>{{ store.state.error }}</span>
    </div>
  </div>
</template>

<script setup>
import store from '../store'

function closeMes() {
  console.log('closeMes');
  store.actions.setError('');
}
</script>

<style scoped>
.status-bar {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message,
.error {
  padding: 14px 20px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  animation: slideIn 0.3s ease;
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.message {
  background: #dcfce7;
  color: #166534;
  border: 1px solid #86efac;
}

.error {
  cursor: default;
  background: #fee2e2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}

.icon {
  font-size: 18px;
}
</style>
