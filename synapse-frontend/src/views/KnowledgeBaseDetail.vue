<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useDocumentStore } from '@/stores/document'
import { usePolling } from '@/composables/usePolling'
import type { Document, DocumentSourceType, DocumentLifecycleStatus } from '@/types'
import {
  ArrowLeft,
  Upload,
  Trash2,
  MessageSquare,
  FileText,
  AlertCircle,
  X,
  Calendar,
  Tag,
  MapPin,
  Layers,
  FileBadge
} from 'lucide-vue-next'
import StatusBadge from '@/components/StatusBadge.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import DocumentDetailDrawer from '@/components/DocumentDetailDrawer.vue'
import DocumentMetadataEditDialog from '@/components/DocumentMetadataEditDialog.vue'
import DocumentSupersedeDialog from '@/components/DocumentSupersedeDialog.vue'

const route = useRoute()
const router = useRouter()
const kbStore = useKnowledgeBaseStore()
const docStore = useDocumentStore()

const kbId = computed(() => route.params.id as string)
const kb = computed(() => kbStore.list.find((k) => k.id === kbId.value))

const showDeleteDialog = ref(false)
const deleteTargetId = ref('')
const deleteTargetName = ref('')
const isDeleting = ref(false)

// 文档治理弹窗状态
const selectedDoc = ref<Document | null>(null)
const showDetailDrawer = ref(false)
const showEditDialog = ref(false)
const showSupersedeDialog = ref(false)
const showRetireDialog = ref(false)
const retireTarget = ref<Document | null>(null)

// 上传弹窗
const showUploadDialog = ref(false)
const uploadFile = ref<File | null>(null)
const uploadSubmitting = ref(false)
const uploadForm = ref({
  sourceType: 'GENERAL' as DocumentSourceType,
  canonicalKey: '',
  versionLabel: '',
  effectiveFrom: '',
  effectiveTo: '',
  authorityLevel: 0,
  jurisdiction: '',
  supersedesDocumentId: ''
})
const fileInputRef = ref<HTMLInputElement | null>(null)

// 轮询文档列表状态（PENDING/PROCESSING）
const { start: startPolling, stop: stopPolling } = usePolling(
  async () => {
    await docStore.fetchList(kbId.value)
    return docStore.list
  },
  2000,
  30
)

// 轮询单个文档索引状态（REFRESHING）
const { start: startIndexPolling, stop: stopIndexPolling } = usePolling(
  async () => {
    await docStore.fetchList(kbId.value)
    const updated = docStore.list.find((d) => d.id === selectedDoc.value?.id)
    if (updated) {
      selectedDoc.value = updated
    }
    return updated
  },
  2000,
  30
)

onMounted(async () => {
  if (kbStore.list.length === 0) {
    await kbStore.fetchList().catch(() => {})
  }
  await docStore.fetchList(kbId.value).catch(() => {})

  // 如果有 PENDING/PROCESSING 的文档，启动轮询
  const hasProcessing = docStore.list.some(
    (d) => d.status === 'PENDING' || d.status === 'PROCESSING'
  )
  if (hasProcessing) {
    startPolling((list) =>
      list.every((d) => d.status !== 'PENDING' && d.status !== 'PROCESSING')
    )
  }
})

onUnmounted(() => {
  stopPolling()
  stopIndexPolling()
})

// 上传弹窗操作
function openUploadDialog() {
  showUploadDialog.value = true
  resetUploadForm()
}

function resetUploadForm() {
  uploadFile.value = null
  uploadForm.value = {
    sourceType: 'GENERAL',
    canonicalKey: '',
    versionLabel: '',
    effectiveFrom: '',
    effectiveTo: '',
    authorityLevel: 0,
    jurisdiction: '',
    supersedesDocumentId: ''
  }
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    uploadFile.value = file
  }
}

function availableDocumentsForSupersede(): Document[] {
  return docStore.list.filter(
    (d) => d.status === 'COMPLETED' && d.lifecycleStatus === 'ACTIVE'
  )
}

