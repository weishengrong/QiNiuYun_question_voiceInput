<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { uploadAudio } from '../api'

const emit = defineEmits<{
  recognized: [text: string, recordId: number]
  liveDelta: [text: string]
  liveCompleted: [text: string]
  error: [message: string]
}>()

const props = defineProps<{
  engine: string
  mode: 'stream' | 'upload'
}>()

const recording = ref(false)
const uploading = ref(false)
const holdMode = ref(false)
const audioLevel = ref(0)
const streamStatus = ref<'idle' | 'connecting' | 'listening' | 'speaking'>('idle')
const streamMessage = ref('')

let mediaStream: MediaStream | null = null
let analyser: AnalyserNode | null = null
let animationId = 0
let recorderAudioContext: AudioContext | null = null
let recorderSource: MediaStreamAudioSourceNode | null = null
let recorderProcessor: ScriptProcessorNode | null = null
let recordedPcmChunks: Int16Array[] = []
let streamSocket: WebSocket | null = null
let streamAudioContext: AudioContext | null = null
let streamSource: MediaStreamAudioSourceNode | null = null
let streamProcessor: ScriptProcessorNode | null = null
let pendingPcm: Int16Array[] = []
let partialText = ''
let streamReady = false
let suppressNextClick = false
let pressTimer = 0
let pressStartedRecording = false

async function startRecording() {
  if (props.mode === 'stream') {
    await startStreaming()
    return
  }

  try {
    const permStatus = await navigator.permissions.query({ name: 'microphone' as PermissionName })
    if (permStatus.state === 'denied') {
      emit('error', '麦克风权限已被禁止，请在浏览器地址栏左侧的锁图标中开启麦克风权限后重试')
      return
    }
  } catch {
  }

  try {
    recordedPcmChunks = []
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    })

    recording.value = true
    await startWavRecorderPipeline()
    startAudioLevelMonitor()
  } catch (err) {
    let msg = '无法访问麦克风'
    if (err instanceof DOMException) {
      if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
        msg = '麦克风权限被拒绝。请允许浏览器使用麦克风：\n1. 点击地址栏左侧锁图标\n2. 将「麦克风」设置为「允许」\n3. 刷新页面后重试'
      } else if (err.name === 'NotFoundError') {
        msg = '未检测到麦克风设备，请确保已连接麦克风'
      } else if (err.name === 'NotReadableError') {
        msg = '麦克风被其他应用占用，请关闭其他使用麦克风的程序后重试'
      }
    }
    emit('error', msg)
  }
}

function writeString(view: DataView, offset: number, str: string) {
  for (let i = 0; i < str.length; i++) {
    view.setUint8(offset + i, str.charCodeAt(i))
  }
}

function stopRecording() {
  if (props.mode === 'stream') {
    stopStreaming()
    return
  }

  recording.value = false
  stopWavRecorderPipeline()
  stopAudioLevelMonitor()
  stopAudioStream()

  if (recordedPcmChunks.length > 0) {
    const wavBlob = pcmChunksToWavBlob(recordedPcmChunks)
    recordedPcmChunks = []
    doUpload(wavBlob)
  }
}

async function startWavRecorderPipeline() {
  if (!mediaStream) return
  recorderAudioContext = new AudioContext()
  recorderSource = recorderAudioContext.createMediaStreamSource(mediaStream)
  recorderProcessor = recorderAudioContext.createScriptProcessor(4096, 1, 1)

  recorderProcessor.onaudioprocess = (event) => {
    if (!recording.value) return
    const input = event.inputBuffer.getChannelData(0)
    recordedPcmChunks.push(resampleTo16kPcm(input, recorderAudioContext?.sampleRate ?? 48000))
  }

  recorderSource.connect(recorderProcessor)
  recorderProcessor.connect(recorderAudioContext.destination)
}

function stopWavRecorderPipeline() {
  if (recorderProcessor) {
    recorderProcessor.disconnect()
    recorderProcessor.onaudioprocess = null
    recorderProcessor = null
  }
  if (recorderSource) {
    recorderSource.disconnect()
    recorderSource = null
  }
  if (recorderAudioContext) {
    recorderAudioContext.close()
    recorderAudioContext = null
  }
}

