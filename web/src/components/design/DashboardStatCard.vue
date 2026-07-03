<template>
  <el-card shadow="hover" class="stat-card" :class="tone">
    <div class="stat-card__body">
      <div class="stat-card__icon" :class="`is-${tone}`">
        <el-icon :size="24"><component :is="icon" /></el-icon>
      </div>
      <div class="stat-card__value">{{ value }}</div>
      <div class="stat-card__label">{{ label }}</div>
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

<style scoped lang="scss">
.stat-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.stat-card__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 8px 4px;
}

.stat-card__icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.stat-card__icon.is-primary {
  background: $fk-primary-light;
  color: $fk-primary;
}

.stat-card__icon.is-success {
  background: $fk-success-bg;
  color: $fk-success;
}

.stat-card__icon.is-danger {
  background: $fk-danger-bg;
  color: $fk-danger;
}

.stat-card__icon.is-warning {
  background: $fk-warning-bg;
  color: $fk-warning;
}

.stat-card__value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  color: $fk-text-primary;
}

.stat-card.is-success .stat-card__value {
  color: $fk-success;
}

.stat-card.is-danger .stat-card__value {
  color: $fk-danger;
}

.stat-card.is-warning .stat-card__value {
  color: $fk-warning;
}

.stat-card__label {
  margin-top: 6px;
  font-size: 13px;
  color: $fk-text-secondary;
}
</style>
