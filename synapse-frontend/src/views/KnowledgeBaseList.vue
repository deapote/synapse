<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useKnowledgeBaseStore } from '@/stores/knowledgeBase'
import { Plus, Trash2, MessageSquare, ChevronRight } from 'lucide-vue-next'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const kbStore = useKnowledgeBaseStore()

const showCreateModal = ref(false)
const newKbName = ref('')
const newKbDesc = ref('')
const isCreating = ref(false)

const showDeleteDialog = ref(false)
const deleteTargetId = ref('')
const deleteTargetName = ref('')
const isDeleting = ref(false)

onMounted(() => {
  kbStore.fetchList().catch(() => {})
})

async function handleCreate() {
  if (!newKbName.value.trim()) return
  isCreating.value = true
  try {
    await kbStore.create({
      name: newKbName.value.trim(),
      description: newKbDesc.value.trim() || undefined
    })
    showCreateModal.value = false
    newKbName.value = ''
    newKbDesc.value = ''
  } finally {
    isCreating.value = false
  }
}

function openDeleteDialog(kb: { id: string; name: string }) {
  deleteTargetId.value = kb.id
  deleteTargetName.value = kb.name
  showDeleteDialog.value = true
}

async function handleDelete() {
  isDeleting.value = true
  try {
    await kbStore.remove(deleteTargetId.value)
    showDeleteDialog.value = false
  } finally {
    isDeleting.value = false
  }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>知识库</h1>
        <p class="subtitle">管理和组织你的文档集合</p>
      </div>
      <button class="btn btn-primary" @click="showCreateModal = true">
        <Plus :size="16" />
        新建知识库
      </button>
    </div>

    <div v-if="kbStore.error" class="alert alert-error">
      {{ kbStore.error }}
    </div>

    <div v-if="kbStore.loading" class="loading">加载中...</div>

    <template v-else>
      <EmptyState
        v-if="kbStore.list.length === 0"
        title="暂无知识库"
        description="创建一个知识库，开始上传文档"
      />

      <div v-else class="kb-list">
        <div
          v-for="kb in kbStore.list"
          :key="kb.id"
          class="kb-card"
          @click="router.push(`/knowledge-bases/${kb.id}`)"
        >
          <div class="kb-card-main">
            <div class="kb-card-header">
              <h3 class="kb-name">{{ kb.name }}</h3>
              <span class="kb-date">{{ formatDate(kb.createdAt) }}</span>
            </div>
            <p v-if="kb.description" class="kb-desc">{{ kb.description }}</p>
            <div class="kb-meta">
              <span class="kb-id">ID: {{ kb.id }}</span>
            </div>
          </div>
          <div class="kb-actions" @click.stop>
            <button
              class="btn-icon"
              title="进入问答"
              @click="router.push(`/knowledge-bases/${kb.id}/chat`)"
            >
              <MessageSquare :size="16" />
            </button>
            <button
              class="btn-icon btn-icon-danger"
              title="删除"
              @click="openDeleteDialog(kb)"
            >
              <Trash2 :size="16" />
            </button>
            <ChevronRight :size="16" class="chevron" />
          </div>
        </div>
      </div>
    </template>

    <!-- Create Modal -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showCreateModal" class="modal-overlay" @click="showCreateModal = false">
          <div class="modal" @click.stop>
            <h3 class="modal-title">新建知识库</h3>
            <div class="form-group">
              <label>名称 <span class="required">*</span></label>
              <input
                v-model="newKbName"
                type="text"
                placeholder="例如：产品手册"
                maxlength="200"
                @keyup.enter="handleCreate"
              />
            </div>
            <div class="form-group">
              <label>描述</label>
              <textarea
                v-model="newKbDesc"
                rows="3"
                placeholder="简要描述这个知识库的用途"
              />
            </div>
            <div class="modal-actions">
              <button class="btn btn-secondary" @click="showCreateModal = false">
                取消
              </button>
              <button
                class="btn btn-primary"
                :disabled="!newKbName.trim() || isCreating"
                @click="handleCreate"
              >
                {{ isCreating ? '创建中...' : '创建' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Delete Confirm -->
    <ConfirmDialog
      :visible="showDeleteDialog"
      title="删除知识库"
      :message="`确定要删除「${deleteTargetName}」吗？该操作将同时删除其下的所有文档及向量数据，不可恢复。`"
      confirm-text="删除"
      danger
      @confirm="handleDelete"
      @cancel="showDeleteDialog = false"
    />
  </div>
</template>

<style scoped>
.page {
  padding: 32px 40px;
  max-width: 960px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 28px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
  margin-bottom: 4px;
}

.subtitle {
  color: var(--text-secondary);
  font-size: 14px;
}

.alert {
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  margin-bottom: 16px;
}

.alert-error {
  background: var(--danger-subtle);
  color: var(--danger);
  border: 1px solid rgba(220, 38, 38, 0.15);
}

.loading {
  text-align: center;
  padding: 60px;
  color: var(--text-muted);
  font-size: 14px;
}

/* KB Cards */
.kb-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kb-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s ease;
  background: var(--bg-base);
}

.kb-card:hover {
  border-color: var(--border-strong);
  background: var(--bg-subtle);
}

.kb-card-main {
  flex: 1;
  min-width: 0;
}

.kb-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.kb-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.kb-date {
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.kb-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 8px;
}

.kb-meta {
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.kb-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.kb-card:hover .kb-actions {
  opacity: 1;
}

.btn-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.btn-icon:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.btn-icon-danger:hover {
  background: var(--danger-subtle);
  color: var(--danger);
}

.chevron {
  color: var(--text-muted);
  margin-left: 4px;
}

/* Buttons */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  border: none;
  font-family: inherit;
}

.btn-primary {
  background: var(--text-primary);
  color: #fff;
}
.btn-primary:hover:not(:disabled) {
  background: #333;
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: transparent;
  color: var(--text-primary);
  border: 1px solid var(--border-strong);
}
.btn-secondary:hover {
  background: var(--bg-hover);
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
}

.modal {
  background: var(--bg-base);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
  width: 100%;
  max-width: 440px;
  box-shadow: var(--shadow-lg);
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
  color: var(--text-primary);
}

.required {
  color: var(--danger);
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-family: inherit;
  background: var(--bg-base);
  color: var(--text-primary);
  transition: border-color 0.15s, box-shadow 0.15s;
  resize: vertical;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--text-primary);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.04);
}

.form-group input::placeholder,
.form-group textarea::placeholder {
  color: var(--text-muted);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
