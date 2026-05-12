<script setup lang="ts">
import type { DocumentStatus } from '@/types'

interface Props {
  status: DocumentStatus
}

defineProps<Props>()

const statusMap: Record<DocumentStatus, { label: string; class: string }> = {
  PENDING: { label: '待处理', class: 'pending' },
  PROCESSING: { label: '处理中', class: 'processing' },
  COMPLETED: { label: '已完成', class: 'completed' },
  FAILED: { label: '失败', class: 'failed' }
}
</script>

<template>
  <span class="status" :class="statusMap[status].class">
    <span v-if="status === 'PENDING' || status === 'PROCESSING'" class="dot" />
    <span v-else class="dot" />
    {{ statusMap[status].label }}
  </span>
</template>

<style scoped>
.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 500;
}

.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.pending {
  background: var(--warning-subtle);
  color: var(--warning);
}
.pending .dot {
  animation: pulse 2s infinite;
}

.processing {
  background: var(--accent-subtle);
  color: var(--accent);
}
.processing .dot {
  animation: pulse 1.5s infinite;
}

.completed {
  background: var(--success-subtle);
  color: var(--success);
}

.failed {
  background: var(--danger-subtle);
  color: var(--danger);
}
</style>
