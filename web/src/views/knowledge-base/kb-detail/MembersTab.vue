<template>
  <div class="tab-section">
    <el-form inline class="member-form">
      <el-input v-model="username" placeholder="用户名" style="width:180px" :prefix-icon="User" />
      <el-select v-model="permission" style="width:160px" placeholder="只读/编辑/管理">
        <el-option v-for="p in PERMISSIONS" :key="p.value" :label="p.label" :value="p.value" />
      </el-select>
      <el-button type="primary" :loading="adding" @click="handleAdd">
        <el-icon class="btn-icon"><Plus /></el-icon>
        添加成员
      </el-button>
    </el-form>
    <el-table v-if="members.length" :data="members" stripe>
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="displayName" label="显示名" />
      <el-table-column label="权限" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="light">{{ permissionLabel(row.permission) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="emit('remove', row.id)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <EmptyState v-else variant="members" />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PERMISSIONS } from '@/constants'
import EmptyState from '@/components/EmptyState.vue'
import { permissionLabel } from '@/utils/format'
import { Plus, User } from '@element-plus/icons-vue'

defineProps<{ members: Array<{ id: number; username: string; displayName: string; permission: string }> }>()

const emit = defineEmits<{
  add: [member: { username: string; permission: string }]
  remove: [memberId: number]
}>()

const adding = ref(false)
const username = ref('')
const permission = reactive({ value: 'READ' })

function handleAdd() {
  if (!username.value.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  adding.value = true
  emit('add', { username: username.value.trim(), permission: permission.value })
}

/** 父组件在添加成功后调用以清空输入 */
function clearInput() {
  username.value = ''
  adding.value = false
}

defineExpose({ clearInput, stopLoading: () => (adding.value = false) })
</script>

<style scoped lang="scss">
.member-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}
</style>