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
            <DocumentsTab
              :docs="docs"
              @preview="openPreview"
              @metadata="openMetadata"
              @reindex="handleReindex"
              @delete="handleDelete"
            >
              <template #upload>
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
              </template>
            </DocumentsTab>
          </el-tab-pane>

          <el-tab-pane label="成员" name="members">
            <MembersTab
              :members="members"
              @add="handleAddMember"
              @remove="handleRemoveMember"
            />
          </el-tab-pane>

          <el-tab-pane label="Wiki" name="wiki">
            <WikiTab
              :pages="wikiPages"
              :loading="wikiLoading"
              :action-id="wikiActionId"
              :rebuilding="wikiRebuildPending"
              @view="openWikiPage"
              @publish="handlePublishWiki"
              @reject="handleRejectWiki"
              @rebuild-index="handleRebuildWikiIndex"
            />
          </el-tab-pane>

          <el-tab-pane label="设置" name="settings">
            <SettingsTab
              :kb="kb"
              :saving="updateKbMutation.isPending.value"
              @save="saveSettings"
            />
          </el-tab-pane>

          <el-tab-pane name="tasks">
            <template #label>
              索引任务
              <el-badge v-if="failedTasks.length" :value="failedTasks.length" class="tab-badge" />
            </template>
            <IndexTasksTab
              :tasks="failedTasks"
              @retry="handleRetry"
            />
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
            <el-option v-for="t in DOC_TYPES" :key="t.value" :label="t.label" :value="t.value" />
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
            <el-option v-for="t in DOC_TYPES" :key="t.value" :label="t.label" :value="t.value" />
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
import MarkdownBody from '@/components/MarkdownBody.vue'
import { DocumentPreviewDrawer } from '@/components/async'
import { DOC_TYPES } from '@/constants'
import { useQuery } from '@tanstack/vue-query'
import { listWikiPages, publishWikiPage, rejectWikiPage, rebuildWikiIndex } from '@/api/wiki'
import { queryKeys } from '@/lib/query-keys'
import DocumentsTab from './kb-detail/DocumentsTab.vue'
import MembersTab from './kb-detail/MembersTab.vue'
import WikiTab from './kb-detail/WikiTab.vue'
import SettingsTab from './kb-detail/SettingsTab.vue'
import IndexTasksTab from './kb-detail/IndexTasksTab.vue'
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
import { Refresh, Upload } from '@element-plus/icons-vue'

const route = useRoute()
const kbId = computed(() => Number(route.params.id))
const tab = ref('docs')
const previewVisible = ref(false)
const previewDocId = ref<number>()
const previewChunkId = ref<number>()
const uploadDialogVisible = ref(false)
const metadataDialogVisible = ref(false)
const wikiDialogVisible = ref(false)
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
const failedTasks = computed(() => failedTasksData.value || [])
const pageLoading = computed(() => kbLoading.value || docsLoading.value || wikiLoading.value)

const settingsForm = reactive({
  name: '',
  description: '',
  visibility: 'PRIVATE',
  searchTopK: 8
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

function handleAddMember(member: { username: string; permission: string }) {
  addMemberMutation.mutateAsync({ ...member }).then(() => {
    ElMessage.success('已添加成员')
  }).catch(() => {})
}

function handleRemoveMember(id: number) {
  ElMessageBox.confirm('确认移除该成员？', '提示', { type: 'warning' }).then(async () => {
    await removeMemberMutation.mutateAsync(id)
    ElMessage.success('已移除')
  }).catch(() => {})
}

function resetUploadMeta() {
  uploadMeta.docType = ''
  uploadMeta.docNo = ''
  uploadMeta.effectiveDate = ''
  uploadMeta.expireDate = ''
  uploadMeta.department = ''
  uploadMeta.tags = ''
}
</script>

<style scoped lang="scss">
.detail-card {
  border-radius: 10px;
  border: 1px solid $fk-border;
  background: $fk-card-bg;
}

.tab-badge {
  margin-left: 4px;
}

.wiki-status-tag {
  margin-bottom: 12px;
}

.btn-icon {
  margin-right: 4px;
}
</style>