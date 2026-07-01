<template>
  <div class="page-container">
    <PageHeader title="智能检索" subtitle="基于 Lucene 混合检索（向量 + 关键词）" />

    <el-card class="search-form-card">
      <el-form label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="知识库">
              <KbSelect v-model="kbId" width="100%" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="关键词">
              <el-input
                v-model="query"
                placeholder="输入检索内容，回车搜索"
                clearable
                @keyup.enter="handleSearch"
              >
                <template #append>
                  <el-button :loading="loading" type="primary" @click="handleSearch">检索</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="返回条数">
              <el-input-number v-model="topK" :min="1" :max="30" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <div v-loading="loading" class="result-area">
      <template v-if="searched">
        <p class="result-summary">共找到 {{ hits.length }} 条相关片段</p>
        <el-card v-for="(hit, i) in hits" :key="i" class="hit-card" shadow="hover">
          <div class="hit-card__header">
            <strong>{{ hit.documentTitle }}</strong>
            <el-tag size="small" type="warning">得分 {{ hit.score.toFixed(3) }}</el-tag>
          </div>
          <p class="hit-card__content">{{ hit.content }}</p>
        </el-card>
        <EmptyState v-if="!hits.length" description="未找到相关内容，请换关键词试试" />
      </template>
      <EmptyState v-else description="选择知识库并输入关键词开始检索" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import KbSelect from '@/components/KbSelect.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useSearchMutation } from '@/composables/queries/useSearch'
import { ElMessage } from 'element-plus'

const kbId = ref<number>()
const query = ref('')
const topK = ref(8)
const searched = ref(false)

const searchMutation = useSearchMutation()
const loading = computed(() => searchMutation.isPending.value)
const hits = computed(() => searchMutation.data.value || [])

async function handleSearch() {
  if (!kbId.value) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!query.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searched.value = true
  try {
    await searchMutation.mutateAsync({
      kbId: kbId.value,
      query: query.value.trim(),
      topK: topK.value
    })
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}
</script>

<style scoped>
.search-form-card { margin-bottom: 16px; border-radius: 10px; }
.result-summary { color: #888; font-size: 13px; margin: 0 0 12px; }
.hit-card { margin-bottom: 12px; border-radius: 10px; }
.hit-card__header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 8px; }
.hit-card__content { margin: 0; white-space: pre-wrap; color: #666; line-height: 1.7; font-size: 14px; }
</style>
