<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import {
  Database,
  LogOut,
  MessageSquare,
  Shield
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

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

async function handleLogout() {
  await authStore.logout()
  router.replace('/login')
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
      <button
        v-if="authStore.isAdmin"
        class="nav-item"
        :class="{ active: isActive('/admin') }"
        @click="router.push('/admin')"
      >
        <Shield class="nav-icon" :size="18" />
        <span>权限管理</span>
      </button>
      <div class="user-box">
        <div class="user-name">{{ authStore.user?.displayName || authStore.user?.username }}</div>
        <div class="user-role">{{ authStore.user?.roles.join(' / ') }}</div>
      </div>
      <button class="nav-item logout" @click="handleLogout">
        <LogOut class="nav-icon" :size="18" />
        <span>退出登录</span>
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
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.user-box {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  border: 1px solid var(--border);
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-muted);
}

.logout:hover {
  color: var(--danger);
  background: var(--danger-subtle);
}
</style>
