<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :loading="isLoading"
    :style="{ width }"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
  </el-select>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useKbsQuery } from '@/composables/queries/useKbs'

const props = withDefaults(defineProps<{
  modelValue?: number
  placeholder?: string
  clearable?: boolean
  width?: string
  autoDefault?: boolean
}>(), {
  placeholder: '选择知识库',
  width: '200px',
  autoDefault: true
})

const emit = defineEmits<{ 'update:modelValue': [value?: number] }>()

const { data, isLoading } = useKbsQuery()
const kbs = computed(() => data.value || [])

watch(
  kbs,
  list => {
    if (props.autoDefault && props.modelValue == null && list.length) {
      emit('update:modelValue', list[0].id)
    }
  },
  { immediate: true }
)

defineExpose({ kbs })
</script>