async function startStreaming() {
  try {
    const permStatus = await navigator.permissions.query({ name: 'microphone' as PermissionName })
    if (permStatus.state === 'denied') {
      emit('error', '麦克风权限已被禁止，请在浏览器地址栏左侧的锁图标中开启麦克风权限后重试')
      return
    }
  } catch {
  }

  try {
    resetStreamingState()
    streamStatus.value = 'connecting'
    streamMessage.value = '连接实时识别...'
    pendingPcm = []
    partialText = ''

    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    })

    streamSocket = new WebSocket(getStepFunWsUrl())
    streamSocket.onopen = () => {
      recording.value = true
      streamStatus.value = 'listening'
      streamMessage.value = '等待 StepFun 配置...'
    }
    streamSocket.onmessage = (event) => handleStreamMessage(event.data)
    streamSocket.onerror = () => {
      streamMessage.value = '实时识别连接异常'
      emit('error', '实时识别连接异常，请检查后端服务和 StepFun Token 配置')
    }
    streamSocket.onclose = () => {
      if (recording.value) {
        recording.value = false
      }
      streamStatus.value = 'idle'
      streamMessage.value = ''
    }

    await startPcmPipeline()
    startAudioLevelMonitor()
  } catch (err) {
    stopStreaming()
    let msg = '无法启动实时语音输入'
    if (err instanceof DOMException && (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError')) {
      msg = '麦克风权限被拒绝，请允许浏览器使用麦克风后重试'
    }
    emit('error', msg)
  }
}

async function startPcmPipeline() {
  if (!mediaStream) return
  streamAudioContext = new AudioContext()
  streamSource = streamAudioContext.createMediaStreamSource(mediaStream)
  streamProcessor = streamAudioContext.createScriptProcessor(4096, 1, 1)

  streamProcessor.onaudioprocess = (event) => {
    if (!recording.value && streamStatus.value !== 'connecting') return
    const input = event.inputBuffer.getChannelData(0)
    const pcm = resampleTo16kPcm(input, streamAudioContext?.sampleRate ?? 48000)
    sendPcmChunk(pcm)
  }

  streamSource.connect(streamProcessor)
  streamProcessor.connect(streamAudioContext.destination)
}

function stopStreaming() {
  recording.value = false
  streamStatus.value = 'idle'
  streamMessage.value = ''
  stopPcmPipeline()
  stopAudioLevelMonitor()
  stopAudioStream()
  if (streamSocket && streamSocket.readyState === WebSocket.OPEN) {
    streamSocket.send(JSON.stringify({ type: 'session.stop' }))
    streamSocket.close()
  } else if (streamSocket) {
    streamSocket.close()
  }
  streamSocket = null
  pendingPcm = []
  partialText = ''
  streamReady = false
  streamMessage.value = ''
}

function stopPcmPipeline() {
  if (streamProcessor) {
    streamProcessor.disconnect()
    streamProcessor.onaudioprocess = null
    streamProcessor = null
  }
  if (streamSource) {
    streamSource.disconnect()
    streamSource = null
  }
  if (streamAudioContext) {
    streamAudioContext.close()
    streamAudioContext = null
  }
}

function resetStreamingState() {
  stopPcmPipeline()
  if (streamSocket) {
    streamSocket.close()
    streamSocket = null
  }
  streamReady = false
}

function handleStreamMessage(raw: string) {
  const msg = JSON.parse(raw) as { type: string; text?: string; message?: string }
  if (msg.type === 'configured') {
    streamReady = true
    streamMessage.value = '正在聆听...'
    flushPendingPcm()
  } else if (msg.type === 'ready') {
    streamMessage.value = '实时通道已连接...'
  } else if (msg.type === 'speech_started') {
    streamStatus.value = 'speaking'
    streamMessage.value = '正在转写...'
  } else if (msg.type === 'speech_stopped') {
    streamStatus.value = 'listening'
    streamMessage.value = '正在整理句子...'
  } else if (msg.type === 'delta') {
    partialText += msg.text ?? ''
    emit('liveDelta', partialText)
  } else if (msg.type === 'completed') {
    const completedText = (msg.text || partialText).trim()
    if (completedText) {
      emit('liveCompleted', completedText)
    }
    partialText = ''
  } else if (msg.type === 'error') {
    streamMessage.value = '实时识别异常'
    emit('error', msg.message || '实时识别服务异常')
  } else if (msg.type === 'closed') {
    streamMessage.value = msg.message || '实时识别已关闭'
  }
}

