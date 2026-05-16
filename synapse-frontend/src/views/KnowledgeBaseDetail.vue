<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { useDocumentStore } from '@/stores/document'
import { usePolling } from '@/composables/usePolling'
import {
  ArrowLeft,
  Upload,
  Trash2,
  MessageSquare,
  FileText,
  AlertCircle
} from 'lucide-vue-next'
import StatusBadge from '@/components/StatusBadge.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const kbStore = useKnowledgeBaseStore()
const docStore = useDocumentStore()

const kbId = computed(() => route.params.id as string)
const kb = computed(() => kbStore.list.find((k) => k.id === kbId.value))

const fileInput = ref<HTMLInputElement | null>(null)

const showDeleteDialog = ref(false)
const deleteTargetId = ref('')
const deleteTargetName = ref('')
const isDeleting = ref(false)

// 轮询文档状态
const { start: startPolling, stop: stopPolling } = usePolling(
  async () => {
    await docStore.fetchList(kbId.value)
    return docStore.list
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
})

function triggerUpload() {
  fileInput.value?.click()
}

async function handleFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  try {
    await docStore.upload(kbId.value, file)
    // 上传成功后启动轮询
    startPolling((list) =>
      list.every((d) => d.status !== 'PENDING' && d.status !== 'PROCESSING')
    )
  } finally {
    target.value = ''
  }
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

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
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
        <button class="btn btn-primary btn-sm" @click="triggerUpload"
          :disabled="docStore.uploading"
        >
          <Upload :size="14" />
          {{ docStore.uploading ? '上传中...' : '上传文档' }}
        </button>
      </div>

      <input
        ref="fileInput"
        type="file"
        style="display: none"
        accept=".pdf,.doc,.docx,.txt,.md"
        @change="handleFileChange"
      />

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
              <th>状态</th>
              <th>分块数</th>
              <th>上传时间</th>
              <th style="width: 60px;"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="doc in docStore.list" :key="doc.id"
              :class="{ 'row-processing': doc.status === 'PROCESSING' }"
            >
              <td>
                <div class="cell-title">{{ doc.fileName }}</div>
                <div class="cell-meta">{{ doc.fileType }}</div>
              </td>
              <td class="cell-secondary">{{ formatFileSize(doc.fileSize) }}</td>
              <td>
                <StatusBadge :status="doc.status" />
              </td>
              <td class="cell-secondary">
                {{ doc.chunkCount > 0 ? doc.chunkCount : '-' }}
              </td>
              <td class="cell-secondary">{{ formatDate(doc.uploadedAt) }}</td>
              <td>
                <button
                  class="btn-icon btn-icon-sm"
                  :disabled="doc.status === 'PROCESSING'"
                  @click="openDeleteDialog(doc)"
                >
                  <Trash2 :size="14" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

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
  padding: 10px 16px;
  font-weight: 500;
  color: var(--text-secondary);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
}

tbody td {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
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
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.cell-secondary {
  color: var(--text-secondary);
  font-size: 13px;
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

.btn-icon-sm {
  width: 28px;
  height: 28px;
}

.btn-icon-sm:hover {
  background: var(--danger-subtle);
  color: var(--danger);
}
</style>
