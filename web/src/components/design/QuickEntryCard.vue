<template>
  <button type="button" class="quick-entry" @click="$router.push(path)">
    <div class="quick-entry__icon" :class="`is-${tone}`">
      <el-icon :size="20"><component :is="icon" /></el-icon>
    </div>
    <div class="quick-entry__text">
      <div class="quick-entry__title">{{ title }}</div>
      <div v-if="description" class="quick-entry__desc">{{ description }}</div>
    </div>
    <el-icon class="quick-entry__arrow"><ArrowRight /></el-icon>
  </button>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'

defineProps<{
  path: string
  title: string
  description?: string
  icon: Component
  tone?: 'primary' | 'success' | 'warning' | 'danger'
}>()
</script>

<style scoped lang="scss">
.quick-entry {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid $fk-border;
  border-radius: 10px;
  background: $fk-surface-muted;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.quick-entry:hover {
  border-color: $fk-primary;
  background: $fk-primary-light;
}

.quick-entry__icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.quick-entry__icon.is-primary {
  background: $fk-primary-light;
  color: $fk-primary;
}

.quick-entry__icon.is-success {
  background: $fk-success-bg;
  color: $fk-success;
}

.quick-entry__icon.is-warning {
  background: $fk-warning-bg;
  color: $fk-warning;
}

.quick-entry__icon.is-danger {
  background: $fk-danger-bg;
  color: $fk-danger;
}

.quick-entry__text {
  flex: 1;
  min-width: 0;
}

.quick-entry__title {
  font-size: 14px;
  font-weight: 600;
  color: $fk-text-primary;
}

.quick-entry__desc {
  margin-top: 4px;
  font-size: 12px;
  color: $fk-text-secondary;
  line-height: 1.4;
}

.quick-entry__arrow {
  color: $fk-text-secondary;
  flex-shrink: 0;
}
</style>
