<template>
  <div class="page-container">
    <PageHeader title="API Key" subtitle="服务账号密钥，用于系统集成调用（Header: X-API-Key）">
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon class="btn-icon"><Plus /></el-icon>
          创建密钥
        </el-button>
      </template>
    </PageHeader>

    <el-card class="list-card" shadow="never">
      <el-table v-loading="isLoading" :data="keys" stripe>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="keyPrefix" label="前缀" width="120" />
        <el-table-column prop="userId" label="绑定用户" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '有效' : '已吊销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近使用" width="180">
          <template #default="{ row }">
            {{ row.lastUsedAt ? formatDateTime(row.lastUsedAt) : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              link
              type="danger"
              @click="handleRevoke(row.id)"
            >
              吊销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!isLoading && !keys.length" variant="docs" description="暂无 API Key" />
    </el-card>

    <el-dialog v-model="showCreate" title="创建 API Key" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 MES 集成" />
        </el-form-item>
        <el-form-item label="绑定用户" prop="userId">
          <el-select v-model="form.userId" filterable placeholder="选择用户" style="width:100%">
            <el-option
              v-for="u in users"
              :key="u.id"
              :label="`${u.displayName || u.username} (${u.username})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRawKey" title="请妥善保存密钥" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon title="密钥仅显示一次，关闭后无法再次查看" />
      <el-input v-model="rawKey" readonly class="raw-key-input">
        <template #append>
          <el-button @click="copyRawKey">复制</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="showRawKey = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatDateTime } from '@/utils/format'
import { useQuery, useQueryClient, useMutation } from '@tanstack/vue-query'
import { createApiKey, listApiKeys, revokeApiKey } from '@/api/api-keys'
import { listUsers } from '@/api/users'
import { queryKeys } from '@/lib/query-keys'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const queryClient = useQueryClient()
const showCreate = ref(false)
const showRawKey = ref(false)
const rawKey = ref('')
const creating = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  userId: undefined as number | undefined
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }]
}

const { data, isLoading } = useQuery({
  queryKey: queryKeys.apiKeys.all,
  queryFn: async () => {
    const res = await listApiKeys()
    return res.data || []
  }
})

const { data: usersData } = useQuery({
  queryKey: queryKeys.users.all,
  queryFn: async () => {
    const res = await listUsers()
    return res.data || []
  }
})

const keys = computed(() => data.value || [])
const users = computed(() => usersData.value || [])

const revokeMutation = useMutation({
  mutationFn: (id: number) => revokeApiKey(id),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.apiKeys.all })
    ElMessage.success('已吊销')
  }
})

function openCreate() {
  form.name = ''
  form.userId = undefined
  showCreate.value = true
}

async function submitCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.userId) return
  creating.value = true
  try {
    const res = await createApiKey({ name: form.name, userId: form.userId })
    showCreate.value = false
    rawKey.value = res.data.apiKey
    showRawKey.value = true
    queryClient.invalidateQueries({ queryKey: queryKeys.apiKeys.all })
    ElMessage.success('创建成功')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  } finally {
    creating.value = false
  }
}

async function handleRevoke(id: number) {
  await ElMessageBox.confirm('吊销后该密钥将立即失效，确认继续？', '吊销 API Key', {
    type: 'warning'
  })
  revokeMutation.mutate(id)
}

async function copyRawKey() {
  try {
    await navigator.clipboard.writeText(rawKey.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}
</script>

<style scoped lang="scss">
.list-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
}

.btn-icon {
  margin-right: 4px;
}

.raw-key-input {
  margin-top: 16px;
}
</style>