function sendPcmChunk(pcm: Int16Array) {
  if (!pcm.length) return
  if (!streamSocket || streamSocket.readyState !== WebSocket.OPEN || !streamReady) {
    pendingPcm.push(pcm)
    return
  }
  streamSocket.send(JSON.stringify({
    type: 'audio.append',
    audio: pcmToBase64(pcm),
  }))
}

function flushPendingPcm() {
  const chunks = pendingPcm.splice(0)
  chunks.forEach(sendPcmChunk)
}

function resampleTo16kPcm(input: Float32Array, sourceRate: number): Int16Array {
  const targetRate = 16000
  const ratio = sourceRate / targetRate
  const outputLength = Math.floor(input.length / ratio)
  const output = new Int16Array(outputLength)

  for (let i = 0; i < outputLength; i++) {
    const sourceIndex = Math.floor(i * ratio)
    const sample = Math.max(-1, Math.min(1, input[sourceIndex] ?? 0))
    output[i] = sample < 0 ? sample * 0x8000 : sample * 0x7fff
  }

  return output
}

function pcmToBase64(pcm: Int16Array): string {
  const bytes = new Uint8Array(pcm.buffer)
  let binary = ''
  const batchSize = 0x8000
  for (let i = 0; i < bytes.length; i += batchSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + batchSize))
  }
  return btoa(binary)
}

