<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Check,
  KeyRound,
  Loader2,
  Plus,
  RefreshCw,
  ShieldCheck,
  ShieldQuestion,
  UserCog,
  UserMinus,
  UserPlus
} from 'lucide-vue-next'
import * as authApi from '@/api/auth'
import type { AuthPermission, RoleName, RoleView, UserAdminView } from '@/types'

const users = ref<UserAdminView[]>([])
const roles = ref<RoleView[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const pendingRoleUsers = ref<Set<string>>(new Set())
const pendingStateUsers = ref<Set<string>>(new Set())
const pendingPermissions = ref<Set<string>>(new Set())

const newUsername = ref('')
const newDisplayName = ref('')
const newPassword = ref('')
const newRoles = ref<RoleName[]>(['USER'])

const roleNames: RoleName[] = ['ADMIN', 'USER']
const permissionGroups: Array<{
  title: string
  permissions: Array<{ name: AuthPermission; label: string; description: string }>
}> = [
  {
    title: '知识库',
    permissions: [
      { name: 'KB_READ', label: '读取', description: '查看知识库、文档和问答内容' },
      { name: 'KB_WRITE', label: '写入', description: '创建知识库、上传文档和发起问答' },
      { name: 'KB_DELETE', label: '删除', description: '删除知识库和文档' }
    ]
  },
  {
    title: '系统',
    permissions: [
      { name: 'AUTH_ADMIN', label: '权限管理', description: '管理用户、角色和接口权限' }
    ]
  }
]

const allPermissions = computed<AuthPermission[]>(() =>
  permissionGroups.flatMap((group) => group.permissions.map((permission) => permission.name))
)

const rolePermissionMap = computed<Record<RoleName, AuthPermission[]>>(() => ({
  ADMIN: roles.value.find((role) => role.name === 'ADMIN')?.permissions ?? [],
  USER: roles.value.find((role) => role.name === 'USER')?.permissions ?? []
}))

const adminCount = computed(() => users.value.filter((user) => user.roles.includes('ADMIN')).length)
const enabledCount = computed(() => users.value.filter((user) => user.enabled).length)
const disabledCount = computed(() => users.value.length - enabledCount.value)
const createDisabled = computed(() =>
  saving.value ||
  !newUsername.value.trim() ||
  newPassword.value.length < 8 ||
  newRoles.value.length === 0
)

onMounted(() => {
  refresh()
})

async function refresh() {
  loading.value = true
  error.value = null
  try {
    const [userList, roleList] = await Promise.all([
      authApi.listUsers(),
      authApi.listRoles()
    ])
    users.value = userList
    roles.value = roleList
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载权限数据失败'
  } finally {
    loading.value = false
  }
}

async function createUser() {
  if (createDisabled.value) return
  saving.value = true
  error.value = null
  try {
    const user = await authApi.createUser({
      username: newUsername.value.trim(),
      displayName: newDisplayName.value.trim() || undefined,
      password: newPassword.value,
      roles: [...newRoles.value]
    })
    users.value = [user, ...users.value]
    newUsername.value = ''
    newDisplayName.value = ''
    newPassword.value = ''
    newRoles.value = ['USER']
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建用户失败'
  } finally {
    saving.value = false
  }
}

async function toggleRole(user: UserAdminView, role: RoleName) {
  if (pendingRoleUsers.value.has(user.id)) return
  const nextRoles = user.roles.includes(role)
    ? user.roles.filter((item) => item !== role)
    : [...user.roles, role]
  if (nextRoles.length === 0) return
  pendingRoleUsers.value.add(user.id)
  error.value = null
  try {
    const updated = await authApi.assignRoles(user.id, nextRoles)
    replaceUser(updated)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新用户角色失败'
  } finally {
    pendingRoleUsers.value.delete(user.id)
  }
}

async function toggleEnabled(user: UserAdminView) {
  if (pendingStateUsers.value.has(user.id)) return
  pendingStateUsers.value.add(user.id)
  error.value = null
  try {
    const updated = await authApi.setEnabled(user.id, !user.enabled)
    replaceUser(updated)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新用户状态失败'
  } finally {
    pendingStateUsers.value.delete(user.id)
  }
}

async function togglePermission(roleName: RoleName, permission: AuthPermission) {
  const key = `${roleName}:${permission}`
  if (pendingPermissions.value.has(key)) return
  const current = rolePermissionMap.value[roleName]
  const next = current.includes(permission)
    ? current.filter((item) => item !== permission)
    : [...current, permission]
  if (next.length === 0) return
  pendingPermissions.value.add(key)
  error.value = null
  try {
    const updated = await authApi.assignRolePermissions(roleName, next)
    roles.value = roles.value.map((role) => role.name === roleName ? updated : role)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新角色权限失败'
  } finally {
    pendingPermissions.value.delete(key)
  }
}

function toggleNewRole(role: RoleName) {
  const nextRoles = newRoles.value.includes(role)
    ? newRoles.value.filter((item) => item !== role)
    : [...newRoles.value, role]
  if (nextRoles.length === 0) return
  newRoles.value = nextRoles
}

function replaceUser(user: UserAdminView) {
  users.value = users.value.map((item) => item.id === user.id ? user : item)
}

function hasPermission(role: RoleName, permission: AuthPermission) {
  return rolePermissionMap.value[role].includes(permission)
}

function isPermissionPending(role: RoleName, permission: AuthPermission) {
  return pendingPermissions.value.has(`${role}:${permission}`)
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <div>
        <h1>权限管理</h1>
        <p class="subtitle">用户、角色和接口权限</p>
      </div>
      <button class="btn btn-secondary" :disabled="loading" @click="refresh">
        <Loader2 v-if="loading" class="spin" :size="16" />
        <RefreshCw v-else :size="16" />
        刷新
      </button>
    </div>

    <div class="metrics-grid">
      <div class="metric">
        <div class="metric-icon"><UserCog :size="18" /></div>
        <div>
          <div class="metric-value">{{ users.length }}</div>
          <div class="metric-label">用户总数</div>
        </div>
      </div>
      <div class="metric">
        <div class="metric-icon"><ShieldCheck :size="18" /></div>
        <div>
          <div class="metric-value">{{ adminCount }}</div>
          <div class="metric-label">管理员</div>
        </div>
      </div>
      <div class="metric">
        <div class="metric-icon"><Check :size="18" /></div>
        <div>
          <div class="metric-value">{{ enabledCount }}</div>
          <div class="metric-label">已启用</div>
        </div>
      </div>
      <div class="metric">
        <div class="metric-icon muted"><UserMinus :size="18" /></div>
        <div>
          <div class="metric-value">{{ disabledCount }}</div>
          <div class="metric-label">已停用</div>
        </div>
      </div>
    </div>

    <div v-if="error" class="alert alert-error">{{ error }}</div>

    <section class="panel create-panel">
      <div class="panel-header">
        <div>
          <div class="section-title">
            <UserPlus :size="18" />
            新建用户
          </div>
          <p class="section-caption">为新账号分配初始角色</p>
        </div>
      </div>

      <div class="create-form">
        <label class="field">
          <span>用户名</span>
          <input v-model="newUsername" autocomplete="off" placeholder="username" />
        </label>
        <label class="field">
          <span>显示名</span>
          <input v-model="newDisplayName" autocomplete="off" placeholder="例如：张三" />
        </label>
        <label class="field">
          <span>初始密码</span>
          <input v-model="newPassword" type="password" autocomplete="new-password" placeholder="至少 8 位" />
        </label>
        <div class="field">
          <span>角色</span>
          <div class="segmented">
            <button
              v-for="role in roleNames"
              :key="role"
              type="button"
              :class="{ active: newRoles.includes(role) }"
              @click="toggleNewRole(role)"
            >
              {{ role }}
            </button>
          </div>
        </div>
        <button class="btn btn-primary create-action" :disabled="createDisabled" @click="createUser">
          <Loader2 v-if="saving" class="spin" :size="16" />
          <Plus v-else :size="16" />
          {{ saving ? '创建中' : '创建用户' }}
        </button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <div>
          <div class="section-title">
            <ShieldQuestion :size="18" />
            角色权限
          </div>
          <p class="section-caption">每个角色至少保留一个权限</p>
        </div>
      </div>

      <div class="permission-table" :style="{ '--permission-count': allPermissions.length }">
        <div class="permission-head role-col">角色</div>
        <template v-for="group in permissionGroups" :key="group.title">
          <div
            v-for="permission in group.permissions"
            :key="permission.name"
            class="permission-head"
          >
            <div class="permission-label">{{ permission.label }}</div>
            <div class="permission-code">{{ permission.name }}</div>
          </div>
        </template>

        <template v-for="role in roleNames" :key="role">
          <div class="role-cell">
            <div class="role-badge">{{ role }}</div>
            <div class="role-count">
              {{ rolePermissionMap[role].length }} / {{ allPermissions.length }}
            </div>
          </div>
          <template v-for="group in permissionGroups" :key="`${role}-${group.title}`">
            <button
              v-for="permission in group.permissions"
              :key="`${role}-${permission.name}`"
              type="button"
              class="permission-toggle"
              :class="{ active: hasPermission(role, permission.name) }"
              :disabled="isPermissionPending(role, permission.name)"
              :title="permission.description"
              @click="togglePermission(role, permission.name)"
            >
              <Loader2 v-if="isPermissionPending(role, permission.name)" class="spin" :size="15" />
              <Check v-else-if="hasPermission(role, permission.name)" :size="15" />
              <span v-else class="empty-check" />
            </button>
          </template>
        </template>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <div>
          <div class="section-title">
            <UserCog :size="18" />
            用户
          </div>
          <p class="section-caption">调整用户角色和启用状态</p>
        </div>
      </div>

      <div v-if="loading" class="loading-row">
        <Loader2 class="spin" :size="18" />
        加载中
      </div>

      <div v-else class="user-table">
        <div class="table-header">
          <span>用户</span>
          <span>角色</span>
          <span>状态</span>
          <span>创建时间</span>
          <span></span>
        </div>

        <div v-for="user in users" :key="user.id" class="user-row">
          <div class="user-cell">
            <div class="avatar">{{ (user.displayName || user.username).slice(0, 1).toUpperCase() }}</div>
            <div>
              <div class="cell-title">{{ user.displayName || user.username }}</div>
              <div class="cell-meta">{{ user.username }}</div>
            </div>
          </div>

          <div class="role-switches">
            <button
              v-for="role in roleNames"
              :key="role"
              type="button"
              class="role-switch"
              :class="{ active: user.roles.includes(role) }"
              :disabled="pendingRoleUsers.has(user.id)"
              @click="toggleRole(user, role)"
            >
              <Loader2 v-if="pendingRoleUsers.has(user.id)" class="spin" :size="14" />
              <Check v-else-if="user.roles.includes(role)" :size="14" />
              <span v-else class="empty-check small" />
              {{ role }}
            </button>
          </div>

          <div>
            <span class="state-pill" :class="{ off: !user.enabled }">
              {{ user.enabled ? '启用' : '停用' }}
            </span>
          </div>

          <div class="cell-meta">{{ formatDate(user.createdAt) }}</div>

          <div class="row-actions">
            <button
              class="btn-icon"
              :class="{ danger: user.enabled }"
              :disabled="pendingStateUsers.has(user.id)"
              :title="user.enabled ? '停用用户' : '启用用户'"
              @click="toggleEnabled(user)"
            >
              <Loader2 v-if="pendingStateUsers.has(user.id)" class="spin" :size="16" />
              <UserMinus v-else-if="user.enabled" :size="16" />
              <UserPlus v-else :size="16" />
            </button>
          </div>
        </div>

        <div v-if="users.length === 0" class="empty-state">
          <KeyRound :size="18" />
          暂无用户
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-page {
  width: 100%;
  max-width: 1240px;
  padding: 32px 40px 56px;
}

.page-header,
.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-header {
  margin-bottom: 20px;
}

h1 {
  font-size: 26px;
  line-height: 1.2;
  font-weight: 650;
  letter-spacing: 0;
}

.subtitle,
.section-caption,
.cell-meta,
.metric-label,
.permission-code,
.role-count {
  color: var(--text-muted);
}

.subtitle {
  margin-top: 6px;
  font-size: 14px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.metric {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 76px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  border: 1px solid var(--border);
  color: var(--text-primary);
}

.metric-icon.muted {
  color: var(--text-secondary);
}

.metric-value {
  font-size: 22px;
  line-height: 1;
  font-weight: 650;
}

.metric-label {
  margin-top: 5px;
  font-size: 12px;
}

.alert {
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  margin-bottom: 16px;
  font-size: 13px;
}

.alert-error {
  color: var(--danger);
  background: var(--danger-subtle);
}

.panel {
  margin-bottom: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  overflow: hidden;
}

.panel-header {
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 650;
}

.section-caption {
  margin-top: 4px;
  font-size: 12px;
}

.create-form {
  display: grid;
  grid-template-columns: 1.1fr 1.1fr 1.2fr 1fr 144px;
  gap: 12px;
  align-items: end;
  padding: 18px 20px 20px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
}

input {
  width: 100%;
  height: 40px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  padding: 0 11px;
  background: var(--bg-base);
  color: var(--text-primary);
  font-size: 14px;
}

input:focus {
  outline: none;
  border-color: var(--text-primary);
  box-shadow: 0 0 0 3px rgba(17, 17, 17, 0.06);
}

.segmented,
.role-switches {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.segmented button,
.role-switch,
.permission-toggle,
.btn,
.btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  color: var(--text-secondary);
  font: inherit;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.segmented button {
  height: 40px;
  min-width: 76px;
  padding: 0 14px;
  font-weight: 600;
}

.segmented button.active,
.role-switch.active {
  border-color: var(--text-primary);
  background: var(--text-primary);
  color: #fff;
}

.permission-toggle.active {
  border-color: var(--border-strong);
  background: var(--bg-subtle);
  color: var(--text-primary);
}

.permission-toggle.active svg {
  width: 22px;
  height: 22px;
  padding: 4px;
  border-radius: 6px;
  background: var(--text-primary);
  color: #fff;
}

.btn {
  height: 40px;
  gap: 7px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
}

.btn-primary {
  border-color: var(--text-primary);
  background: var(--text-primary);
  color: #fff;
}

.btn-secondary:hover:not(:disabled),
.segmented button:hover:not(:disabled),
.role-switch:hover:not(:disabled),
.btn-icon:hover:not(:disabled) {
  border-color: var(--border-strong);
  background: var(--bg-hover);
  color: var(--text-primary);
}

.btn:disabled,
.btn-icon:disabled,
.segmented button:disabled,
.role-switch:disabled,
.permission-toggle:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.create-action {
  width: 100%;
}

.permission-table {
  display: grid;
  grid-template-columns: 168px repeat(var(--permission-count), minmax(128px, 1fr));
  overflow-x: auto;
}

.permission-head,
.role-cell,
.permission-toggle {
  min-height: 70px;
  border-bottom: 1px solid var(--border);
  border-right: 1px solid var(--border);
}

.permission-head {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 12px 14px;
  background: var(--bg-subtle);
}

.role-col {
  position: sticky;
  left: 0;
  z-index: 2;
}

.role-cell {
  position: sticky;
  left: 0;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  padding: 12px 14px;
  background: var(--bg-base);
}

.permission-label,
.role-badge {
  color: var(--text-primary);
  font-weight: 650;
}

.permission-code,
.role-count {
  margin-top: 2px;
  font-size: 11px;
  font-family: var(--font-mono);
}

.permission-toggle {
  min-width: 128px;
  border-top: none;
  border-left: none;
  border-radius: 0;
}

.empty-check {
  width: 15px;
  height: 15px;
  border: 1.5px solid currentColor;
  border-radius: 4px;
}

.empty-check.small {
  width: 14px;
  height: 14px;
}

.user-table {
  overflow-x: auto;
}

.table-header,
.user-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.4fr) minmax(190px, 1.1fr) minmax(110px, 0.6fr) minmax(160px, 0.9fr) 56px;
  align-items: center;
  min-width: 860px;
}

.table-header {
  min-height: 46px;
  padding: 0 20px;
  color: var(--text-muted);
  background: var(--bg-subtle);
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  font-weight: 600;
}

.user-row {
  min-height: 76px;
  padding: 0 20px;
  border-bottom: 1px solid var(--border);
}

.user-row:last-child {
  border-bottom: none;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  font-weight: 650;
}

.cell-title {
  font-weight: 600;
}

.cell-meta {
  font-size: 12px;
}

.role-switch {
  height: 36px;
  gap: 6px;
  padding: 0 11px;
  font-size: 12px;
  font-weight: 600;
}

.state-pill {
  display: inline-flex;
  align-items: center;
  min-width: 58px;
  justify-content: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--success-subtle);
  color: var(--success);
  font-size: 12px;
  font-weight: 650;
}

.state-pill.off {
  background: var(--danger-subtle);
  color: var(--danger);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
}

.btn-icon {
  width: 34px;
  height: 34px;
}

.btn-icon.danger:hover:not(:disabled) {
  border-color: rgba(220, 38, 38, 0.28);
  color: var(--danger);
  background: var(--danger-subtle);
}

.loading-row,
.empty-state {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 80px;
  padding: 0 20px;
  color: var(--text-muted);
}

.spin {
  animation: rotate 0.85s linear infinite;
}

@keyframes rotate {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 980px) {
  .admin-page {
    padding: 24px;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .create-form {
    grid-template-columns: 1fr 1fr;
  }

  .create-action {
    width: auto;
  }
}

@media (max-width: 640px) {
  .admin-page {
    padding: 20px 16px 40px;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .metrics-grid,
  .create-form {
    grid-template-columns: 1fr;
  }
}
</style>
