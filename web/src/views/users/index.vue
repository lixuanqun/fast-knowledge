<template>
  <div class="page-container">
    <PageHeader title="用户管理" subtitle="管理系统用户账号与权限">
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon class="btn-icon"><Plus /></el-icon>
          新建用户
        </el-button>
      </template>
    </PageHeader>

    <el-card class="list-card" shadow="never">
      <el-table v-loading="isLoading" :data="pagedUsers" stripe>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="显示名" width="140" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(userRow(row))">编辑</el-button>
            <el-button link class="reset-link" @click="openResetPwd(userRow(row))">重置密码</el-button>
            <el-button link type="danger" @click="handleDelete(userRow(row))">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="filteredUsers.length" class="table-footer">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="filteredUsers.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          small
        />
      </div>
    </el-card>

    <el-dialog
      v-model="showForm"
      :title="editing ? '编辑用户' : '新建用户'"
      width="480px"
      destroy-on-close
      align-center
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item v-if="!editing" label="用户名" prop="username" required>
          <el-input v-model="form.username" placeholder="字母、数字、下划线" />
        </el-form-item>
        <el-form-item v-if="!editing" label="密码" prop="password" required>
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName" required>
          <el-input v-model="form.displayName" placeholder="请输入显示名" />
        </el-form-item>
        <el-form-item label="角色" prop="role" required>
          <el-select v-model="form.role" style="width:100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          {{ editing ? '保存' : '确定' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { KbUser } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import { formatDateTime } from '@/utils/format'
import {
  useCreateUserMutation,
  useDeleteUserMutation,
  useResetUserPasswordMutation,
  useUpdateUserMutation,
  useUsersQuery
} from '@/composables/queries/useUsers'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const { data: usersData, isLoading } = useUsersQuery()
const createMutation = useCreateUserMutation()
const updateMutation = useUpdateUserMutation()
const deleteMutation = useDeleteUserMutation()
const resetPwdMutation = useResetUserPasswordMutation()

const page = ref(1)
const pageSize = ref(10)
const saving = computed(
  () => createMutation.isPending.value || updateMutation.isPending.value
)
const showForm = ref(false)
const editing = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
  displayName: '',
  role: 'USER',
  enabled: true
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '至少 6 位', trigger: 'blur' }
  ],
  displayName: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const filteredUsers = computed(() => usersData.value || [])
const pagedUsers = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredUsers.value.slice(start, start + pageSize.value)
})

function roleLabel(role: string) {
  return role === 'ADMIN' ? 'ADMIN' : 'USER'
}

function roleTagType(role: string) {
  return role === 'ADMIN' ? 'danger' : 'info'
}

function userRow(row: unknown) {
  return row as KbUser
}

function openCreate() {
  editing.value = false
  editingId.value = undefined
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.role = 'USER'
  form.enabled = true
  showForm.value = true
}

function openEdit(user: KbUser) {
  editing.value = true
  editingId.value = user.id
  form.displayName = user.displayName
  form.role = user.role
  form.enabled = user.status === 1
  showForm.value = true
}

async function openResetPwd(user: KbUser) {
  const { value } = await ElMessageBox.prompt(`为用户 ${user.username} 设置新密码`, '重置密码', {
    inputType: 'password',
    inputPlaceholder: '至少 6 位'
  })
  if (!value || value.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  try {
    await resetPwdMutation.mutateAsync({ id: user.id, newPassword: value })
    ElMessage.success('密码已重置')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editing.value && editingId.value != null) {
      await updateMutation.mutateAsync({
        id: editingId.value,
        data: {
          displayName: form.displayName,
          role: form.role,
          status: form.enabled ? 1 : 0
        }
      })
      ElMessage.success('已保存')
    } else {
      await createMutation.mutateAsync({
        username: form.username,
        password: form.password,
        displayName: form.displayName,
        role: form.role
      })
      ElMessage.success('用户已创建')
    }
    showForm.value = false
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleDelete(user: KbUser) {
  await ElMessageBox.confirm(`确认删除用户 ${user.username}？`, '警告', { type: 'warning' })
  try {
    await deleteMutation.mutateAsync(user.id)
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
  background: $fk-card-bg;
}

.btn-icon {
  margin-right: 4px;
}

.reset-link {
  color: $fk-warning !important;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
