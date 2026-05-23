<script setup lang="ts">
import { ref } from 'vue'
import VoiceRecorder from './components/VoiceRecorder.vue'
import TextEditor from './components/TextEditor.vue'
import HistoryRecords from './components/HistoryRecords.vue'
import type { EngineOption } from './types'

const text = ref('')
const currentRecordId = ref<number | null>(null)
const engine = ref('vosk')

const engines: EngineOption[] = [
  { label: 'Vosk 离线', value: 'vosk' },
  { label: '七牛云', value: 'qiniu' },
]

function onRecognized(recognizedText: string, recordId: number) {
  text.value = recognizedText
  currentRecordId.value = recordId
  errorMsg.value = ''
}

function onError(message: string) {
  errorMsg.value = message
  setTimeout(() => {
    errorMsg.value = ''
  }, 8000)
}

function onReuse(reuseText: string) {
  text.value = reuseText
}

function onUpdateText(val: string) {
  text.value = val
}
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-left">
        <span class="app-logo">🎤</span>
        <h1 class="app-title">语音输入法</h1>
      </div>
      <div class="header-right">
        <label class="engine-label">引擎：</label>
        <select v-model="engine" class="engine-select">
          <option
            v-for="opt in engines"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </option>
        </select>
      </div>
    </header>

    <main class="app-main">
      <div v-if="errorMsg" class="error-toast">{{ errorMsg }}</div>

      <section class="editor-section">
        <TextEditor
          :text="text"
          @update:text="onUpdateText"
        />
      </section>

      <section class="recorder-section">
        <VoiceRecorder
          :engine="engine"
          @recognized="onRecognized"
          @error="onError"
        />
      </section>

      <section class="history-section">
        <HistoryRecords @reuse="onReuse" />
      </section>
    </main>

    <footer class="app-footer">
      <span>基于浏览器 MediaRecorder API · 支持七牛云/Vosk 语音识别</span>
    </footer>
  </div>
</template>

<style scoped>
.app-container {
  max-width: 720px;
  margin: 0 auto;
  padding: 0 16px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid #e5e7eb;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-logo {
  font-size: 28px;
}

.app-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.engine-label {
  font-size: 14px;
  color: #6b7280;
}

.engine-select {
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 14px;
  color: #374151;
  background: #fff;
  outline: none;
  cursor: pointer;
  transition: border-color 0.15s;
}

.engine-select:focus {
  border-color: #4f46e5;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 0;
  position: relative;
}

.editor-section {
  flex-shrink: 0;
}

.recorder-section {
  flex-shrink: 0;
}

.history-section {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.error-toast {
  position: fixed;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 20px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #991b1b;
  font-size: 14px;
  z-index: 100;
  max-width: 520px;
  width: calc(100% - 32px);
  white-space: pre-line;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.app-footer {
  text-align: center;
  padding: 12px 0;
  border-top: 1px solid #e5e7eb;
  font-size: 12px;
  color: #9ca3af;
}
</style>