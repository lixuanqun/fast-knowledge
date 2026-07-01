<template>
  <div class="page-container">
    <PageHeader title="用户管理" subtitle="管理系统用户账号、角色与状态（仅管理员）">
      <template #actions>
        <el-button type="primary" @click="openCreate">新建用户</el-button>
      </template>
    </PageHeader>

    <el-card class="list-card">
      <el-table v-loading="isLoading" :data="users" stripe>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="显示名" width="140" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
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
            <el-button link @click="openResetPwd(userRow(row))">重置密码</el-button>
            <el-button link type="danger" @click="handleDelete(userRow(row))">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showForm" :title="editing ? '编辑用户' : '新建用户'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item v-if="!editing" label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="字母、数字、下划线" />
        </el-form-item>
        <el-form-item v-if="!editing" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
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
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
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

const { data: usersData, isLoading } = useUsersQuery()
const createMutation = useCreateUserMutation()
const updateMutation = useUpdateUserMutation()
const deleteMutation = useDeleteUserMutation()
const resetPwdMutation = useResetUserPasswordMutation()

const users = computed(() => usersData.value || [])
const saving = computed(
  () =>
    createMutation.isPending.value ||
    updateMutation.isPending.value
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
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

function userRow(row: unknown) {
  return row as KbUser
}

function roleLabel(role: string) {
  return { ADMIN: '管理员', USER: '普通用户', EDITOR: '普通用户', VIEWER: '普通用户' }[role] || role
}

function roleTagType(role: string) {
  return ({ ADMIN: 'danger', USER: 'info', EDITOR: 'info', VIEWER: 'info' } as const)[role] || 'info'
}

function openCreate() {
  editing.value = false
  editingId.value = undefined
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.role = 'VIEWER'
  form.enabled = true
  showForm.value = true
}

function openEdit(row: KbUser) {
  editing.value = true
  editingId.value = row.id
  form.displayName = row.displayName
  form.role = row.role
  form.enabled = row.status === 1
  showForm.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editing.value && editingId.value) {
      await updateMutation.mutateAsync({
        id: editingId.value,
        data: {
          displayName: form.displayName,
          role: form.role,
          status: form.enabled ? 1 : 0
        }
      })
      ElMessage.success('已更新')
    } else {
      await createMutation.mutateAsync({
        username: form.username,
        password: form.password,
        displayName: form.displayName,
        role: form.role
      })
      ElMessage.success('已创建')
    }
    showForm.value = false
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function openResetPwd(row: KbUser) {
  const { value } = await ElMessageBox.prompt(`为用户「${row.username}」设置新密码`, '重置密码', {
    inputType: 'password'
  })
  if (!value || value.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  try {
    await resetPwdMutation.mutateAsync({ id: row.id, newPassword: value })
    ElMessage.success('密码已重置')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleDelete(row: KbUser) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '警告', { type: 'warning' })
  try {
    await deleteMutation.mutateAsync(row.id)
    ElMessage.success('已删除')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}
</script>

<style scoped>
.list-card { border-radius: 10px; }
</style>
