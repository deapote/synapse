<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Document } from '@/types'
import { X } from 'lucide-vue-next'

const props = defineProps<{
  oldDocument: Document | null
  visible: boolean
  documents: Document[]
}>()

const emit = defineEmits<{
  close: []
  confirm: [newDocumentId: string, effectiveTo: string]
}>()

const selectedNewDocId = ref('')
const effectiveTo = ref('')

const availableNewDocs = computed(() => {
  if (!props.oldDocument) return []
  return props.documents.filter(
    (d) => d.id !== props.oldDocument?.id && d.status === 'COMPLETED' && d.lifecycleStatus === 'ACTIVE'
  )
})

const canSubmit = computed(() => selectedNewDocId.value && effectiveTo.value)

function handleConfirm() {
  if (!canSubmit.value) return
  emit('confirm', selectedNewDocId.value, effectiveTo.value)
  selectedNewDocId.value = ''
  effectiveTo.value = ''
}

function handleClose() {
  selectedNewDocId.value = ''
  effectiveTo.value = ''
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="dialog-overlay" @click="handleClose">
        <div class="dialog" @click.stop>
          <div class="dialog-header">
            <h3>手动替代文档</h3>
            <button class="btn-icon" @click="handleClose">
              <X :size="16" />
            </button>
          </div>

          <div class="dialog-body">
            <p class="hint">
              将 <strong>{{ oldDocument?.fileName }}</strong> 标记为已替代，并指定替代文档。
            </p>

            <div class="form-group">
              <label class="form-label">选择替代文档 <span class="required">*</span></label>
              <select v-model="selectedNewDocId" class="form-select">
                <option value="">请选择</option>
                <option v-for="d in availableNewDocs" :key="d.id" :value="d.id">
                  {{ d.fileName }} {{ d.versionLabel ? `(${d.versionLabel})` : '' }}
                </option>
              </select>
            </div>

            <div class="form-group">
              <label class="form-label">替代生效日期（旧文档失效日）<span class="required">*</span></label>
              <input v-model="effectiveTo" type="date" class="form-input" />
              <span class="form-hint">旧文档将在该日期起失效</span>
            </div>
          </div>

          <div class="dialog-footer">
            <button class="btn btn-secondary" @click="handleClose">取消</button>
            <button class="btn btn-primary" :disabled="!canSubmit" @click="handleConfirm">
              确认替代
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.dialog {
  background: var(--bg-base);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  width: 100%;
  max-width: 480px;
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.dialog-header h3 {
  font-size: 16px;
  font-weight: 600;
}

.dialog-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hint {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--border);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.required {
  color: var(--danger);
}

.form-input,
.form-select {
  padding: 8px 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-size: 13px;
  background: var(--bg-base);
  color: var(--text-primary);
  outline: none;
  font-family: inherit;
}

.form-hint {
  font-size: 11px;
  color: var(--text-muted);
}

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
}

.btn-icon:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
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
