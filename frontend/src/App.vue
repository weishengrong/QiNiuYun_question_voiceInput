<script setup lang="ts">
import { ref } from 'vue'
import VoiceRecorder from './components/VoiceRecorder.vue'
import TextEditor from './components/TextEditor.vue'
import HistoryRecords from './components/HistoryRecords.vue'
import type { EngineOption } from './types'

const text = ref('')
const currentRecordId = ref<number | null>(null)
const engine = ref('stepfun_stream')
const errorMsg = ref('')
const liveDraft = ref('')

const engines: EngineOption[] = [
  { label: 'StepFun 实时', value: 'stepfun_stream', mode: 'stream' },
  { label: 'Vosk 离线', value: 'vosk', mode: 'upload' },
]

function onRecognized(recognizedText: string, recordId: number) {
  text.value = recognizedText
  currentRecordId.value = recordId
  liveDraft.value = ''
  errorMsg.value = ''
}

function onLiveDelta(partialText: string) {
  liveDraft.value = partialText
}

function onLiveCompleted(finalText: string) {
  const separator = text.value && !/[，。！？；：,.!?;\s]$/.test(text.value) ? ' ' : ''
  text.value = `${text.value}${separator}${finalText}`
  liveDraft.value = ''
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

function getEngineMode(value: string): 'stream' | 'upload' {
  return engines.find((item) => item.value === value)?.mode ?? 'upload'
}
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-left">
        <span class="app-logo" aria-hidden="true"></span>
        <div>
          <h1 class="app-title">语音输入法</h1>
          <p class="app-subtitle">边说边写，停顿后自动修正文稿</p>
        </div>
      </div>
      <div class="header-right">
        <label class="engine-label">识别引擎</label>
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

      <section class="metrics-row" aria-label="产品能力">
        <div class="metric-item">
          <strong>实时</strong>
          <span>100ms 音频分片</span>
        </div>
        <div class="metric-item">
          <strong>准确</strong>
          <span>增量预览 + 最终回填</span>
        </div>
        <div class="metric-item">
          <strong>可控</strong>
          <span>StepFun 流式 / Vosk 离线</span>
        </div>
      </section>

      <section class="editor-section">
        <TextEditor
          :text="liveDraft ? `${text}${text ? ' ' : ''}${liveDraft}` : text"
          @update:text="onUpdateText"
        />
      </section>

      <section class="recorder-section">
        <VoiceRecorder
          :engine="engine"
          :mode="getEngineMode(engine)"
          @recognized="onRecognized"
          @live-delta="onLiveDelta"
          @live-completed="onLiveCompleted"
          @error="onError"
        />
      </section>

      <section class="history-section">
        <HistoryRecords @reuse="onReuse" />
      </section>
    </main>

    <footer class="app-footer">
      <span>支持 StepFun 实时流式识别 · Vosk 离线回退</span>
    </footer>
  </div>
</template>

<style scoped>
.app-container {
  max-width: 880px;
  margin: 0 auto;
  padding: 0 20px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0 16px;
  border-bottom: 1px solid #dbe3ea;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-logo {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background:
    linear-gradient(90deg, transparent 10px, #ffffff 10px 13px, transparent 13px 18px, #ffffff 18px 21px, transparent 21px 26px, #ffffff 26px 29px, transparent 29px),
    #0f766e;
  box-shadow: 0 8px 18px rgba(15, 118, 110, 0.24);
}

.app-title {
  font-size: 22px;
  font-weight: 700;
  color: #172033;
  margin: 0;
}

.app-subtitle {
  margin: 2px 0 0;
  color: #607086;
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.engine-label {
  font-size: 14px;
  color: #607086;
}

.engine-select {
  padding: 8px 30px 8px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font-size: 14px;
  color: #172033;
  background: #fff;
  outline: none;
  cursor: pointer;
  transition: border-color 0.15s;
}

.engine-select:focus {
  border-color: #0f766e;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px 0;
  position: relative;
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-item {
  min-height: 66px;
  padding: 12px 14px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #fff;
}

.metric-item strong,
.metric-item span {
  display: block;
}

.metric-item strong {
  color: #172033;
  font-size: 16px;
}

.metric-item span {
  margin-top: 3px;
  color: #607086;
  font-size: 13px;
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

@media (max-width: 640px) {
  .app-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .engine-select {
    flex: 1;
  }

  .metrics-row {
    grid-template-columns: 1fr;
  }
}
</style>
