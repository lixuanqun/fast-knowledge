<template>
  <el-card shadow="hover" class="stat-card" :class="tone">
    <div class="stat-card__body">
      <div class="stat-card__icon" :class="`is-${tone}`">
        <el-icon :size="22"><component :is="icon" /></el-icon>
      </div>
      <div class="stat-card__content">
        <div class="stat-card__value">{{ value }}</div>
        <div class="stat-card__label">{{ label }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  label: string
  value: number | string
  icon: Component
  tone?: 'primary' | 'success' | 'danger' | 'warning'
}>()
</script>

<style scoped>
.stat-card {
  border-radius: 10px;
  border: 1px solid var(--fk-border-color, #e4e7ed);
}

.stat-card__body {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-card__icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card__icon.is-primary {
  background: #ecf5ff;
  color: #409eff;
}

.stat-card__icon.is-success {
  background: #f0f9eb;
  color: #67c23a;
}

.stat-card__icon.is-danger {
  background: #fef0f0;
  color: #f56c6c;
}

.stat-card__icon.is-warning {
  background: #fdf6ec;
  color: #e6a23c;
}

.stat-card__value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  color: #303133;
}

.stat-card.is-success .stat-card__value {
  color: #67c23a;
}

.stat-card.is-danger .stat-card__value {
  color: #f56c6c;
}

.stat-card.is-warning .stat-card__value {
  color: #e6a23c;
}

.stat-card__label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}
</style>
