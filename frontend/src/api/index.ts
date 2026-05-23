import axios from 'axios'
import type {
  ApiResult,
  AsrResponse,
  RecordPageData,
  RecordVO,
} from '../types'

const http = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

http.interceptors.response.use(
  (res) => {
    const body = res.data as ApiResult<unknown>
    if (body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return res
  },
  (err) => {
    return Promise.reject(err)
  },
)

export async function uploadAudio(
  audio: Blob,
  engine: string,
): Promise<ApiResult<AsrResponse>> {
  const form = new FormData()
  form.append('audio', audio, 'recording.webm')
  form.append('engine', engine)
  const res = await http.post<ApiResult<AsrResponse>>(
    '/asr/recognize',
    form,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
    },
  )
  return res.data
}

export async function fetchRecords(
  page: number,
  size: number,
): Promise<ApiResult<RecordPageData>> {
  const res = await http.get<ApiResult<RecordPageData>>('/records', {
    params: { page, size },
  })
  return res.data
}

export async function fetchRecordDetail(
  id: number,
): Promise<ApiResult<RecordVO>> {
  const res = await http.get<ApiResult<RecordVO>>(`/records/${id}`)
  return res.data
}

export async function updateRecordText(
  id: number,
  editedText: string,
): Promise<ApiResult<null>> {
  const res = await http.put<ApiResult<null>>(`/records/${id}/text`, {
    editedText,
  })
  return res.data
}

export async function deleteRecord(id: number): Promise<ApiResult<null>> {
  const res = await http.delete<ApiResult<null>>(`/records/${id}`)
  return res.data
}

export function getAudioUrl(recordId: number): string {
  return `/api/audio/${recordId}`
}

export default http