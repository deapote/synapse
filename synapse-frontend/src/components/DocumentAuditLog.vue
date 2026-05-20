<script setup lang="ts">
import type { DocumentAuditEvent } from '@/types'
import { History } from 'lucide-vue-next'

defineProps<{
  events: DocumentAuditEvent[]
  loading: boolean
}>()

function actionLabel(action: string): string {
  const map: Record<string, string> = {
    METADATA_UPDATED: '修改元数据',
    SUPERSEDED: '被替代',
    RETIRED: '废止',
    REACTIVATED: '重新启用',
    INDEX_REFRESH_REQUESTED: '请求刷新索引',
    INDEX_REFRESH_SUCCEEDED: '索引刷新成功',
    INDEX_REFRESH_FAILED: '索引刷新失败'
  }
  return map[action] || action
}

function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.toLocaleDateString('zh-CN')} ${d.toLocaleTimeString('zh-CN')}`
}
</script>

<template>
  <div class="audit-log">
    <div class="section-header">
      <History :size="12" />
      <h4>审计日志</h4>
    </div>

    <div v-if="loading" class="empty-state">加载中...</div>
    <div v-else-if="events.length === 0" class="empty-state">暂无审计记录</div>
    <div v-else class="event-list">
      <div v-for="e in events" :key="e.id" class="event-item">
        <div class="event-meta">
          <span class="event-action">{{ actionLabel(e.action) }}</span>
          <span class="event-time">{{ formatDateTime(e.createdAt) }}</span>
        </div>
        <div class="event-actor">操作人: {{ e.actorUserId }}</div>
        <div v-if="e.reason" class="event-reason">原因: {{ e.reason }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.audit-log {
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

.event-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.event-item {
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
}

.event-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.event-action {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.event-time {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
}

.event-actor {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.event-reason {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}
</style>
