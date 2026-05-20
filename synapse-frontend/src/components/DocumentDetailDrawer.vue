<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Document, DocumentAuditEvent } from '@/types'
import { X, Calendar, Tag, MapPin, FileBadge, Layers, RefreshCw, AlertTriangle } from 'lucide-vue-next'
import DocumentAuditLog from './DocumentAuditLog.vue'
import DocumentVersionChain from './DocumentVersionChain.vue'
import { getAuditEvents, getVersionChain } from '@/api/document'

const props = defineProps<{
  document: Document | null
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
  edit: [doc: Document]
  supersede: [doc: Document]
  retire: [doc: Document]
  reactivate: [doc: Document]
  reindex: [doc: Document]
}>()

const isOpen = computed(() => props.visible && props.document !== null)

const auditEvents = ref<DocumentAuditEvent[]>([])
const versionChain = ref<Document[]>([])
const loadingAudit = ref(false)
const loadingChain = ref(false)

watch(() => props.document, async (doc) => {
  if (doc) {
    loadingAudit.value = true
    loadingChain.value = true
    try {
      auditEvents.value = await getAuditEvents(doc.id)
    } catch {
      auditEvents.value = []
    } finally {
      loadingAudit.value = false
    }
    try {
      versionChain.value = await getVersionChain(doc.id)
    } catch {
      versionChain.value = []
    } finally {
      loadingChain.value = false
    }
  } else {
    auditEvents.value = []
    versionChain.value = []
  }
}, { immediate: true })

function indexStatusLabel(status: string | null | undefined): string {
  const map: Record<string, string> = {
    SYNCED: '已同步',
    STALE: '待刷新',
    REFRESHING: '刷新中',
    FAILED: '刷新失败'
  }
  return status ? map[status] || status : '-'
}

