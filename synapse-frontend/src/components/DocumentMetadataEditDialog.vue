<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Document, DocumentSourceType } from '@/types'
import { X } from 'lucide-vue-next'

const props = defineProps<{
  document: Document | null
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
  save: [patch: { sourceType?: string | null; canonicalKey?: string | null; versionLabel?: string | null; effectiveFrom?: string | null; effectiveTo?: string | null; authorityLevel?: number | null; jurisdiction?: string | null }]
}>()

const form = ref({
  sourceType: '' as DocumentSourceType | '',
  canonicalKey: '',
  versionLabel: '',
  effectiveFrom: '',
  effectiveTo: '',
  authorityLevel: 0,
  jurisdiction: '',
  clearCanonicalKey: false,
  clearVersionLabel: false,
  clearEffectiveTo: false,
  clearJurisdiction: false
})

watch(() => props.document, (doc) => {
  if (doc) {
    form.value.sourceType = doc.sourceType || ''
    form.value.canonicalKey = doc.canonicalKey || ''
    form.value.versionLabel = doc.versionLabel || ''
    form.value.effectiveFrom = doc.effectiveFrom || ''
    form.value.effectiveTo = doc.effectiveTo || ''
    form.value.authorityLevel = doc.authorityLevel || 0
    form.value.jurisdiction = doc.jurisdiction || ''
    form.value.clearCanonicalKey = false
    form.value.clearVersionLabel = false
    form.value.clearEffectiveTo = false
    form.value.clearJurisdiction = false
  }
}, { immediate: true })

function handleSave() {
  const patch: Record<string, unknown> = {}

  if (form.value.sourceType) {
    patch.sourceType = form.value.sourceType
  }

  if (form.value.clearCanonicalKey) {
    patch.canonicalKey = null
  } else if (form.value.canonicalKey !== props.document?.canonicalKey) {
    patch.canonicalKey = form.value.canonicalKey || null
  }

  if (form.value.clearVersionLabel) {
    patch.versionLabel = null
  } else if (form.value.versionLabel !== props.document?.versionLabel) {
    patch.versionLabel = form.value.versionLabel || null
  }

  if (form.value.effectiveFrom !== props.document?.effectiveFrom) {
    patch.effectiveFrom = form.value.effectiveFrom || null
  }

  if (form.value.clearEffectiveTo) {
    patch.effectiveTo = null
  } else if (form.value.effectiveTo !== props.document?.effectiveTo) {
    patch.effectiveTo = form.value.effectiveTo || null
  }

  if (form.value.authorityLevel !== props.document?.authorityLevel) {
    patch.authorityLevel = form.value.authorityLevel
  }

  if (form.value.clearJurisdiction) {
    patch.jurisdiction = null
  } else if (form.value.jurisdiction !== props.document?.jurisdiction) {
    patch.jurisdiction = form.value.jurisdiction || null
  }

  emit('save', patch)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="dialog-overlay" @click="emit('close')">
        <div class="dialog" @click.stop>
          <div class="dialog-header">
            <h3>编辑文档元数据</h3>
            <button class="btn-icon" @click="emit('close')">
              <X :size="16" />
            </button>
          </div>

          <div class="dialog-body">
            <div class="form-group">
              <label class="form-label">资料类型</label>
              <select v-model="form.sourceType" class="form-select">
                <option value="">不修改</option>
                <option value="GENERAL">普通资料</option>
                <option value="LEGAL">法规</option>
                <option value="POLICY">政策</option>
              </select>
            </div>

            <div class="form-group">
              <label class="form-label">规范标识</label>
              <div class="field-row">
                <input v-model="form.canonicalKey" type="text" class="form-input" :disabled="form.clearCanonicalKey" />
                <label class="clear-check">
                  <input v-model="form.clearCanonicalKey" type="checkbox" />
                  清空
                </label>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">版本标签</label>
              <div class="field-row">
                <input v-model="form.versionLabel" type="text" class="form-input" :disabled="form.clearVersionLabel" />
                <label class="clear-check">
                  <input v-model="form.clearVersionLabel" type="checkbox" />
                  清空
                </label>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-1">
                <label class="form-label">生效日期</label>
                <input v-model="form.effectiveFrom" type="date" class="form-input" />
              </div>
              <div class="form-group flex-1">
                <label class="form-label">结束日期</label>
                <div class="field-row">
                  <input v-model="form.effectiveTo" type="date" class="form-input" :disabled="form.clearEffectiveTo" />
                  <label class="clear-check">
                    <input v-model="form.clearEffectiveTo" type="checkbox" />
                    清空
                  </label>
                </div>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">权威等级</label>
              <input v-model.number="form.authorityLevel" type="number" class="form-input" min="0" />
            </div>

            <div class="form-group">
              <label class="form-label">适用区域</label>
              <div class="field-row">
                <input v-model="form.jurisdiction" type="text" class="form-input" :disabled="form.clearJurisdiction" />
                <label class="clear-check">
                  <input v-model="form.clearJurisdiction" type="checkbox" />
                  清空
                </label>
              </div>
            </div>
          </div>

          <div class="dialog-footer">
            <button class="btn btn-secondary" @click="emit('close')">取消</button>
            <button class="btn btn-primary" @click="handleSave">保存</button>
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
  max-width: 520px;
  max-height: 90vh;
  overflow-y: auto;
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
  gap: 14px;
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

.form-row {
  display: flex;
  gap: 12px;
}

.flex-1 {
  flex: 1;
}

.form-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.field-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-input,
.form-select {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-size: 13px;
  background: var(--bg-base);
  color: var(--text-primary);
  outline: none;
  font-family: inherit;
}

.form-input:disabled,
.form-select:disabled {
  opacity: 0.5;
  background: var(--bg-subtle);
}

.clear-check {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  cursor: pointer;
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

.btn-primary:hover {
  background: #333;
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
