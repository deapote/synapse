<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Plus, RefreshCw, Shield, UserCheck, UserX } from 'lucide-vue-next'
import * as authApi from '@/api/auth'
import type { AuthPermission, RoleName, RoleView, UserAdminView } from '@/types'

const users = ref<UserAdminView[]>([])
const roles = ref<RoleView[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const newUsername = ref('')
const newDisplayName = ref('')
const newPassword = ref('')
const newRoles = ref<RoleName[]>(['USER'])

const permissions: AuthPermission[] = ['KB_READ', 'KB_WRITE', 'KB_DELETE', 'AUTH_ADMIN']
const roleNames: RoleName[] = ['ADMIN', 'USER']

const rolePermissionMap = computed<Record<RoleName, AuthPermission[]>>(() => ({
  ADMIN: roles.value.find((r) => r.name === 'ADMIN')?.permissions ?? [],
  USER: roles.value.find((r) => r.name === 'USER')?.permissions ?? []
}))

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
  if (!newUsername.value.trim() || newPassword.value.length < 8) return
  saving.value = true
  error.value = null
  try {
    const user = await authApi.createUser({
      username: newUsername.value.trim(),
      displayName: newDisplayName.value.trim() || undefined,
      password: newPassword.value,
      roles: newRoles.value
    })
    users.value.unshift(user)
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
  const nextRoles = user.roles.includes(role)
    ? user.roles.filter((r) => r !== role)
    : [...user.roles, role]
  if (nextRoles.length === 0) return
  error.value = null
  try {
    const updated = await authApi.assignRoles(user.id, nextRoles)
    replaceUser(updated)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新用户角色失败'
  }
}

async function toggleEnabled(user: UserAdminView) {
  error.value = null
  try {
    const updated = await authApi.setEnabled(user.id, !user.enabled)
    replaceUser(updated)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新用户状态失败'
  }
}

async function togglePermission(roleName: RoleName, permission: AuthPermission) {
  const current = rolePermissionMap.value[roleName]
  const next = current.includes(permission)
    ? current.filter((p) => p !== permission)
    : [...current, permission]
  if (next.length === 0) return
  error.value = null
  try {
    const updated = await authApi.assignRolePermissions(roleName, next)
    roles.value = roles.value.map((role) => role.name === roleName ? updated : role)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新角色权限失败'
  }
}

function replaceUser(user: UserAdminView) {
  users.value = users.value.map((item) => item.id === user.id ? user : item)
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>权限管理</h1>
        <p class="subtitle">管理用户、角色和接口权限</p>
      </div>
      <button class="btn btn-secondary" @click="refresh">
        <RefreshCw :size="15" />
        刷新
      </button>
    </div>

    <div v-if="error" class="alert alert-error">{{ error }}</div>

    <section class="section">
      <div class="section-title">
        <Plus :size="17" />
        新建用户
      </div>
      <div class="create-grid">
        <input v-model="newUsername" placeholder="用户名" />
        <input v-model="newDisplayName" placeholder="显示名" />
        <input v-model="newPassword" type="password" placeholder="初始密码，至少8位" />
        <select v-model="newRoles" multiple>
          <option v-for="role in roleNames" :key="role" :value="role">{{ role }}</option>
        </select>
        <button class="btn btn-primary" :disabled="saving" @click="createUser">
          {{ saving ? '创建中...' : '创建用户' }}
        </button>
      </div>
    </section>

    <section class="section">
      <div class="section-title">
        <Shield :size="17" />
        角色权限
      </div>
      <div class="role-grid">
        <div v-for="role in roleNames" :key="role" class="role-row">
          <div class="role-name">{{ role }}</div>
          <label v-for="permission in permissions" :key="permission" class="check-pill">
            <input
              type="checkbox"
              :checked="rolePermissionMap[role].includes(permission)"
              @change="togglePermission(role, permission)"
            />
            {{ permission }}
          </label>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="section-title">
        <UserCheck :size="17" />
        用户
      </div>
      <div v-if="loading" class="loading">加载中...</div>
      <table v-else>
        <thead>
          <tr>
            <th>用户</th>
            <th>角色</th>
            <th>状态</th>
            <th>创建时间</th>
            <th style="width: 96px;"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>
              <div class="cell-title">{{ user.displayName || user.username }}</div>
              <div class="cell-meta">{{ user.username }}</div>
            </td>
            <td>
              <label v-for="role in roleNames" :key="role" class="check-pill">
                <input
                  type="checkbox"
                  :checked="user.roles.includes(role)"
                  @change="toggleRole(user, role)"
                />
                {{ role }}
              </label>
            </td>
            <td>
              <span class="state" :class="{ off: !user.enabled }">
                {{ user.enabled ? '启用' : '停用' }}
              </span>
            </td>
            <td class="cell-meta">{{ new Date(user.createdAt).toLocaleString('zh-CN') }}</td>
            <td>
              <button class="btn-icon" :title="user.enabled ? '停用' : '启用'" @click="toggleEnabled(user)">
                <UserX v-if="user.enabled" :size="15" />
                <UserCheck v-else :size="15" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.page {
  padding: 32px 40px;
  max-width: 1100px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

h1 {
  font-size: 24px;
  font-weight: 600;
}

.subtitle {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 14px;
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

.section {
  margin-bottom: 28px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-weight: 600;
}

.create-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

input,
select {
  min-height: 38px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  background: var(--bg-base);
}

input:focus,
select:focus {
  outline: none;
  border-color: var(--text-primary);
}

.role-grid {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.role-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
}

.role-row:last-child {
  border-bottom: none;
}

.role-name {
  width: 80px;
  font-weight: 600;
}

.check-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 9px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-secondary);
  margin-right: 6px;
}

table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

th,
td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  vertical-align: middle;
}

th {
  font-size: 12px;
  color: var(--text-muted);
  background: var(--bg-subtle);
  font-weight: 500;
}

.cell-title {
  font-weight: 500;
}

.cell-meta {
  font-size: 12px;
  color: var(--text-muted);
}

.state {
  color: var(--success);
  font-weight: 500;
}

.state.off {
  color: var(--danger);
}

.btn,
.btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.btn {
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 500;
}

.btn-primary {
  background: var(--text-primary);
  color: #fff;
}

.btn-secondary {
  background: transparent;
  border: 1px solid var(--border-strong);
}

.btn-icon {
  width: 32px;
  height: 32px;
  color: var(--text-secondary);
  background: transparent;
}

.btn-icon:hover {
  background: var(--bg-hover);
}

.loading {
  color: var(--text-muted);
  padding: 24px 0;
}

@media (max-width: 900px) {
  .create-grid {
    grid-template-columns: 1fr;
  }

  .role-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
