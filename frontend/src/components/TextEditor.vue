<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  text: string
}>()

const emit = defineEmits<{
  'update:text': [value: string]
}>()

const editorRef = ref<HTMLDivElement | null>(null)

watch(
  () => props.text,
  (val) => {
    if (editorRef.value && document.activeElement !== editorRef.value) {
      editorRef.value.textContent = val
    }
  },
)

function onInput(e: Event) {
  const el = e.target as HTMLDivElement
  emit('update:text', el.textContent || '')
}

function copyText() {
  const text = editorRef.value?.textContent || ''
  navigator.clipboard.writeText(text).then(() => {
    const btn = document.activeElement as HTMLElement | null
    if (btn) {
      const orig = btn.textContent
      btn.textContent = '已复制'
      setTimeout(() => {
        btn.textContent = orig
      }, 1200)
    }
  })
}

function clearText() {
  if (editorRef.value) {
    editorRef.value.textContent = ''
  }
  emit('update:text', '')
}
</script>

<template>
  <div class="text-editor">
    <div
      ref="editorRef"
      class="editor-area"
      contenteditable="true"
      :data-placeholder="text ? '' : '开始说话，识别结果会实时出现在这里...'"
      @input="onInput"
    ></div>
    <div class="editor-toolbar">
      <button class="tool-btn" :disabled="!text" @click="copyText">
        📋 复制
      </button>
      <button class="tool-btn" :disabled="!text" @click="clearText">
        🗑 清空
      </button>
    </div>
  </div>
</template>

<style scoped>
.text-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.editor-area {
  min-height: 168px;
  max-height: 320px;
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  font-size: 16px;
  line-height: 1.7;
  overflow-y: auto;
  outline: none;
  background: #fff;
  transition: border-color 0.2s;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.editor-area:focus {
  border-color: #0f766e;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

.editor-area:empty::before {
  content: attr(data-placeholder);
  color: #d1d5db;
  pointer-events: none;
}

.editor-toolbar {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.tool-btn {
  padding: 6px 16px;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  color: #334155;
  transition: all 0.15s;
}

.tool-btn:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.tool-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
