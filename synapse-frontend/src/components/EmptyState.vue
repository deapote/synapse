<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

interface Props {
  title?: string
  description?: string
}

withDefaults(defineProps<Props>(), {
  title: '暂无数据',
  description: '开始创建你的第一个项目吧'
})
</script>

<template>
  <div class="empty-state">
    <div class="icon">
      <Inbox :size="24" />
    </div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
  </div>
</template>

<style scoped>
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: var(--text-secondary);
}

.icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  border-radius: var(--radius-md);
  background: var(--bg-subtle);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

p {
  font-size: 13px;
}
</style>
