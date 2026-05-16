import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          redirect: '/knowledge-bases'
        },
        {
          path: 'knowledge-bases',
          name: 'KnowledgeBaseList',
          component: () => import('@/views/KnowledgeBaseList.vue')
        },
        {
          path: 'knowledge-bases/:id',
          name: 'KnowledgeBaseDetail',
          component: () => import('@/views/KnowledgeBaseDetail.vue'),
          props: true
        },
        {
          path: 'knowledge-bases/:id/chat',
          name: 'KnowledgeBaseChat',
          component: () => import('@/views/ChatView.vue'),
          props: true
        },
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('@/views/ChatView.vue')
        },
        {
          path: 'admin',
          name: 'Admin',
          component: () => import('@/views/AdminView.vue'),
          meta: { requiresAdmin: true }
        }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    return true
  }
  try {
    if (!authStore.user) {
      await authStore.fetchMe()
    }
  } catch {
    return {
      name: 'Login',
      query: { redirect: to.fullPath }
    }
  }
  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    return '/knowledge-bases'
  }
  return true
})

export default router
