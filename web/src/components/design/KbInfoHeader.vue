<template>
  <el-card class="kb-info-header fk-card" shadow="never">
    <div class="kb-info-header__body">
      <div class="kb-info-header__icon">
        <el-icon :size="28"><Collection /></el-icon>
      </div>
      <div class="kb-info-header__main">
        <div class="kb-info-header__title-row">
          <h2 class="kb-info-header__title">{{ name }}</h2>
          <el-tag :type="visibility === 'PUBLIC' ? 'success' : 'info'" size="small" effect="light">
            {{ visibilityLabel(visibility) }}
          </el-tag>
        </div>
        <p v-if="description" class="kb-info-header__desc">{{ description }}</p>
        <div class="kb-info-header__meta">
          <span>文档数量：{{ documentCount }}</span>
          <span class="meta-sep">·</span>
          <span>检索 Top K：{{ searchTopK }}</span>
          <span class="meta-sep">·</span>
          <span>混合检索：PgVector HYBRID</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { Collection } from '@element-plus/icons-vue'
import { visibilityLabel } from '@/utils/format'

defineProps<{
  name: string
  description?: string
  visibility: string
  documentCount: number
  searchTopK: number
}>()
</script>

<style scoped lang="scss">
.kb-info-header {
  margin-bottom: 16px;
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.kb-info-header__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.kb-info-header__icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: $fk-primary-light;
  color: $fk-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kb-info-header__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.kb-info-header__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: $fk-text-primary;
}

.kb-info-header__desc {
  margin: 8px 0 0;
  font-size: 14px;
  color: $fk-text-regular;
  line-height: 1.6;
}

.kb-info-header__meta {
  margin-top: 10px;
  font-size: 13px;
  color: $fk-text-secondary;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.meta-sep {
  opacity: 0.6;
}
</style>
