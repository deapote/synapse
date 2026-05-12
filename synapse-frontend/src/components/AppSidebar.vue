<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import {
  Database,
  MessageSquare,
  Settings
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

interface NavItem {
  label: string
  path: string
  icon: typeof Database
}

const navItems: NavItem[] = [
  { label: '知识库', path: '/knowledge-bases', icon: Database },
  { label: '问答', path: '/chat', icon: MessageSquare },
]

function isActive(path: string): boolean {
  if (path === '/chat') {
    return route.path === '/chat' || route.path.endsWith('/chat')
  }
  if (path === '/knowledge-bases') {
    // 精确匹配 /knowledge-bases 或 /knowledge-bases/:id，不匹配子路由
    return route.path === '/knowledge-bases' ||
      /^\/knowledge-bases\/[^\/]+$/.test(route.path)
  }
  return route.path.startsWith(path)
}

function navigate(path: string) {
  router.push(path)
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand" @click="router.push('/')">
      Synapse
    </div>

    <nav class="nav">
      <button
        v-for="item in navItems"
        :key="item.path"
        class="nav-item"
        :class="{ active: isActive(item.path) }"
        @click="navigate(item.path)"
      >
        <component :is="item.icon" class="nav-icon" :size="18" />
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <div class="footer">
      <button class="nav-item" @click="router.push('/settings')">
        <Settings class="nav-icon" :size="18" />
        <span>设置</span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  width: var(--sidebar-w);
  height: 100vh;
  background: var(--bg-subtle);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  padding: 20px 12px;
  z-index: 100;
}

.brand {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  padding: 8px 12px 20px;
  cursor: pointer;
  user-select: none;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 450;
  cursor: pointer;
  transition: all 0.15s ease;
  border: none;
  background: transparent;
  text-align: left;
  width: 100%;
}

.nav-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--bg-active);
  color: var(--text-primary);
  font-weight: 500;
}

.nav-icon {
  opacity: 0.6;
  flex-shrink: 0;
}

.nav-item.active .nav-icon,
.nav-item:hover .nav-icon {
  opacity: 1;
}

.footer {
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}
</style>
