<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogIn } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const username = ref('admin')
const password = ref('')
const submitting = ref(false)

async function handleLogin() {
  if (!username.value.trim() || !password.value) return
  submitting.value = true
  try {
    await authStore.login({
      username: username.value.trim(),
      password: password.value
    })
    const redirect = typeof route.query.redirect === 'string'
      ? route.query.redirect
      : '/knowledge-bases'
    router.replace(redirect)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="brand">Synapse</div>
      <h1>登录知识库</h1>
      <p class="subtitle">使用账号进入受保护的知识库工作台</p>

      <div v-if="authStore.error" class="alert alert-error">
        {{ authStore.error }}
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <label>
          用户名
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            placeholder="admin"
          />
        </label>
        <label>
          密码
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </label>
        <button
          class="btn-login"
          type="submit"
          :disabled="submitting || !username.trim() || !password"
        >
          <LogIn :size="16" />
          {{ submitting ? '登录中...' : '登录' }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--bg-subtle);
}

.login-panel {
  width: 100%;
  max-width: 380px;
  padding: 32px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-base);
  box-shadow: var(--shadow-md);
}

.brand {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 28px;
}

h1 {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 6px;
}

.subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 22px;
}

.alert {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  margin-bottom: 16px;
}

.alert-error {
  background: var(--danger-subtle);
  color: var(--danger);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
}

input {
  height: 38px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  padding: 0 12px;
  font-size: 14px;
}

input:focus {
  outline: none;
  border-color: var(--text-primary);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.04);
}

.btn-login {
  height: 40px;
  margin-top: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--text-primary);
  color: #fff;
  font-weight: 500;
}

.btn-login:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
