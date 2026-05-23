# API 接口文档

## 基础信息

- **Base URL**: `http://localhost:8080/api`
- **响应格式**: 统一 JSON 格式

### 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

- `code`: 0 成功，非0 失败
- `message`: 提示信息
- `data`: 业务数据

---

## 1. 语音识别

### 1.1 上传音频并识别

上传录音文件，后端调用 ASR 引擎识别并返回文本。

**Request**

```
POST /api/asr/recognize
Content-Type: multipart/form-data
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| audio | File | 是 | 音频文件(wav/mp3/webm/ogg) |
| engine | String | 否 | 识别引擎: qiniu(默认) / vosk |

**Response (200)**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "recordId": 1,
    "originalText": "今天天气真不错，适合出去走走。",
    "engineType": "qiniu",
    "duration": 5.2,
    "confidence": 95.50
  }
}
```

**Response (500 - 识别失败)**

```json
{
  "code": 50001,
  "message": "语音识别失败: 音频质量过低",
  "data": null
}
```

---

## 2. 音频管理

### 2.1 获取音频文件

通过 recordId 获取原始音频文件，用于前端播放验证。

**Request**

```
GET /api/audio/{recordId}
```

**Response (200)**

```
Content-Type: audio/wav
Binary audio data
```

---

## 3. 历史记录

### 3.1 获取历史记录列表

分页查询历史识别记录。

**Request**

```
GET /api/records?page=1&size=20
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 20 |

**Response (200)**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 100,
    "page": 1,
    "size": 20,
    "records": [
      {
        "id": 1,
        "originalText": "今天天气真不错",
        "editedText": null,
        "engineType": "qiniu",
        "duration": 3.2,
        "status": 1,
        "createdAt": "2025-05-23 14:30:00"
      }
    ]
  }
}
```

### 3.2 获取单条记录详情

**Request**

```
GET /api/records/{id}
```

**Response (200)** — 同上 data 结构中的单条记录

### 3.3 更新编辑后的文本

用户在前端编辑识别结果后保存。

**Request**

```
PUT /api/records/{id}/text
Content-Type: application/json
```

```json
{
  "editedText": "今天天气真不错，适合出去走走。"
}
```

**Response (200)**

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

### 3.4 删除记录

**Request**

```
DELETE /api/records/{id}
```

**Response (200)**

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

---

## 4. 错误码说明

| code | 说明 |
|------|------|
| 0 | 成功 |
| 40000 | 请求参数错误 |
| 40001 | 音频文件为空 |
| 40002 | 音频格式不支持 |
| 40003 | 音频文件过大(>10MB) |
| 50001 | 语音识别失败 |
| 50002 | ASR 引擎调用异常 |
| 50003 | 音频处理失败 |
| 40400 | 记录不存在 |

---

## 5. 前端录音参数建议

前端使用 `MediaRecorder` API 时的推荐配置：

```javascript
// 推荐录音参数
const constraints = {
  audio: {
    sampleRate: 16000,     // 16kHz 采样率
    channelCount: 1,        // 单声道
    echoCancellation: true, // 回声消除
    noiseSuppression: true  // 降噪
  }
};

// 推荐编码格式
const mimeType = 'audio/webm;codecs=opus';
// 或 'audio/wav' (部分浏览器不支持)
```