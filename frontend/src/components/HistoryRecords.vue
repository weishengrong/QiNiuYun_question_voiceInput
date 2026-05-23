<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { fetchRecords, updateRecordText, deleteRecord, getAudioUrl } from '../api'
import type { RecordVO, RecordPageData } from '../types'

const emit = defineEmits<{
  reuse: [text: string]
}>()

const pageData = ref<RecordPageData>({
  total: 0,
  page: 1,
  size: 20,
  records: [],
})
const loading = ref(false)
const editingId = ref<number | null>(null)
const editText = ref('')
const audioPlaying = ref<number | null>(null)

async function loadRecords() {
  loading.value = true
  try {
    const res = await fetchRecords(pageData.value.page, pageData.value.size)
    if (res.code === 0 && res.data) {
      pageData.value = res.data
    }
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  pageData.value.page = p
  loadRecords()
}

const totalPages = computed(() => Math.ceil(pageData.value.total / pageData.value.size))

function startEdit(record: RecordVO) {
  editingId.value = record.id
  editText.value = record.editedText || record.originalText
}

async function saveEdit(id: number) {
  if (!editText.value.trim()) return
  try {
    await updateRecordText(id, editText.value)
    editingId.value = null
    editText.value = ''
    loadRecords()
  } catch {
    // ignore
  }
}

function cancelEdit() {
  editingId.value = null
  editText.value = ''
}

async function removeRecord(id: number) {
  try {
    await deleteRecord(id)
    loadRecords()
  } catch {
    // ignore
  }
}

function toggleAudio(recordId: number) {
  if (audioPlaying.value === recordId) {
    audioPlaying.value = null
    return
  }
  audioPlaying.value = recordId
}

function getDisplayText(record: RecordVO): string {
  return record.editedText || record.originalText
}

function getStatusLabel(status: number): string {
  switch (status) {
    case 0:
      return '处理中'
    case 1:
      return '成功'
    case 2:
      return '失败'
    default:
      return '未知'
  }
}

function getStatusClass(status: number): string {
  switch (status) {
    case 0:
      return 'status-pending'
    case 1:
      return 'status-success'
    case 2:
      return 'status-error'
    default:
      return ''
  }
}

onMounted(loadRecords)
</script>

<template>
  <div class="history-records">
    <div class="history-header">
      <h3>📜 历史记录</h3>
      <span class="record-count">共 {{ pageData.total }} 条</span>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="pageData.records.length === 0" class="empty">
      暂无历史记录
    </div>

    <div v-else class="records-list">
      <div
        v-for="record in pageData.records"
        :key="record.id"
        class="record-item"
      >
        <div class="record-meta">
          <span class="record-time">{{ record.createdAt }}</span>
          <span
            class="record-status"
            :class="getStatusClass(record.status)"
          >
            {{ getStatusLabel(record.status) }}
          </span>
          <span class="record-engine">{{ record.engineType }}</span>
          <span v-if="record.confidence" class="record-confidence">
            {{ record.confidence.toFixed(1) }}%
          </span>
        </div>

        <div v-if="editingId === record.id" class="record-edit">
          <textarea
            v-model="editText"
            class="edit-input"
            rows="3"
          ></textarea>
          <div class="edit-actions">
            <button class="action-btn primary" @click="saveEdit(record.id)">保存</button>
            <button class="action-btn" @click="cancelEdit">取消</button>
          </div>
        </div>

        <div v-else class="record-text" @click="startEdit(record)">
          {{ getDisplayText(record) }}
        </div>

        <div class="record-actions">
          <button
            class="action-btn icon-btn"
            title="播放音频"
            @click="toggleAudio(record.id)"
          >
            {{ audioPlaying === record.id ? '⏹' : '▶️' }}
          </button>

          <audio
            v-if="audioPlaying === record.id"
            :src="getAudioUrl(record.id)"
            controls
            autoplay
            class="audio-player"
            @ended="audioPlaying = null"
          ></audio>

          <button
            class="action-btn reuse-btn"
            @click="emit('reuse', getDisplayText(record))"
          >
            📋 复用
          </button>

          <button
            class="action-btn edit-btn"
            @click="startEdit(record)"
          >
            ✏️ 编辑
          </button>

          <button
            class="action-btn delete-btn"
            @click="removeRecord(record.id)"
          >
            🗑 删除
          </button>
        </div>
      </div>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button
        class="page-btn"
        :disabled="pageData.page <= 1"
        @click="goPage(pageData.page - 1)"
      >
        上一页
      </button>
      <span class="page-info">
        {{ pageData.page }} / {{ totalPages }}
      </span>
      <button
        class="page-btn"
        :disabled="pageData.page >= totalPages"
        @click="goPage(pageData.page + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<style scoped>
.history-records {
  border-top: 1px solid #e5e7eb;
  padding-top: 16px;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.history-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.record-count {
  font-size: 13px;
  color: #9ca3af;
}

.loading,
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 24px 0;
  font-size: 14px;
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 360px;
  overflow-y: auto;
}

.record-item {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fafafa;
  transition: background 0.15s;
}

.record-item:hover {
  background: #f3f4f6;
}

.record-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  flex-wrap: wrap;
}

.record-time {
  color: #6b7280;
}

.record-status {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.status-pending {
  background: #fef3c7;
  color: #92400e;
}

.status-success {
  background: #d1fae5;
  color: #065f46;
}

.status-error {
  background: #fee2e2;
  color: #991b1b;
}

.record-engine,
.record-confidence {
  color: #9ca3af;
}

.record-text {
  font-size: 14px;
  line-height: 1.6;
  color: #374151;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}

.record-text:hover {
  color: #4f46e5;
}

.record-edit {
  margin-top: 4px;
}

.edit-input {
  width: 100%;
  padding: 8px;
  border: 1px solid #4f46e5;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
}

.edit-actions {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.record-actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.action-btn {
  padding: 4px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 5px;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  color: #374151;
  transition: all 0.15s;
}

.action-btn:hover {
  background: #f3f4f6;
}

.action-btn.primary {
  background: #4f46e5;
  color: #fff;
  border-color: #4f46e5;
}

.action-btn.primary:hover {
  background: #4338ca;
}

.reuse-btn:hover {
  color: #4f46e5;
  border-color: #4f46e5;
}

.edit-btn:hover {
  color: #059669;
  border-color: #059669;
}

.delete-btn:hover {
  color: #ef4444;
  border-color: #ef4444;
}

.icon-btn {
  padding: 4px 8px;
  font-size: 14px;
}

.audio-player {
  width: 180px;
  height: 32px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}

.page-btn {
  padding: 4px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 5px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  transition: all 0.15s;
}

.page-btn:hover:not(:disabled) {
  background: #f3f4f6;
  border-color: #d1d5db;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: #6b7280;
}
</style>
