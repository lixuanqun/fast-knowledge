<template>
  <div class="page-container">
    <PageHeader :title="kb?.name || '知识库详情'" show-back>
      <template #actions>
        <el-button :loading="rebuildMutation.isPending.value" @click="handleRebuild">重建索引</el-button>
        <el-upload
          :show-file-list="false"
          :http-request="handleUpload"
          accept=".pdf,.docx,.txt,.md,.pptx,.xlsx,.html"
          style="display:inline-block"
        >
          <el-button type="primary" :loading="uploadMutation.isPending.value">
            <el-icon class="btn-icon"><Upload /></el-icon>
            上传文档
          </el-button>
        </el-upload>
      </template>
    </PageHeader>

    <el-skeleton v-if="pageLoading" animated>
      <template #template>
        <el-skeleton-item variant="rect" style="height:96px;margin-bottom:16px;border-radius:10px" />
        <el-skeleton-item variant="rect" style="height:360px;border-radius:10px" />
      </template>
    </el-skeleton>

    <template v-else>
      <KbInfoHeader
        v-if="kb"
        :name="kb.name"
        :description="kb.description"
        :visibility="kb.visibility"
        :document-count="docs.length"
        :search-top-k="kb.searchTopK"
        :search-alpha="kb.searchAlpha"
      />

      <el-card class="detail-card fk-card" shadow="never">
        <el-tabs v-model="tab">
          <el-tab-pane label="文档" name="docs">
            <el-table v-if="docs.length" :data="pagedDocs" stripe>
              <el-table-column prop="title" label="标题" min-width="160">
                <template #default="{ row }">
                  <el-link type="primary" @click="openPreview(docRow(row))">{{ row.title }}</el-link>
                </template>
              </el-table-column>
              <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
              <el-table-column prop="fileType" label="类型" width="80" />
              <el-table-column label="大小" width="100">
                <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
              </el-table-column>
              <el-table-column label="索引状态" width="160">
                <template #default="{ row }">
                  <IndexStatusTag :status="row.indexStatus" :chunk-count="row.chunkCount" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openPreview(docRow(row))">预览</el-button>
                  <el-button link @click="handleReindex(row.id)">重新索引</el-button>
                  <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="docs.length" class="table-footer">
              <el-pagination
                v-model:current-page="docPage"
                v-model:page-size="docPageSize"
                :total="docs.length"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                background
                small
              />
            </div>
            <EmptyState v-else variant="docs">
              <el-upload
                :show-file-list="false"
                :http-request="handleUpload"
                accept=".pdf,.docx,.txt,.md,.pptx,.xlsx,.html"
              >
                <el-button type="primary">上传第一份文档</el-button>
              </el-upload>
            </EmptyState>
          </el-tab-pane>

          <el-tab-pane label="成员" name="members">
            <div class="tab-section">
              <div class="tab-section__header">
                <span class="tab-section__title">成员管理</span>
                <el-button link type="primary" @click="showPermissionHelp">了解成员权限</el-button>
              </div>
              <el-form inline class="member-form">
                <el-input v-model="memberForm.username" placeholder="请输入用户名" style="width:180px" />
                <el-select v-model="memberForm.permission" style="width:120px">
                  <el-option label="只读" value="READ" />
                  <el-option label="编辑" value="WRITE" />
                  <el-option label="管理" value="ADMIN" />
                </el-select>
                <el-button type="primary" :loading="addMemberMutation.isPending.value" @click="handleAddMember">
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
                    <el-button link type="danger" @click="handleRemoveMember(row.id)">移除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <EmptyState v-else variant="members" />
            </div>
          </el-tab-pane>

          <el-tab-pane label="设置" name="settings">
            <div class="tab-section">
              <h3 class="tab-section__title">基本设置</h3>
              <el-form label-width="130px" class="settings-form">
                <el-form-item label="名称" required>
                  <el-input v-model="settingsForm.name" maxlength="50" show-word-limit />
                </el-form-item>
                <el-form-item label="描述">
                  <el-input
                    v-model="settingsForm.description"
                    type="textarea"
                    :rows="3"
                    maxlength="200"
                    show-word-limit
                  />
                </el-form-item>
                <el-form-item label="可见性">
                  <el-radio-group v-model="settingsForm.visibility">
                    <el-radio value="PRIVATE">私有</el-radio>
                    <el-radio value="PUBLIC">公开</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-form>

              <h3 class="tab-section__title section-gap">检索配置</h3>
              <el-form label-width="130px" class="settings-form">
                <el-form-item label="混合检索权重 α">
                  <el-slider
                    v-model="settingsForm.searchAlpha"
                    :min="0"
                    :max="1"
                    :step="0.1"
                    show-input
                  />
                  <div class="form-tip">越大越偏向向量语义，越小越偏向关键词</div>
                </el-form-item>
                <el-form-item label="检索 Top K">
                  <el-input-number v-model="settingsForm.searchTopK" :min="1" :max="20" />
                  <div class="form-tip">检索时返回的最大文档数量，支持 1–20</div>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="updateKbMutation.isPending.value" @click="saveSettings">
                    保存设置
                  </el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>

          <el-tab-pane name="tasks">
            <template #label>
              索引任务
              <el-badge v-if="failedTasks.length" :value="failedTasks.length" class="tab-badge" />
            </template>
            <div class="tab-section">
              <p class="tab-section__hint">展示索引失败的任务，可在此重试</p>
              <el-table v-if="failedTasks.length" :data="failedTasks" stripe>
                <el-table-column prop="documentId" label="文档ID" width="100" />
                <el-table-column prop="status" label="状态" width="120">
                  <template #default="{ row }">
                    <IndexStatusTag :status="row.status" />
                  </template>
                </el-table-column>
                <el-table-column prop="retryCount" label="重试" width="80" />
                <el-table-column prop="errorMsg" label="错误信息" show-overflow-tooltip />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="handleRetry(row.documentId)">重试</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <EmptyState v-else variant="tasks" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <DocumentPreviewDrawer v-model:visible="previewVisible" :kb-id="kbId" :doc-id="previewDocId" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { KbDocument } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import KbInfoHeader from '@/components/design/KbInfoHeader.vue'
