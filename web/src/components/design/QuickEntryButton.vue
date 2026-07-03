<template>
  <button
    type="button"
    :class="['quick-btn', { 'is-primary': primary }]"
    @click="$router.push(path)"
  >
    <el-icon :size="16"><component :is="icon" /></el-icon>
    <span>{{ label }}</span>
  </button>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  path: string
  label: string
  icon: Component
  primary?: boolean
}>()
</script>

<style scoped lang="scss">
.quick-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  height: 44px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
  border: 1px solid $fk-primary;
  background: $fk-surface-muted;
  color: $fk-primary;
}

.quick-btn:hover {
  background: $fk-primary-light;
}

.quick-btn.is-primary {
  background: $fk-primary;
  color: #fff;
  border-color: $fk-primary;
}

.quick-btn.is-primary:hover {
  background: $fk-primary-hover;
  border-color: $fk-primary-hover;
}
</style>
