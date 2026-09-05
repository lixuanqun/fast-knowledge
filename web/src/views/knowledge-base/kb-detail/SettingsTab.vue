<template>
  <div class="tab-section">
    <h3 class="tab-section__title">基本设置</h3>
    <el-form label-width="130px" class="settings-form">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" maxlength="50" show-word-limit />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="可见性">
        <el-radio-group v-model="form.visibility">
          <el-radio value="PRIVATE">私有</el-radio>
          <el-radio value="PUBLIC">公开</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <h3 class="tab-section__title section-gap">检索配置</h3>
    <el-form label-width="130px" class="settings-form">
      <el-form-item label="检索 Top K">
        <el-input-number v-model="form.searchTopK" :min="1" :max="20" />
        <div class="form-tip">检索与 RAG 返回的最大片段数（1–20）</div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="emit('save', { ...form })">
          保存设置
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{
  kb: { name: string; description?: string; visibility: string; searchTopK: number } | undefined
  saving?: boolean
}>()

const emit = defineEmits<{
  save: [settings: { name: string; description: string; visibility: string; searchTopK: number }]
}>()

const form = reactive({
  name: '',
  description: '',
  visibility: 'PRIVATE',
  searchTopK: 8
})

watch(
  () => props.kb,
  kb => {
    if (!kb) return
    form.name = kb.name
    form.description = kb.description || ''
    form.visibility = kb.visibility
    form.searchTopK = kb.searchTopK
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.tab-section {
  padding: 4px 0;
}

.tab-section__title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: $fk-text-primary;
}

.section-gap {
  margin-top: 24px;
}

.form-tip {
  font-size: 12px;
  color: $fk-text-secondary;
  margin-top: 4px;
}
</style>