function validateUploadForm(): string | null {
  if (!uploadFile.value) {
    return '请选择文件'
  }
  if (!uploadForm.value.sourceType) {
    return '请选择资料类型'
  }
  if (uploadForm.value.supersedesDocumentId && !uploadForm.value.effectiveFrom) {
    return '替代旧文档时，生效日期必填'
  }
  return null
}

async function handleUploadSubmit() {
  const errorMsg = validateUploadForm()
  if (errorMsg) {
    docStore.error = errorMsg
    return
  }
  if (!uploadFile.value) return

  uploadSubmitting.value = true
  docStore.error = null

  try {
    const metadata: Record<string, string | number> = {
      sourceType: uploadForm.value.sourceType
    }
    if (uploadForm.value.canonicalKey) metadata.canonicalKey = uploadForm.value.canonicalKey
    if (uploadForm.value.versionLabel) metadata.versionLabel = uploadForm.value.versionLabel
    if (uploadForm.value.effectiveFrom) metadata.effectiveFrom = uploadForm.value.effectiveFrom
    if (uploadForm.value.effectiveTo) metadata.effectiveTo = uploadForm.value.effectiveTo
    if (uploadForm.value.authorityLevel !== undefined && uploadForm.value.authorityLevel !== null) {
      metadata.authorityLevel = uploadForm.value.authorityLevel
    }
    if (uploadForm.value.jurisdiction) metadata.jurisdiction = uploadForm.value.jurisdiction
    if (uploadForm.value.supersedesDocumentId) metadata.supersedesDocumentId = uploadForm.value.supersedesDocumentId

    await docStore.upload(kbId.value, uploadFile.value, metadata)
    showUploadDialog.value = false
    resetUploadForm()
    // 启动轮询
    startPolling((list) =>
      list.every((d) => d.status !== 'PENDING' && d.status !== 'PROCESSING')
    )
  } catch {
    // error 已由 store 设置
  } finally {
    uploadSubmitting.value = false
  }
}

function clearFilters() {
  docStore.filters.sourceType = ''
  docStore.filters.lifecycleStatus = ''
  docStore.filters.indexStatus = ''
  docStore.filters.canonicalKey = ''
  docStore.fetchList(kbId.value)
}

function openDeleteDialog(doc: { id: string; fileName: string }) {
  deleteTargetId.value = doc.id
  deleteTargetName.value = doc.fileName
  showDeleteDialog.value = true
}

