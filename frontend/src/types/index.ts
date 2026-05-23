export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface AsrResponse {
  recordId: number
  originalText: string
  engineType: string
  duration: number
  confidence: number
}

export interface RecordVO {
  id: number
  originalText: string
  editedText: string | null
  engineType: string
  duration: number
  confidence: number
  status: number
  createdAt: string
}

export interface RecordPageData {
  total: number
  page: number
  size: number
  records: RecordVO[]
}

export interface EngineOption {
  label: string
  value: string
  mode?: 'stream' | 'upload'
}
