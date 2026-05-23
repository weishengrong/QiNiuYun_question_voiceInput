<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { uploadAudio } from '../api'

const emit = defineEmits<{
  recognized: [text: string, recordId: number]
  error: [message: string]
}>()

const props = defineProps<{
  engine: string
}>()

const recording = ref(false)
const uploading = ref(false)
const holdMode = ref(false)
const audioLevel = ref(0)

let mediaRecorder: MediaRecorder | null = null
let mediaStream: MediaStream | null = null
let audioChunks: Blob[] = []
let analyser: AnalyserNode | null = null
let animationId = 0

async function startRecording() {
  try {
    const permStatus = await navigator.permissions.query({ name: 'microphone' as PermissionName })
    if (permStatus.state === 'denied') {
      emit('error', '麦克风权限已被禁止，请在浏览器地址栏左侧的锁图标中开启麦克风权限后重试')
      return
    }
  } catch {
  }

  try {
    audioChunks = []
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        sampleRate: 16000,
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
      },
    })

    const mimeType = getSupportedMimeType()
    mediaRecorder = new MediaRecorder(mediaStream, mimeType ? { mimeType } : {})

    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) {
        audioChunks.push(e.data)
      }
    }

    mediaRecorder.onstop = async () => {
      stopAudioStream()
      if (audioChunks.length === 0) return

      const rawBlob = new Blob(audioChunks, { type: mimeType || 'audio/webm' })
      const wavBlob = await convertToWav(rawBlob)
      if (wavBlob) {
        await doUpload(wavBlob)
      } else {
        await doUpload(rawBlob)
      }
    }

    mediaRecorder.start(250)
    recording.value = true
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

async function convertToWav(blob: Blob): Promise<Blob | null> {
  try {
    const arrayBuffer = await blob.arrayBuffer()
    const audioCtx = new AudioContext({ sampleRate: 16000 })
    const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer)
    await audioCtx.close()

    const pcmData = audioBuffer.getChannelData(0)
    const sampleRate = audioBuffer.sampleRate
    const numChannels = 1
    const bitsPerSample = 16
    const byteRate = sampleRate * numChannels * bitsPerSample / 8
    const blockAlign = numChannels * bitsPerSample / 8
    const dataSize = pcmData.length * numChannels * bitsPerSample / 8

    const buffer = new ArrayBuffer(44 + dataSize)
    const view = new DataView(buffer)

    writeString(view, 0, 'RIFF')
    view.setUint32(4, 36 + dataSize, true)
    writeString(view, 8, 'WAVE')

    writeString(view, 12, 'fmt ')
    view.setUint32(16, 16, true)
    view.setUint16(20, 1, true)
    view.setUint16(22, numChannels, true)
    view.setUint32(24, sampleRate, true)
    view.setUint32(28, byteRate, true)
    view.setUint16(32, blockAlign, true)
    view.setUint16(34, bitsPerSample, true)

    writeString(view, 36, 'data')
    view.setUint32(40, dataSize, true)

    let offset = 44
    for (let i = 0; i < pcmData.length; i++) {
      const s = Math.max(-1, Math.min(1, pcmData[i]))
      view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true)
      offset += 2
    }

    return new Blob([buffer], { type: 'audio/wav' })
  } catch {
    return null
  }
}

function writeString(view: DataView, offset: number, str: string) {
  for (let i = 0; i < str.length; i++) {
    view.setUint8(offset + i, str.charCodeAt(i))
  }
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  }
  recording.value = false
  stopAudioLevelMonitor()
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

function getSupportedMimeType(): string | undefined {
  const types = [
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/ogg;codecs=opus',
  ]
  for (const t of types) {
    if (MediaRecorder.isTypeSupported(t)) return t
  }
  return undefined
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
  holdMode.value = true
  startRecording()
}

function onPointerUp() {
  if (holdMode.value) {
    holdMode.value = false
    stopRecording()
  }
}

function onClickToggle() {
  if (recording.value) {
    stopRecording()
  } else {
    startRecording()
  }
}

onUnmounted(() => {
  stopRecording()
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
        <div v-else-if="recording" class="status-text recording">
          <span class="rec-dot"></span> 录音中...
        </div>
        <div v-else class="status-text idle">
          点击录音 / 按住说话
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
  padding: 8px 0 4px;
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
  border: 3px solid #4f46e5;
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
  background: #eef2ff;
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
  color: #9ca3af;
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
  background: #4f46e5;
  border-radius: 2px;
  transition: height 0.1s;
  min-height: 2px;
}
</style>