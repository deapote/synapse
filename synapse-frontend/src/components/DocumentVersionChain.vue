<script setup lang="ts">
import type { Document } from '@/types'
import { computed } from 'vue'
import { GitBranch, FileText } from 'lucide-vue-next'

const props = defineProps<{
  document: Document | null
  chain: Document[]
  loading: boolean
}>()

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

function lifecycleClass(status: string | null | undefined): string {
  const map: Record<string, string> = {
    ACTIVE: 'is-active',
    SUPERSEDED: 'is-superseded',
    RETIRED: 'is-retired'
  }
  return map[status || ''] || ''
}

function lifecycleLabel(status: string | null | undefined): string {
  const map: Record<string, string> = {
    ACTIVE: '生效中',
    SUPERSEDED: '已替代',
    RETIRED: '已废止'
  }
  return map[status || ''] || status || '-'
}

const sortedChain = computed(() => {
  // Sort by effectiveFrom ascending, then by uploadedAt ascending
  return [...props.chain].sort((a, b) => {
    const aDate = a.effectiveFrom || a.uploadedAt || ''
    const bDate = b.effectiveFrom || b.uploadedAt || ''
    return aDate.localeCompare(bDate)
  })
})
</script>

<template>
  <div class="version-chain">
    <div class="section-header">
      <GitBranch :size="12" />
      <h4>版本链</h4>
    </div>

    <div v-if="loading" class="empty-state">加载中...</div>
    <div v-else-if="chain.length === 0" class="empty-state">暂无版本关联</div>
    <div v-else class="chain-list">
      <div
        v-for="(doc, idx) in sortedChain"
        :key="doc.id"
        class="chain-node"
        :class="{ 'is-current': doc.id === document?.id }"
      >
        <div class="node-connector">
          <div class="node-dot" />
          <div v-if="idx < sortedChain.length - 1" class="node-line" />
        </div>
        <div class="node-content">
          <div class="node-title">
            <FileText :size="12" />
            <span class="file-name">{{ doc.fileName }}</span>
            <span v-if="doc.versionLabel" class="version-tag">{{ doc.versionLabel }}</span>
            <span v-if="doc.id === document?.id" class="current-badge">当前</span>
          </div>
          <div class="node-meta">
            <span class="lifecycle-badge" :class="lifecycleClass(doc.lifecycleStatus)">
              {{ lifecycleLabel(doc.lifecycleStatus) }}
            </span>
            <span class="date-range">
              {{ formatDate(doc.effectiveFrom) }} ~ {{ formatDate(doc.effectiveTo) }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.version-chain {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.empty-state {
  font-size: 13px;
  color: var(--text-muted);
  padding: 12px 0;
}

.chain-list {
  display: flex;
  flex-direction: column;
}

.chain-node {
  display: flex;
  gap: 10px;
  padding: 8px 0;
}

.chain-node.is-current .node-content {
  background: var(--accent-subtle);
  border-color: var(--accent);
}

.node-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
  padding-top: 6px;
}

.node-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  border: 2px solid var(--bg-base);
  box-shadow: 0 0 0 1px var(--border-strong);
}

.chain-node.is-current .node-dot {
  background: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-subtle);
}

.node-line {
  width: 1px;
  flex: 1;
  min-height: 20px;
  background: var(--border);
}

.node-content {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node-title {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.file-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  word-break: break-all;
}

.version-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 100px;
  background: var(--bg-hover);
  color: var(--text-secondary);
}

.current-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 100px;
  background: var(--accent);
  color: #fff;
  font-weight: 500;
}

.node-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.lifecycle-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 100px;
  font-weight: 500;
}

.is-active {
  background: var(--success-subtle);
  color: var(--success);
}

.is-superseded {
  background: var(--warning-subtle);
  color: var(--warning);
}

.is-retired {
  background: var(--danger-subtle);
  color: var(--danger);
}

.date-range {
  font-size: 11px;
  color: var(--text-muted);
}
</style>
