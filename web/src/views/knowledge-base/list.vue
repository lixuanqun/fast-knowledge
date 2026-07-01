<template>
  <div class="page-container">
    <PageHeader title="知识库管理" subtitle="创建和管理知识库，上传文档并配置检索参数">
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon class="btn-icon"><Plus /></el-icon>
          新建知识库
        </el-button>
      </template>
    </PageHeader>

    <el-card class="list-card" shadow="never">
      <div class="filters">
        <el-input
          v-model="keyword"
          placeholder="搜索知识库名称或描述"
          clearable
          style="max-width:360px"
          :prefix-icon="Search"
        />
        <el-select
          v-model="workspaceFilter"
          placeholder="按工作区筛选"
          clearable
          style="width:200px"
        >
          <el-option v-for="ws in workspaces" :key="ws.id" :label="ws.name" :value="ws.id" />
        </el-select>
      </div>

      <el-table v-loading="isLoading" :data="pagedKbs" stripe>
        <el-table-column prop="name" label="名称" min-width="140">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/kbs/${row.id}`)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="180" />
        <el-table-column label="可见性" width="90">
          <template #default="{ row }">
            <el-tag :type="row.visibility === 'PUBLIC' ? 'success' : 'info'" size="small">
              {{ visibilityLabel(row.visibility) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检索 Top K" width="110" prop="searchTopK" align="center" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goSettings(row.id)">编辑</el-button>
            <el-button link type="primary" @click="$router.push(`/kbs/${row.id}`)">文档</el-button>
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState v-if="!isLoading && !filteredKbs.length" description="暂无知识库，点击右上角新建" />

      <div v-if="filteredKbs.length" class="table-footer">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="filteredKbs.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          small
        />
      </div>
    </el-card>

    <el-dialog v-model="showDialog" title="新建知识库" width="480px" destroy-on-close align-center>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="名称" prop="name" required>
          <el-input v-model="form.name" placeholder="请输入知识库名称" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识库描述（可选）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="可见性" prop="visibility" required>
          <el-radio-group v-model="form.visibility">
            <el-radio value="PRIVATE">私有（仅自己可见）</el-radio>
            <el-radio value="PUBLIC">公开（所有用户可见）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="createMutation.isPending.value" @click="handleCreate">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { visibilityLabel } from '@/utils/format'
import { useCreateKbMutation, useDeleteKbMutation, useKbsQuery } from '@/composables/queries/useKbs'
import { useWorkspacesQuery } from '@/composables/queries/useWorkspaces'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'

const router = useRouter()
const { data: kbs, isLoading } = useKbsQuery()
const { data: workspaces } = useWorkspacesQuery()
const createMutation = useCreateKbMutation()
const deleteMutation = useDeleteKbMutation()

const keyword = ref('')
const workspaceFilter = ref<number | undefined>()
const page = ref(1)
const pageSize = ref(10)
const showDialog = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '', visibility: 'PRIVATE' })
const rules: FormRules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }

const filteredKbs = computed(() => {
  let result = kbs.value || []
  if (workspaceFilter.value != null) {
    result = result.filter(k => k.workspaceId === workspaceFilter.value)
  }
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return result
  return result.filter(
    k => k.name.toLowerCase().includes(kw) || (k.description || '').toLowerCase().includes(kw)
  )
})

const pagedKbs = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredKbs.value.slice(start, start + pageSize.value)
})

watch([keyword, workspaceFilter], () => {
  page.value = 1
})

function goSettings(id: number) {
  router.push({ path: `/kbs/${id}`, query: { tab: 'settings' } })
}

function openCreate() {
  form.name = ''
  form.description = ''
  form.visibility = 'PRIVATE'
  showDialog.value = true
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await createMutation.mutateAsync(form)
    ElMessage.success('创建成功')
    showDialog.value = false
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确认删除该知识库及所有文档？', '警告', { type: 'warning' })
  try {
    await deleteMutation.mutateAsync(id)
    ElMessage.success('已删除')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}
</script>

<style scoped lang="scss">
.list-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.btn-icon {
  margin-right: 4px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
