<template>
  <div class="page-header">
    <div class="page-header__main">
      <el-button v-if="showBack" link class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <div>
        <h2 class="page-header__title">{{ title }}</h2>
        <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

defineProps<{
  title: string
  subtitle?: string
  showBack?: boolean
}>()

const router = useRouter()

function goBack() {
  router.back()
}
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header__main {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.page-header__title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: $fk-text-primary;
}

.page-header__subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: $fk-text-secondary;
}

.page-header__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.back-btn {
  margin-top: 4px;
  padding-left: 0;
}
</style>
