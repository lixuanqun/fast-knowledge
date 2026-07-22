<template>
  <div class="page-container">
    <PageHeader
      :title="kb?.name || '知识库详情'"
      :subtitle="kb?.description"
      show-back
    >
      <template #actions>
        <el-button :loading="rebuildMutation.isPending.value" @click="handleRebuild">
          <el-icon class="btn-icon"><Refresh /></el-icon>
          重建索引
        </el-button>
        <el-upload
          :show-file-list="false"
          accept=".pdf,.docx,.txt,.md,.pptx,.xlsx,.html"
          style="display:inline-block"
          :auto-upload="false"
          :on-change="onHeaderUploadPick"
        >
          <el-button type="primary">
            <el-icon class="btn-icon"><Upload /></el-icon>
            上传文档
          </el-button>
        </el-upload>
      </template>
    </PageHeader>

    <el-skeleton v-if="pageLoading" animated>
      <template #template>
        <el-skeleton-item variant="rect" style="height:360px;border-radius:10px" />
      </template>
    </el-skeleton>

    <template v-else>
      <el-card class="detail-card fk-card" shadow="never">
        <el-tabs v-model="tab">
          <el-tab-pane label="文档" name="docs">
            <el-table v-if="docs.length" :data="pagedDocs" stripe>
              <el-table-column prop="title" label="标题" min-width="160">
                <template #default="{ row }">
                  <el-link type="primary" @click="openPreview(row as KbDocument)">{{ row.title }}</el-link>
                </template>
              </el-table-column>
              <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
              <el-table-column prop="docType" label="类型" width="90" show-overflow-tooltip />
              <el-table-column prop="docNo" label="文号" width="120" show-overflow-tooltip />
              <el-table-column prop="fileType" label="格式" width="72" />
              <el-table-column label="大小" width="100">
                <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
              </el-table-column>
              <el-table-column label="索引状态" width="120">
                <template #default="{ row }">
                  <IndexStatusTag :status="row.indexStatus" />
                </template>
              </el-table-column>
              <el-table-column label="检索" width="100" align="center">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    effect="light"
                    :type="recallTagType(row as KbDocument)"
                  >{{ recallLabel(row as KbDocument) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="分块" width="80" align="center">
                <template #default="{ row }">{{ row.chunkCount ?? 0 }}</template>
              </el-table-column>
              <el-table-column label="操作" width="240" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openPreview(row as KbDocument)">预览</el-button>
                  <el-button link type="primary" @click="openMetadata(row as KbDocument)">元数据</el-button>
                  <el-button link type="primary" @click="handleReindex(row.id)">重新索引</el-button>
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
                accept=".pdf,.docx,.txt,.md,.pptx,.xlsx,.html"
                :auto-upload="false"
                :on-change="onHeaderUploadPick"
              >
                <el-button type="primary">
                  <el-icon class="btn-icon"><Upload /></el-icon>
                  上传第一份文档
                </el-button>
              </el-upload>
            </EmptyState>
          </el-tab-pane>

          <el-tab-pane label="成员" name="members">
            <div class="tab-section">
              <el-form inline class="member-form">
                <el-input v-model="memberForm.username" placeholder="用户名" style="width:180px" :prefix-icon="User" />
                <el-select v-model="memberForm.permission" style="width:160px" placeholder="只读/编辑/管理">
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

          <el-tab-pane label="Wiki" name="wiki">
            <div v-loading="wikiLoading" class="tab-section">
              <div class="wiki-toolbar">
                <p class="tab-section__hint">
                  文档索引后编译为 Wiki（默认草稿）。发布后进入问答双路召回；「目录」问法优先命中 index。
                </p>
                <div class="wiki-toolbar__actions">
                  <el-select v-model="wikiStatusFilter" clearable placeholder="全部状态" style="width:140px">
                    <el-option label="草稿" value="DRAFT" />
                    <el-option label="已发布" value="PUBLISHED" />
                  </el-select>
                  <el-button :loading="wikiRebuildPending" @click="handleRebuildWikiIndex">重建目录</el-button>
                </div>
              </div>
              <el-table v-if="filteredWikiPages.length" :data="filteredWikiPages" stripe>
                <el-table-column prop="title" label="标题" min-width="180">
                  <template #default="{ row }">
                    <el-link type="primary" @click="openWikiPage(row as WikiPage)">{{ row.title }}</el-link>
                  </template>
                </el-table-column>
                <el-table-column prop="slug" label="Slug" width="160" show-overflow-tooltip />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
                      {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="更新时间" width="180">
                  <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.slug !== 'index' && row.status !== 'PUBLISHED'"
                      link
                      type="primary"
                      :loading="wikiActionId === row.id"
                      @click="handlePublishWiki(row as WikiPage)"
                    >发布</el-button>
                    <el-button
                      v-if="row.slug !== 'index' && row.status === 'PUBLISHED'"
                      link
                      type="warning"
                      :loading="wikiActionId === row.id"
                      @click="handleRejectWiki(row as WikiPage)"
                    >下架</el-button>
                    <el-button link type="primary" @click="openWikiPage(row as WikiPage)">查看</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <EmptyState v-else variant="docs" description="暂无 Wiki 页面，上传文档并完成索引后将自动生成" />
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
                <el-form-item label="检索 Top K">
                  <el-input-number v-model="settingsForm.searchTopK" :min="1" :max="20" />
                  <div class="form-tip">检索与 RAG 返回的最大片段数（1–20）</div>
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

    <DocumentPreviewDrawer
      v-model:visible="previewVisible"
      :kb-id="kbId"
      :doc-id="previewDocId"
      :highlight-chunk-id="previewChunkId"
    />

    <el-dialog v-model="uploadDialogVisible" title="上传文档" width="520px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="文件" required>
          <el-upload
            :auto-upload="false"
            :limit="1"
            :on-change="onUploadFileChange"
            :on-remove="() => (pendingFile = undefined)"
            accept=".pdf,.docx,.txt,.md,.pptx,.xlsx,.html"
          >
            <el-button type="primary">选择文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="uploadMeta.docType" clearable placeholder="制度/工艺/设备..." style="width:100%">
            <el-option label="制度" value="POLICY" />
            <el-option label="工艺" value="PROCESS" />
            <el-option label="设备" value="EQUIPMENT" />
            <el-option label="质量" value="QUALITY" />
            <el-option label="安全" value="SAFETY" />
            <el-option label="FAQ" value="FAQ" />
            <el-option label="其他" value="GENERAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="文号">
          <el-input v-model="uploadMeta.docNo" placeholder="如 Q/SY 001-2024" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="uploadMeta.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="失效日期">
          <el-date-picker v-model="uploadMeta.expireDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="uploadMeta.department" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="uploadMeta.tags" placeholder="逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadMutation.isPending.value" @click="confirmUpload">
          上传
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="metadataDialogVisible" title="编辑文档元数据" width="520px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="文档类型">
          <el-select v-model="metadataForm.docType" clearable style="width:100%">
            <el-option label="制度" value="POLICY" />
            <el-option label="工艺" value="PROCESS" />
            <el-option label="设备" value="EQUIPMENT" />
            <el-option label="质量" value="QUALITY" />
            <el-option label="安全" value="SAFETY" />
            <el-option label="FAQ" value="FAQ" />
            <el-option label="其他" value="GENERAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="文号">
          <el-input v-model="metadataForm.docNo" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="metadataForm.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="失效日期">
          <el-date-picker v-model="metadataForm.expireDate" type="date" value-format="YYYY-MM-DD" style="width:100%" clearable />
        </el-form-item>
        <el-form-item label="参与检索">
          <el-switch
            v-model="metadataForm.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="是"
            inactive-text="禁用"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="metadataForm.department" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="metadataForm.tags" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metadataDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="metadataMutation.isPending.value" @click="saveMetadata">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="wikiDialogVisible" :title="activeWiki?.title || 'Wiki'" width="720px" destroy-on-close>
      <el-tag v-if="activeWiki" size="small" class="wiki-status-tag">{{ activeWiki.status }}</el-tag>
      <MarkdownBody v-if="activeWiki" :content="activeWiki.contentMd" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { KbDocument, WikiPage } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import IndexStatusTag from '@/components/IndexStatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import MarkdownBody from '@/components/MarkdownBody.vue'
import { DocumentPreviewDrawer } from '@/components/async'
import { formatDateTime, formatFileSize, permissionLabel } from '@/utils/format'
import { useQuery } from '@tanstack/vue-query'
import { listWikiPages, publishWikiPage, rejectWikiPage, rebuildWikiIndex } from '@/api/wiki'
import { queryKeys } from '@/lib/query-keys'
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
  useUpdateDocumentMetadataMutation,
  useUpdateKbMutation,
  useUploadDocumentMutation
} from '@/composables/queries/useKbDetail'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { Plus, Refresh, Upload, User } from '@element-plus/icons-vue'

const route = useRoute()
const kbId = computed(() => Number(route.params.id))
const tab = ref('docs')
const docPage = ref(1)
const docPageSize = ref(10)
const previewVisible = ref(false)
const previewDocId = ref<number>()
const previewChunkId = ref<number>()
const uploadDialogVisible = ref(false)
const metadataDialogVisible = ref(false)
const wikiDialogVisible = ref(false)
const wikiStatusFilter = ref('')
const wikiActionId = ref<number>()
const wikiRebuildPending = ref(false)
const pendingFile = ref<File>()
const editingDocId = ref<number>()
const activeWiki = ref<WikiPage>()

const uploadMeta = reactive({
  docType: '',
  docNo: '',
  effectiveDate: '',
  expireDate: '',
  department: '',
  tags: ''
})

const metadataForm = reactive({
  docType: '',
  docNo: '',
  effectiveDate: '',
  expireDate: '',
  department: '',
  tags: '',
  enabled: 1 as number
})

const { data: kb, isLoading: kbLoading } = useKbQuery(kbId)
const { data: docsData, isLoading: docsLoading } = useKbDocumentsQuery(kbId)
const { data: membersData } = useKbMembersQuery(kbId)
const { data: failedTasksData } = useKbFailedTasksQuery(kbId)

const uploadMutation = useUploadDocumentMutation(kbId)
const metadataMutation = useUpdateDocumentMetadataMutation(kbId)
const deleteMutation = useDeleteDocumentMutation(kbId)
const reindexMutation = useReindexDocumentMutation(kbId)
const rebuildMutation = useRebuildKbIndexMutation(kbId)
const retryMutation = useRetryIndexTaskMutation(kbId)
const updateKbMutation = useUpdateKbMutation(kbId)
const addMemberMutation = useAddKbMemberMutation(kbId)
const removeMemberMutation = useRemoveKbMemberMutation(kbId)

const { data: wikiData, isLoading: wikiLoading, refetch: refetchWiki } = useQuery({
  queryKey: computed(() => queryKeys.wiki.pages(kbId.value)),
  queryFn: async () => {
    const res = await listWikiPages(kbId.value)
    return (res.data || []) as WikiPage[]
  },
  enabled: computed(() => tab.value === 'wiki' && kbId.value > 0)
})

const docs = computed(() => docsData.value || [])
const members = computed(() => membersData.value || [])
const wikiPages = computed(() => wikiData.value || [])
const filteredWikiPages = computed(() => {
  if (!wikiStatusFilter.value) return wikiPages.value
  return wikiPages.value.filter((p) => p.status === wikiStatusFilter.value)
})
const failedTasks = computed(() => failedTasksData.value || [])
const pageLoading = computed(() => kbLoading.value || docsLoading.value)

const pagedDocs = computed(() => {
  const start = (docPage.value - 1) * docPageSize.value
  return docs.value.slice(start, start + docPageSize.value)
})

const settingsForm = reactive({
  name: '',
  description: '',
  visibility: 'PRIVATE',
  searchTopK: 8
})

const memberForm = reactive({
  username: '',
  permission: 'READ'
})

watch(
  kb,
  val => {
    if (!val) return
    settingsForm.name = val.name
    settingsForm.description = val.description || ''
    settingsForm.visibility = val.visibility
    settingsForm.searchTopK = val.searchTopK
  },
  { immediate: true }
)

onMounted(() => {
  const q = route.query.tab as string
  if (q) tab.value = q
})

function openPreview(doc: KbDocument, chunkId?: number) {
  previewDocId.value = doc.id
  previewChunkId.value = chunkId
  previewVisible.value = true
}

function resetUploadMeta() {
  uploadMeta.docType = ''
  uploadMeta.docNo = ''
  uploadMeta.effectiveDate = ''
  uploadMeta.expireDate = ''
  uploadMeta.department = ''
  uploadMeta.tags = ''
}

function onHeaderUploadPick(file: UploadFile) {
  pendingFile.value = file.raw
  resetUploadMeta()
  uploadDialogVisible.value = true
}

function onUploadFileChange(file: UploadFile) {
  pendingFile.value = file.raw
}

async function confirmUpload() {
  if (!pendingFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  try {
    await uploadMutation.mutateAsync({
      file: pendingFile.value,
      metadata: {
        docType: uploadMeta.docType || undefined,
        docNo: uploadMeta.docNo || undefined,
        effectiveDate: uploadMeta.effectiveDate || undefined,
        expireDate: uploadMeta.expireDate || undefined,
        department: uploadMeta.department || undefined,
        tags: uploadMeta.tags || undefined
      }
    })
    uploadDialogVisible.value = false
    pendingFile.value = undefined
    ElMessage.success('上传成功，正在索引')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

function openMetadata(doc: KbDocument) {
  editingDocId.value = doc.id
  metadataForm.docType = doc.docType || ''
  metadataForm.docNo = doc.docNo || ''
  metadataForm.effectiveDate = doc.effectiveDate || ''
  metadataForm.expireDate = doc.expireDate || ''
  metadataForm.department = doc.department || ''
  metadataForm.tags = doc.tags || ''
  metadataForm.enabled = doc.enabled === 0 ? 0 : 1
  metadataDialogVisible.value = true
}

async function saveMetadata() {
  if (!editingDocId.value) return
  try {
    await metadataMutation.mutateAsync({
      docId: editingDocId.value,
      metadata: {
        docType: metadataForm.docType,
        docNo: metadataForm.docNo,
        department: metadataForm.department,
        tags: metadataForm.tags,
        enabled: metadataForm.enabled,
        effectiveDate: metadataForm.effectiveDate || undefined,
        expireDate: metadataForm.expireDate || undefined,
        clearEffectiveDate: !metadataForm.effectiveDate,
        clearExpireDate: !metadataForm.expireDate
      }
    })
    metadataDialogVisible.value = false
    ElMessage.success('元数据已更新')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

function recallLabel(doc: KbDocument): string {
  if (doc.enabled === 0) return '已禁用'
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  if (doc.effectiveDate) {
    const effective = new Date(doc.effectiveDate)
    if (effective > today) return '未生效'
  }
  if (doc.expireDate) {
    const expire = new Date(doc.expireDate)
    if (expire < today) return '已过期'
  }
  return '可检索'
}

function recallTagType(doc: KbDocument): 'success' | 'info' | 'warning' | 'danger' {
  const label = recallLabel(doc)
  if (label === '可检索') return 'success'
  if (label === '未生效') return 'warning'
  return 'info'
}

function openWikiPage(page: WikiPage) {
  activeWiki.value = page
  wikiDialogVisible.value = true
}

async function handlePublishWiki(page: WikiPage) {
  wikiActionId.value = page.id
  try {
    await publishWikiPage(kbId.value, page.id)
    ElMessage.success('已发布，目录已更新')
    await refetchWiki()
  } catch {
    /* axios */
  } finally {
    wikiActionId.value = undefined
  }
}

async function handleRejectWiki(page: WikiPage) {
  wikiActionId.value = page.id
  try {
    await rejectWikiPage(kbId.value, page.id)
    ElMessage.success('已下架为草稿')
    await refetchWiki()
  } catch {
    /* axios */
  } finally {
    wikiActionId.value = undefined
  }
}

async function handleRebuildWikiIndex() {
  wikiRebuildPending.value = true
  try {
    await rebuildWikiIndex(kbId.value)
    ElMessage.success('目录已重建')
    await refetchWiki()
  } catch {
    /* axios */
  } finally {
    wikiRebuildPending.value = false
  }
}

async function handleDelete(docId: number) {
  await ElMessageBox.confirm('确认删除该文档？', '警告', { type: 'warning' })
  try {
    await deleteMutation.mutateAsync(docId)
    ElMessage.success('已删除')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleReindex(docId: number) {
  try {
    await reindexMutation.mutateAsync(docId)
    ElMessage.success('已提交重新索引')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleRebuild() {
  await ElMessageBox.confirm('确认重建该知识库全部索引？', '提示', { type: 'warning' })
  try {
    await rebuildMutation.mutateAsync()
    ElMessage.success('已提交重建任务')
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

async function saveSettings() {
  try {
    await updateKbMutation.mutateAsync({ ...settingsForm })
    ElMessage.success('设置已保存')
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
    await addMemberMutation.mutateAsync({ ...memberForm })
    memberForm.username = ''
    ElMessage.success('已添加成员')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}

async function handleRemoveMember(id: number) {
  await ElMessageBox.confirm('确认移除该成员？', '提示', { type: 'warning' })
  try {
    await removeMemberMutation.mutateAsync(id)
    ElMessage.success('已移除')
  } catch {
    /* 错误已由 axios 拦截器提示 */
  }
}
</script>

<style scoped lang="scss">
.detail-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.tab-section {
  padding: 4px 0;
}

.tab-section__title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: $fk-text-primary;
}

.tab-section__hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: $fk-text-secondary;
}

.wiki-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.wiki-toolbar__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.section-gap {
  margin-top: 24px;
}

.member-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.form-tip {
  font-size: 12px;
  color: $fk-text-secondary;
  margin-top: 4px;
}

.btn-icon {
  margin-right: 4px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.tab-badge {
  margin-left: 4px;
}

.wiki-status-tag {
  margin-bottom: 12px;
}
</style>