function indexStatusClass(status: string | null | undefined): string {
  const map: Record<string, string> = {
    SYNCED: 'is-synced',
    STALE: 'is-stale',
    REFRESHING: 'is-refreshing',
    FAILED: 'is-failed'
  }
  return map[status || ''] || ''
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.toLocaleDateString('zh-CN')} ${d.toLocaleTimeString('zh-CN')}`
}
</script>

<template>
  <Teleport to="body">
    <Transition name="slide-right">
      <div v-if="isOpen" class="drawer-overlay" @click="emit('close')">
        <div class="drawer" @click.stop>
          <div class="drawer-header">
            <h3>{{ props.document?.fileName }}</h3>
            <button class="btn-icon" @click="emit('close')">
              <X :size="16" />
            </button>
          </div>

          <div v-if="props.document" class="drawer-body">
            <!-- 索引状态 -->
            <div class="status-bar">
              <span class="status-badge" :class="indexStatusClass(props.document.indexStatus)">
                <RefreshCw v-if="props.document.indexStatus === 'REFRESHING'" :size="12" class="spin" />
                <AlertTriangle v-else-if="props.document.indexStatus === 'FAILED'" :size="12" />
                <span v-else class="status-dot" />
                {{ indexStatusLabel(props.document.indexStatus) }}
              </span>
              <span v-if="props.document.metadataVersion !== undefined" class="version-hint">
                metadataVersion: {{ props.document.metadataVersion }}
                <span v-if="props.document.indexedMetadataVersion !== undefined">
                  / indexed: {{ props.document.indexedMetadataVersion }}
                </span>
              </span>
            </div>

            <!-- 基础信息 -->
            <div class="section">
              <h4>基础信息</h4>
              <div class="kv-grid">
                <div class="kv-item">
                  <span class="kv-key">ID</span>
                  <span class="kv-value mono">{{ props.document.id }}</span>
                </div>
                <div class="kv-item">
                  <span class="kv-key">状态</span>
                  <span class="kv-value">{{ props.document.status }}</span>
                </div>
                <div class="kv-item">
                  <span class="kv-key">分块数</span>
                  <span class="kv-value">{{ props.document.chunkCount }}</span>
                </div>
                <div class="kv-item">
                  <span class="kv-key">上传时间</span>
                  <span class="kv-value">{{ formatDateTime(props.document.uploadedAt) }}</span>
                </div>
              </div>
            </div>

            <!-- 时效元数据 -->
            <div class="section">
              <h4>时效元数据</h4>
              <div class="kv-grid">
                <div class="kv-item">
                  <span class="kv-key">类型</span>
                  <span class="kv-value">{{ props.document.sourceType || '-' }}</span>
                </div>
                <div class="kv-item">
                  <span class="kv-key">
                    <FileBadge :size="12" />
                    规范标识
                  </span>
                  <span class="kv-value">{{ props.document.canonicalKey || '-' }}</span>
                </div>
                <div class="kv-item">
                  <span class="kv-key">
                    <Tag :size="12" />
                    版本标签
                  </span>
                  <span class="kv-value">{{ props.document.versionLabel || '-' }}</span>
                </div>
                <div class="kv-item">
                  <span class="kv-key">
                    <Calendar :size="12" />
                    生效期
                  </span>
                  <span class="kv-value">
                    {{ formatDate(props.document.effectiveFrom) }} ~ {{ formatDate(props.document.effectiveTo) }}
                  </span>
                </div>
                <div class="kv-item">
                  <span class="kv-key">时效状态</span>
                  <span class="kv-value">{{ props.document.lifecycleStatus || '-' }}</span>
                </div>
                <div class="kv-item">
                  <span class="kv-key">
                    <MapPin :size="12" />
                    适用区域
                  </span>
                  <span class="kv-value">{{ props.document.jurisdiction || '-' }}</span>
                </div>
                <div class="kv-item">
                  <span class="kv-key">权威等级</span>
                  <span class="kv-value">{{ props.document.authorityLevel ?? '-' }}</span>
                </div>
                <div v-if="props.document.supersedesDocumentId" class="kv-item">
                  <span class="kv-key">
                    <Layers :size="12" />
                    替代文档
                  </span>
                  <span class="kv-value mono">{{ props.document.supersedesDocumentId }}</span>
                </div>
              </div>
            </div>

            <!-- 索引信息 -->
            <div v-if="props.document.lastIndexRefreshAt || props.document.lastIndexFailureReason" class="section">
              <h4>索引信息</h4>
              <div class="kv-grid">
                <div v-if="props.document.lastIndexRefreshAt" class="kv-item">
                  <span class="kv-key">最后刷新</span>
                  <span class="kv-value">{{ formatDateTime(props.document.lastIndexRefreshAt) }}</span>
                </div>
                <div v-if="props.document.lastIndexFailureReason" class="kv-item">
                  <span class="kv-key">失败原因</span>
                  <span class="kv-value fail-reason">{{ props.document.lastIndexFailureReason }}</span>
                </div>
              </div>
            </div>

            <!-- 版本链 -->
            <DocumentVersionChain
              :document="props.document"
              :chain="versionChain"
              :loading="loadingChain"
            />

            <!-- 审计日志 -->
            <DocumentAuditLog
              :events="auditEvents"
              :loading="loadingAudit"
            />

            <!-- 操作按钮 -->
            <div class="section actions">
              <button class="btn btn-secondary" @click="emit('edit', props.document)">
                编辑元数据
              </button>
              <button
                v-if="props.document.lifecycleStatus === 'ACTIVE'"
                class="btn btn-secondary"
                @click="emit('supersede', props.document)"
              >
                手动替代
              </button>
              <button
                v-if="props.document.lifecycleStatus === 'ACTIVE'"
                class="btn btn-danger"
                @click="emit('retire', props.document)"
              >
                废止
              </button>
              <button
                v-if="props.document.lifecycleStatus === 'RETIRED'"
                class="btn btn-secondary"
                @click="emit('reactivate', props.document)"
              >
                重新启用
              </button>
              <button class="btn btn-secondary" @click="emit('reindex', props.document)">
                重新刷新索引
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.2);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.drawer {
  width: 420px;
  max-width: 90vw;
  height: 100%;
  background: var(--bg-base);
  border-left: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  background: var(--bg-base);
  z-index: 1;
}

.drawer-header h3 {
  font-size: 15px;
  font-weight: 600;
  word-break: break-all;
}

.drawer-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 500;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.is-synced {
  background: var(--success-subtle);
  color: var(--success);
}

.is-stale {
  background: var(--warning-subtle);
  color: var(--warning);
}

.is-refreshing {
  background: var(--accent-subtle);
  color: var(--accent);
}

.is-failed {
  background: var(--danger-subtle);
  color: var(--danger);
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.version-hint {
  font-size: 11px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.section h4 {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 10px;
}

.kv-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kv-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid var(--border);
}

.kv-item:last-child {
  border-bottom: none;
}

.kv-key {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.kv-value {
  font-size: 13px;
  color: var(--text-primary);
  text-align: right;
  word-break: break-all;
}

.kv-value.mono {
  font-family: var(--font-mono);
  font-size: 11px;
}

.kv-value.fail-reason {
  color: var(--danger);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid var(--border-strong);
  background: transparent;
  color: var(--text-primary);
  font-family: inherit;
}

.btn:hover {
  background: var(--bg-hover);
}

.btn-danger {
  border-color: var(--danger);
  color: var(--danger);
}

.btn-danger:hover {
  background: var(--danger-subtle);
}

/* Transitions */
.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.25s ease;
}

.slide-right-enter-from .drawer,
.slide-right-leave-to .drawer {
  transform: translateX(100%);
}

.slide-right-enter-from,
.slide-right-leave-to {
  opacity: 0;
}
</style>