import IndexStatusTag from '@/components/IndexStatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import { DocumentPreviewDrawer } from '@/components/async'
import { formatFileSize, permissionLabel } from '@/utils/format'
import {
  useAddKbMemberMutation,
  useDeleteDocumentMutation,
  useKbDocumentsQuery,
  useKbFailedTasksQuery,
  useKbMembersQuery,
  useKbQuery,
  useRebuildKbIndexMutation,
  useReindexDocumentMutation,
  useRemoveKbMemberMutation,
  useRetryIndexTaskMutation,
  useUpdateKbMutation,
  useUploadDocumentMutation
} from '@/composables/queries/useKbDetail'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'

const route = useRoute()
const kbId = Number(route.params.id)

const tab = ref('docs')
const docPage = ref(1)
const docPageSize = ref(10)

onMounted(() => {
  const q = route.query.tab
  if (typeof q === 'string' && ['docs', 'members', 'settings', 'tasks'].includes(q)) {
    tab.value = q
  }
})

const previewVisible = ref(false)
const previewDocId = ref<number>()
const memberForm = reactive({ username: '', permission: 'READ' })
const settingsForm = reactive({
  name: '',
  description: '',
  visibility: 'PRIVATE',
  searchAlpha: 0.6,
  searchTopK: 8
})

const { data: kb, isLoading: kbLoading } = useKbQuery(kbId)
const { data: docsData, isLoading: docsLoading } = useKbDocumentsQuery(kbId)
const { data: membersData, isLoading: membersLoading } = useKbMembersQuery(kbId)
const { data: failedTasksData, isLoading: tasksLoading } = useKbFailedTasksQuery(kbId)