async function handleDelete() {
  isDeleting.value = true
  try {
    await docStore.remove(deleteTargetId.value)
    showDeleteDialog.value = false
  } finally {
    isDeleting.value = false
  }
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatShortDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

function formatEffectivePeriod(doc: Document): string {
  const from = doc.effectiveFrom ? formatShortDate(doc.effectiveFrom) : '长期'
  if (!doc.effectiveTo) {
    return `${from} 起长期有效`
  }
  return `${from} ~ ${formatShortDate(doc.effectiveTo)} 前有效`
}

function sourceTypeLabel(sourceType: DocumentSourceType | null | undefined): string {
  const map: Record<string, string> = {
    GENERAL: '普通',
    LEGAL: '法规',
    POLICY: '政策'
  }
  return sourceType ? map[sourceType] || sourceType : '-'
}

function lifecycleStatusLabel(status: DocumentLifecycleStatus | null | undefined): string {
  const map: Record<string, string> = {
    ACTIVE: '生效中',
    SUPERSEDED: '已替代',
    RETIRED: '已废止'
  }
  return status ? map[status] || status : '-'
}

function lifecycleStatusClass(status: DocumentLifecycleStatus | null | undefined): string {
  if (!status) return ''
  const map: Record<string, string> = {
    ACTIVE: 'ls-active',
    SUPERSEDED: 'ls-superseded',
    RETIRED: 'ls-retired'
  }
  return map[status] || ''
}

function openDetailDrawer(doc: Document) {
  selectedDoc.value = doc
  showDetailDrawer.value = true
}

function closeDetailDrawer() {
  showDetailDrawer.value = false
  selectedDoc.value = null
}

function openEditDialog() {
  showEditDialog.value = true
}

function closeEditDialog() {
  showEditDialog.value = false
}

async function handleSaveMetadata(patch: Record<string, unknown>) {
  if (!selectedDoc.value) return
  try {
    const updated = await docStore.patchDocMetadata(selectedDoc.value.id, patch as any)
    selectedDoc.value = updated
    closeEditDialog()
  } catch {
    // error handled by store
  }
}

function openSupersedeDialog() {
  showSupersedeDialog.value = true
}

function closeSupersedeDialog() {
  showSupersedeDialog.value = false
}

async function handleSupersede(newDocId: string, effectiveTo: string) {
  if (!selectedDoc.value) return
  try {
    await docStore.supersedeDocument(selectedDoc.value.id, newDocId, effectiveTo)
    await docStore.fetchList(kbId.value)
    const updated = docStore.list.find((d) => d.id === selectedDoc.value?.id)
    if (updated) {
      selectedDoc.value = updated
    }
    closeSupersedeDialog()
  } catch {
    // error handled by store
  }
}

function openRetireDialog(doc: Document) {
  retireTarget.value = doc
  showRetireDialog.value = true
}

function closeRetireDialog() {
  showRetireDialog.value = false
  retireTarget.value = null
}

async function handleRetire() {
  if (!retireTarget.value) return
  try {
    const updated = await docStore.retireDoc(retireTarget.value.id)
    selectedDoc.value = updated
    closeRetireDialog()
  } catch {
    // error handled by store
  }
}

async function handleReactivate() {
  if (!selectedDoc.value) return
  try {
    const updated = await docStore.reactivateDoc(selectedDoc.value.id)
    selectedDoc.value = updated
  } catch {
    // error handled by store
  }
}

async function handleReindex() {
  if (!selectedDoc.value) return
  try {
    const updated = await docStore.reindexDoc(selectedDoc.value.id)
    selectedDoc.value = updated
    // 启动轮询，直到索引状态不再是 REFRESHING
    if (updated.indexStatus === 'REFRESHING') {
      startIndexPolling((doc) => doc?.indexStatus !== 'REFRESHING')
    }
  } catch {
    // error handled by store
  }
}
</script>

<template>
  <div class="page">
    <!-- Header -->
    <div class="page-header">
      <button class="btn-back" @click="router.push('/knowledge-bases')">
        <ArrowLeft :size="16" />
        返回
      </button>
      <div class="header-actions">
        <button
          class="btn btn-secondary"
          @click="router.push(`/knowledge-bases/${kbId}/chat`)"
        >
          <MessageSquare :size="16" />
          问答
        </button>
      </div>
    </div>

    <!-- KB Info -->
    <div v-if="kb" class="kb-info">
      <h1>{{ kb.name }}</h1>
      <p v-if="kb.description" class="kb-desc">{{ kb.description }}</p>
      <div class="kb-meta">
        <span>ID: {{ kb.id }}</span>
        <span>Owner: {{ kb.ownerUserId }}</span>
        <span>创建于 {{ new Date(kb.createdAt).toLocaleDateString('zh-CN') }}</span>
      </div>
    </div>

    <div v-else-if="kbStore.loading" class="loading">加载中...</div>
    <div v-else class="alert alert-error">
      <AlertCircle :size="16" />
      知识库不存在
    </div>

    <!-- Documents Section -->
    <div v-if="kb" class="section">
      <div class="section-header">
        <div class="section-title">
          <FileText :size="18" />
          文档
          <span class="count">{{ docStore.list.length }}</span>
        </div>
        <button class="btn btn-primary btn-sm" @click="openUploadDialog"
          :disabled="docStore.uploading"
        >
          <Upload :size="14" />
          {{ docStore.uploading ? '上传中...' : '上传文档' }}
        </button>
      </div>

      <!-- 筛选器 -->
      <div class="filter-bar">
        <select v-model="docStore.filters.sourceType" class="form-select filter-select" @change="docStore.fetchList(kbId)">
          <option value="">全部类型</option>
          <option value="GENERAL">普通资料</option>
          <option value="LEGAL">法规</option>
          <option value="POLICY">政策</option>
        </select>
        <select v-model="docStore.filters.lifecycleStatus" class="form-select filter-select" @change="docStore.fetchList(kbId)">
          <option value="">全部时效状态</option>
          <option value="ACTIVE">生效中</option>
          <option value="SUPERSEDED">已替代</option>
          <option value="RETIRED">已废止</option>
        </select>
        <select v-model="docStore.filters.indexStatus" class="form-select filter-select" @change="docStore.fetchList(kbId)">
          <option value="">全部索引状态</option>
          <option value="SYNCED">已同步</option>
          <option value="STALE">待刷新</option>
          <option value="REFRESHING">刷新中</option>
          <option value="FAILED">刷新失败</option>
        </select>
        <input
          v-model="docStore.filters.canonicalKey"
          type="text"
          class="form-input filter-input"
          placeholder="规范标识筛选"
          @keyup.enter="docStore.fetchList(kbId)"
        />
        <button class="btn btn-secondary btn-sm" @click="clearFilters"
          v-if="docStore.filters.sourceType || docStore.filters.lifecycleStatus || docStore.filters.indexStatus || docStore.filters.canonicalKey"
        >
          清除筛选
        </button>
      </div>

      <div v-if="docStore.error" class="alert alert-error">
        {{ docStore.error }}
      </div>

      <EmptyState
        v-if="!docStore.loading && docStore.list.length === 0"
        title="暂无文档"
        description="上传文档到该知识库，系统将自动解析并建立索引"
      />

      <div v-else-if="docStore.list.length > 0" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>文件名</th>
              <th>大小</th>
              <th>类型</th>
              <th>时效状态</th>
              <th>生效期</th>
              <th>摄入状态</th>
              <th style="width: 60px;"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="doc in docStore.list" :key="doc.id"
              :class="{ 'row-processing': doc.status === 'PROCESSING' }"
            >
              <td>
                <div class="cell-title">{{ doc.fileName }}</div>
                <div class="cell-meta">
                  <span v-if="doc.versionLabel" class="meta-tag">
                    <Tag :size="10" />
                    {{ doc.versionLabel }}
                  </span>
                  <span v-if="doc.canonicalKey" class="meta-tag">
                    <FileBadge :size="10" />
                    {{ doc.canonicalKey }}
                  </span>
                  <span v-if="doc.jurisdiction" class="meta-tag">
                    <MapPin :size="10" />
                    {{ doc.jurisdiction }}
                  </span>
                </div>
              </td>
              <td class="cell-secondary">{{ formatFileSize(doc.fileSize) }}</td>
              <td class="cell-secondary">{{ sourceTypeLabel(doc.sourceType) }}</td>
              <td>
                <span v-if="doc.lifecycleStatus" class="lifecycle-badge" :class="lifecycleStatusClass(doc.lifecycleStatus)">
                  {{ lifecycleStatusLabel(doc.lifecycleStatus) }}
                </span>
                <span v-else class="cell-secondary">-</span>
              </td>
              <td class="cell-secondary">
                <span v-if="doc.effectiveFrom || doc.effectiveTo" class="effective-period">
                  <Calendar :size="10" />
                  {{ formatEffectivePeriod(doc) }}
                </span>
                <span v-else class="cell-secondary">-</span>
              </td>
              <td>
                <StatusBadge :status="doc.status" />
              </td>
              <td>
                <div class="row-actions">
                  <button
                    class="btn-icon btn-icon-sm"
                    @click="openDetailDrawer(doc)"
                  >
                    <FileText :size="14" />
                  </button>
                  <button
                    class="btn-icon btn-icon-sm"
                    :disabled="doc.status === 'PROCESSING'"
                    @click="openDeleteDialog(doc)"
                  >
                    <Trash2 :size="14" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Upload Dialog -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showUploadDialog" class="upload-dialog-overlay" @click="showUploadDialog = false">
          <div class="upload-dialog" @click.stop>
            <div class="upload-dialog-header">
              <h3>上传文档</h3>
              <button class="btn-icon" @click="showUploadDialog = false">
                <X :size="16" />
              </button>
            </div>

            <div class="upload-dialog-body">
              <!-- 文件选择 -->
              <div class="form-group">
                <label class="form-label">文件 <span class="required">*</span></label>
                <input
                  ref="fileInputRef"
                  type="file"
                  accept=".pdf,.doc,.docx,.txt,.md"
                  @change="handleFileSelect"
                />
                <div v-if="uploadFile" class="file-selected">
                  <FileText :size="14" />
                  {{ uploadFile.name }} ({{ formatFileSize(uploadFile.size) }})
                </div>
              </div>

              <!-- 资料类型 -->
              <div class="form-group">
                <label class="form-label">资料类型 <span class="required">*</span></label>
                <select v-model="uploadForm.sourceType" class="form-select">
                  <option value="GENERAL">普通资料</option>
                  <option value="LEGAL">法规</option>
                  <option value="POLICY">政策</option>
                </select>
              </div>

              <!-- 规范标识 & 版本标签 -->
              <div class="form-row">
                <div class="form-group flex-1">
                  <label class="form-label">规范标识 (canonicalKey)</label>
                  <input v-model="uploadForm.canonicalKey" type="text" class="form-input" placeholder="如 law-x" />
                </div>
                <div class="form-group flex-1">
                  <label class="form-label">版本标签</label>
                  <input v-model="uploadForm.versionLabel" type="text" class="form-input" placeholder="如 2024 版" />
                </div>
              </div>

              <!-- 生效日期 & 结束日期 -->
              <div class="form-row">
                <div class="form-group flex-1">
                  <label class="form-label">生效日期</label>
                  <input v-model="uploadForm.effectiveFrom" type="date" class="form-input" />
                </div>
                <div class="form-group flex-1">
                  <label class="form-label">结束日期（排他）</label>
                  <input v-model="uploadForm.effectiveTo" type="date" class="form-input" />
                  <span class="form-hint">到达该日期即失效，不包含该日期</span>
                </div>
              </div>

              <!-- 权威等级 & 适用区域 -->
              <div class="form-row">
                <div class="form-group flex-1">
                  <label class="form-label">权威等级</label>
                  <input v-model.number="uploadForm.authorityLevel" type="number" class="form-input" min="0" />
                </div>
                <div class="form-group flex-1">
                  <label class="form-label">适用区域</label>
                  <input v-model="uploadForm.jurisdiction" type="text" class="form-input" placeholder="如 全国" />
                </div>
              </div>

              <!-- 替代旧文档 -->
              <div class="form-group">
                <label class="form-label">
                  <Layers :size="12" />
                  替代旧版本
                </label>
                <select v-model="uploadForm.supersedesDocumentId" class="form-select">
                  <option value="">不替代</option>
                  <option
                    v-for="d in availableDocumentsForSupersede()"
                    :key="d.id"
                    :value="d.id"
                  >
                    {{ d.fileName }} {{ d.versionLabel ? `(${d.versionLabel})` : '' }} — {{ lifecycleStatusLabel(d.lifecycleStatus) }}
                  </option>
                </select>
                <span v-if="uploadForm.supersedesDocumentId" class="form-hint warning">
                  替代旧版本时，生效日期必填。旧版本将在新文档摄入成功后自动标记为"已替代"。
                </span>
              </div>
            </div>

            <div class="upload-dialog-footer">
              <button class="btn btn-secondary" @click="showUploadDialog = false">
                取消
              </button>
              <button
                class="btn btn-primary"
                :disabled="uploadSubmitting"
                @click="handleUploadSubmit"
              >
                {{ uploadSubmitting ? '上传中...' : '上传' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Delete Confirm -->
    <ConfirmDialog
      :visible="showDeleteDialog"
      title="删除文档"
      :message="`确定要删除「${deleteTargetName}」吗？同时会清理向量库中的相关数据。`"
      confirm-text="删除"
      danger
      @confirm="handleDelete"
      @cancel="showDeleteDialog = false"
    />

    <!-- Document Detail Drawer -->
    <DocumentDetailDrawer
      :document="selectedDoc"
      :visible="showDetailDrawer"
      @close="closeDetailDrawer"
      @edit="openEditDialog"
      @supersede="openSupersedeDialog"
      @retire="openRetireDialog(selectedDoc!)"
      @reactivate="handleReactivate"
      @reindex="handleReindex"
    />

    <!-- Metadata Edit Dialog -->
    <DocumentMetadataEditDialog
      :document="selectedDoc"
      :visible="showEditDialog"
      @close="closeEditDialog"
      @save="handleSaveMetadata"
    />

    <!-- Supersede Dialog -->
    <DocumentSupersedeDialog
      :old-document="selectedDoc"
      :visible="showSupersedeDialog"
      :documents="docStore.list"
      @close="closeSupersedeDialog"
      @confirm="handleSupersede"
    />

    <!-- Retire Confirm Dialog -->
    <ConfirmDialog
      :visible="showRetireDialog"
      title="废止文档"
      :message="`确定要废止「${retireTarget?.fileName || ''}」吗？废止后该文档将不再被检索。`"
      confirm-text="废止"
      danger
      @confirm="handleRetire"
      @cancel="closeRetireDialog"
    />
  </div>
</template>

<style scoped>
.page {
  padding: 24px 40px 40px;
  max-width: 1000px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.btn-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-back:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.kb-info {
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border);
}

.kb-info h1 {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.02em;
  margin-bottom: 6px;
}

.kb-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 12px;
}

.kb-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.loading {
  text-align: center;
  padding: 40px;
  color: var(--text-muted);
}

.alert {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  margin-bottom: 16px;
}

.alert-error {
  background: var(--danger-subtle);
  color: var(--danger);
  border: 1px solid rgba(220, 38, 38, 0.15);
}

/* Filter bar */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-select {
  width: auto;
  min-width: 120px;
}

.filter-input {
  width: auto;
  min-width: 140px;
}

/* Section */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.count {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  background: var(--bg-subtle);
  padding: 2px 8px;
  border-radius: 100px;
}

/* Buttons */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  border: none;
  font-family: inherit;
}

.btn-primary {
  background: var(--text-primary);
  color: #fff;
}
.btn-primary:hover:not(:disabled) {
  background: #333;
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: transparent;
  color: var(--text-primary);
  border: 1px solid var(--border-strong);
}
.btn-secondary:hover {
  background: var(--bg-hover);
}

.btn-sm {
  padding: 6px 12px;
  font-size: 12px;
}

/* Table */
.table-wrap {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

thead th {
  text-align: left;
  padding: 10px 12px;
  font-weight: 500;
  color: var(--text-secondary);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
}

tbody td {
  padding: 12px;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}

tbody tr:last-child td {
  border-bottom: none;
}

tbody tr:hover td {
  background: var(--bg-subtle);
}

tbody tr.row-processing {
  background: var(--accent-subtle);
}

.cell-title {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 13px;
}

.cell-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 1px 5px;
  border-radius: 4px;
}

.cell-secondary {
  color: var(--text-secondary);
  font-size: 13px;
}

/* Lifecycle badge */
.lifecycle-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 100px;
  font-size: 11px;
  font-weight: 500;
}

