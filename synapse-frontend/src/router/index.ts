import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
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
        }
      ]
    }
  ]
})

export default router