function pcmChunksToWavBlob(chunks: Int16Array[]): Blob {
  const totalSamples = chunks.reduce((sum, chunk) => sum + chunk.length, 0)
  const dataSize = totalSamples * 2
  const buffer = new ArrayBuffer(44 + dataSize)
  const view = new DataView(buffer)

  writeString(view, 0, 'RIFF')
  view.setUint32(4, 36 + dataSize, true)
  writeString(view, 8, 'WAVE')
  writeString(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, 16000, true)
  view.setUint32(28, 16000 * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeString(view, 36, 'data')
  view.setUint32(40, dataSize, true)

  let offset = 44
  for (const chunk of chunks) {
    for (let i = 0; i < chunk.length; i++) {
      view.setInt16(offset, chunk[i], true)
      offset += 2
    }
  }

  return new Blob([buffer], { type: 'audio/wav' })
}

function getStepFunWsUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/asr/stepfun`
}

function stopAudioStream() {
  if (mediaStream) {
    mediaStream.getTracks().forEach((t) => t.stop())
    mediaStream = null
  }
}

async function doUpload(blob: Blob) {
  uploading.value = true
  try {
    const res = await uploadAudio(blob, props.engine)
    if (res.code === 0 && res.data) {
      emit('recognized', res.data.originalText, res.data.recordId)
    }
  } catch (err) {
    const msg = err instanceof Error ? err.message : '识别请求失败'
    emit('error', msg)
  } finally {
    uploading.value = false
  }
}

function startAudioLevelMonitor() {
  if (!mediaStream) return
  const ctx = new AudioContext()
  analyser = ctx.createAnalyser()
  analyser.fftSize = 256
  const src = ctx.createMediaStreamSource(mediaStream)
  src.connect(analyser)
  const data = new Uint8Array(analyser.frequencyBinCount)

  function tick() {
    if (!analyser) return
    analyser.getByteFrequencyData(data)
    const avg = data.reduce((a, b) => a + b, 0) / data.length
    audioLevel.value = Math.min(avg / 128, 1)
    animationId = requestAnimationFrame(tick)
  }
  tick()
}

function stopAudioLevelMonitor() {
  if (animationId) {
    cancelAnimationFrame(animationId)
    animationId = 0
  }
  analyser = null
}

function onPointerDown() {
  pressStartedRecording = false
  if (pressTimer) {
    clearTimeout(pressTimer)
  }
  pressTimer = window.setTimeout(() => {
    if (!recording.value && !uploading.value) {
      holdMode.value = true
      pressStartedRecording = true
      startRecording()
    }
  }, 260)
}

function onPointerUp() {
  if (pressTimer) {
    clearTimeout(pressTimer)
    pressTimer = 0
  }
  if (holdMode.value && pressStartedRecording) {
    holdMode.value = false
    suppressNextClick = true
    stopRecording()
  }
}

function onClickToggle() {
  if (suppressNextClick) {
    suppressNextClick = false
    return
  }
  if (recording.value) {
    stopRecording()
  } else {
    startRecording()
  }
}

onUnmounted(() => {
  if (pressTimer) {
    clearTimeout(pressTimer)
  }
  stopRecording()
  stopWavRecorderPipeline()
  stopAudioLevelMonitor()
  stopAudioStream()
})
</script>

<template>
  <div class="voice-recorder">
    <div class="recorder-controls">
      <div class="recorder-buttons">
        <button
          class="record-btn"
          :class="{ recording, uploading }"
          :disabled="uploading"
          @pointerdown="onPointerDown"
          @pointerup="onPointerUp"
          @pointerleave="holdMode && onPointerUp()"
          @click="onClickToggle"
        >
          <span v-if="uploading" class="btn-icon spinner"></span>
          <span v-else-if="recording" class="btn-icon stop-icon"></span>
          <span v-else class="btn-icon mic-icon"></span>
        </button>
      </div>

      <div class="recorder-status">
        <div v-if="uploading" class="status-text uploading">
          <span class="loading-dot"></span> 识别中...
        </div>
        <div v-else-if="streamStatus === 'connecting'" class="status-text uploading">
          <span class="loading-dot"></span> {{ streamMessage || '连接实时识别...' }}
        </div>
        <div v-else-if="streamStatus === 'speaking'" class="status-text recording">
          <span class="rec-dot"></span> {{ streamMessage || '正在转写...' }}
        </div>
        <div v-else-if="recording" class="status-text recording">
          <span class="rec-dot"></span> {{ streamMessage || '正在聆听...' }}
        </div>
        <div v-else class="status-text idle">
          {{ mode === 'stream' ? '实时语音输入' : '点击录音 / 按住说话' }}
        </div>
      </div>
    </div>

    <div v-if="recording" class="waveform-bar">
      <div
        v-for="i in 40"
        :key="i"
        class="wave-bar"
        :style="{ height: Math.max(2, audioLevel * 24 * Math.sin((i / 40) * Math.PI * 4 + Date.now() / 200)) + 'px' }"
      ></div>
    </div>
  </div>
</template>

<style scoped>
.voice-recorder {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 0 8px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #fff;
}

.recorder-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.record-btn {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: 3px solid #0f766e;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  position: relative;
  -webkit-user-select: none;
  user-select: none;
  touch-action: none;
}

.record-btn:hover {
  background: #ecfdf5;
}

.record-btn.recording {
  background: #fee2e2;
  border-color: #ef4444;
  animation: pulse 1.2s infinite;
}

.record-btn.uploading {
  border-color: #f59e0b;
  cursor: not-allowed;
  opacity: 0.7;
}

.record-btn:disabled {
  cursor: not-allowed;
}

.btn-icon {
  font-size: 24px;
  line-height: 1;
}

.mic-icon::before {
  content: '🎤';
}

.stop-icon {
  width: 18px;
  height: 18px;
  background: #ef4444;
  border-radius: 3px;
}

.spinner {
  width: 24px;
  height: 24px;
  border: 3px solid #ddd;
  border-top-color: #f59e0b;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4);
  }
  50% {
    box-shadow: 0 0 0 16px rgba(239, 68, 68, 0);
  }
}

.recorder-status {
  min-width: 120px;
}

.status-text {
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-text.idle {
  color: #64748b;
}

.status-text.recording {
  color: #ef4444;
  font-weight: 500;
}

.status-text.uploading {
  color: #f59e0b;
  font-weight: 500;
}

.rec-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  animation: blink 1s step-end infinite;
}

.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f59e0b;
  animation: blink 0.6s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.waveform-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  height: 28px;
  margin-top: 8px;
}

.wave-bar {
  width: 4px;
  background: #0f766e;
  border-radius: 2px;
  transition: height 0.1s;
  min-height: 2px;
}
</style>