.ls-active {
  background: var(--success-subtle);
  color: var(--success);
}

.ls-superseded {
  background: var(--warning-subtle);
  color: var(--warning);
}

.ls-retired {
  background: var(--danger-subtle);
  color: var(--danger);
}

.effective-period {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

/* Icon buttons */
.btn-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.btn-icon:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.btn-icon:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.row-actions {
  display: flex;
  gap: 4px;
}

.btn-icon-sm {
  width: 28px;
  height: 28px;
}

.btn-icon-sm:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

/* Upload Dialog */
.upload-dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
  padding: 20px;
}

.upload-dialog {
  background: var(--bg-base);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  width: 100%;
  max-width: 560px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
}

.upload-dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.upload-dialog-header h3 {
  font-size: 16px;
  font-weight: 600;
}

.upload-dialog-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--border);
}

/* Form */
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-row {
  display: flex;
  gap: 12px;
}

.flex-1 {
  flex: 1;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.required {
  color: var(--danger);
}

.form-input,
.form-select {
  padding: 8px 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-size: 13px;
  background: var(--bg-base);
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.15s;
  font-family: inherit;
}

.form-input:focus,
.form-select:focus {
  border-color: var(--text-primary);
}

.form-select {
  cursor: pointer;
}

.form-hint {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.4;
}

.form-hint.warning {
  color: var(--warning);
}

.file-selected {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>