const updateKbMutation = useUpdateKbMutation(kbId)
const uploadMutation = useUploadDocumentMutation(kbId)
const deleteDocMutation = useDeleteDocumentMutation(kbId)
const reindexMutation = useReindexDocumentMutation(kbId)
const addMemberMutation = useAddKbMemberMutation(kbId)
const removeMemberMutation = useRemoveKbMemberMutation(kbId)
const retryMutation = useRetryIndexTaskMutation(kbId)
const rebuildMutation = useRebuildKbIndexMutation(kbId)

const docs = computed(() => docsData.value || [])
const pagedDocs = computed(() => {
  const start = (docPage.value - 1) * docPageSize.value
  return docs.value.slice(start, start + docPageSize.value)
})
const members = computed(() => membersData.value || [])
const failedTasks = computed(() => failedTasksData.value || [])
const pageLoading = computed(
  () => kbLoading.value || docsLoading.value || membersLoading.value || tasksLoading.value
)

watch(
  kb,
  data => {
    if (!data) return
    settingsForm.name = data.name
    settingsForm.description = data.description || ''
    settingsForm.visibility = data.visibility
    settingsForm.searchAlpha = data.searchAlpha
    settingsForm.searchTopK = data.searchTopK
  },
  { immediate: true }
)

function showPermissionHelp() {
  ElMessageBox.alert(
    '只读：可查看与检索文档；编辑：可上传与管理文档；管理：可修改知识库设置与成员。',
    '成员权限说明',
    { confirmButtonText: '知道了' }
  )
}

async function saveSettings() {
  if (!settingsForm.name.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  try {
    await updateKbMutation.mutateAsync({ ...settingsForm })
    ElMessage.success('设置已保存')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleUpload(opt: { file: File }) {
  try {
    await uploadMutation.mutateAsync(opt.file)
    ElMessage.success('上传成功，正在后台索引')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleReindex(docId: number) {
  try {
    await reindexMutation.mutateAsync(docId)
    ElMessage.info('已提交重新索引')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleDelete(docId: number) {
  await ElMessageBox.confirm('确认删除该文档？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger'
  })
  try {
    await deleteDocMutation.mutateAsync(docId)
    ElMessage.success('已删除')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleAddMember() {
  if (!memberForm.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  try {
    await addMemberMutation.mutateAsync({
      username: memberForm.username.trim(),
      permission: memberForm.permission
    })
    ElMessage.success('已添加')
    memberForm.username = ''
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleRemoveMember(memberId: number) {
  await ElMessageBox.confirm('确认移除该成员？', '提示', { type: 'warning' })
  try {
    await removeMemberMutation.mutateAsync(memberId)
    ElMessage.success('已移除')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleRetry(documentId: number) {
  try {
    await retryMutation.mutateAsync(documentId)
    ElMessage.success('已提交重试')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleRebuild() {
  await ElMessageBox.confirm('将从分块数据全量重建 Lucene 索引，确认？', '重建索引', { type: 'warning' })
  try {
    await rebuildMutation.mutateAsync()
    ElMessage.success('索引重建已在后台执行，列表将自动刷新')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

function docRow(row: unknown) {
  return row as KbDocument
}

function openPreview(row: KbDocument) {
  previewDocId.value = row.id
  previewVisible.value = true
}
</script>

<style scoped lang="scss">
.detail-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.tab-section__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.tab-section__title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: $fk-text-primary;
}

.tab-section__hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: $fk-text-secondary;
}

.section-gap {
  margin-top: 24px;
}

.member-form {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.settings-form {
  max-width: 560px;
}

.form-tip {
  font-size: 12px;
  color: $fk-text-secondary;
  margin-top: 4px;
  line-height: 1.5;
}

.tab-badge {
  margin-left: 6px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.btn-icon {
  margin-right: 4px;
}
</style>
