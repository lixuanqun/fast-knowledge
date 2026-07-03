<template>
  <div>
    <PageHeader title="审计日志" subtitle="登录、检索、问答与关键操作留痕" />

    <el-card shadow="never" class="fk-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="用户 ID">
          <el-input v-model.number="filters.userId" placeholder="可选" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="操作">
          <el-select v-model="filters.action" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="a in actionOptions" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load(1)">查询</el-button>
          <el-button @click="handleExport">导出 CSV</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="用户" width="90" />
        <el-table-column prop="action" label="操作" width="140" />
        <el-table-column prop="targetType" label="对象类型" width="110" />
        <el-table-column prop="targetId" label="对象 ID" width="100" />
        <el-table-column prop="detail" label="详情" show-overflow-tooltip min-width="200" />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { exportAudits, listAudits, type AuditLog } from '@/api/audits'
import { formatDateTime } from '@/utils/format'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const rows = ref<AuditLog[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filters = reactive({ userId: undefined as number | undefined, action: '' })

const actionOptions = [
  'LOGIN',
  'LOGOUT',
  'SEARCH',
  'QA',
  'CHAT',
  'CREATE_KB',
  'UPLOAD_DOC',
  'DELETE_DOC',
  'INITIAL_SETUP'
]

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const res = await listAudits({
      page: page.value,
      size: pageSize.value,
      userId: filters.userId,
      action: filters.action || undefined
    })
    rows.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  try {
    const blob = (await exportAudits({
      userId: filters.userId,
      action: filters.action || undefined
    })) as unknown as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'audit-log.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => load(1))
</script>

<style scoped lang="scss">
.filter-form {
  margin-bottom: 16px;
}
.table-footer {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
