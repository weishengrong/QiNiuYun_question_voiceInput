# 数据库设计

## 1. 总览

仅需一张核心表 `voice_record`，记录每次语音识别的结果和历史。

## 2. 表结构

### voice_record（语音识别记录）

| 字段 | 类型 | 长度 | 约束 | 说明 |
|------|------|------|------|------|
| id | BIGINT | - | PK, AUTO_INCREMENT | 主键 |
| audio_name | VARCHAR | 255 | NOT NULL | 原始音频文件名 |
| audio_size | BIGINT | - | NOT NULL | 音频文件大小(字节) |
| audio_duration | INT | - | NULL | 音频时长(秒) |
| audio_format | VARCHAR | 20 | NOT NULL | 音频格式(wav/mp3/webm) |
| original_text | TEXT | - | NULL | 原始识别文本 |
| edited_text | TEXT | - | NULL | 用户编辑后的文本 |
| engine_type | VARCHAR | 20 | NOT NULL | 识别引擎(stepfun_stream/vosk) |
| confidence | DECIMAL(5,2) | - | NULL | 识别置信度(0-100) |
| status | TINYINT | - | NOT NULL, DEFAULT 0 | 状态: 0=处理中, 1=成功, 2=失败 |
| error_msg | VARCHAR | 500 | NULL | 失败原因 |
| client_ip | VARCHAR | 50 | NULL | 客户端IP |
| created_at | DATETIME | - | NOT NULL | 创建时间 |
| updated_at | DATETIME | - | NOT NULL, ON UPDATE | 更新时间 |

### 建表 SQL

```sql
CREATE TABLE voice_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    audio_name VARCHAR(255) NOT NULL COMMENT '原始音频文件名',
    audio_size BIGINT NOT NULL COMMENT '音频文件大小(字节)',
    audio_duration INT NULL COMMENT '音频时长(秒)',
    audio_format VARCHAR(20) NOT NULL COMMENT '音频格式(wav/mp3/webm)',
    original_text TEXT NULL COMMENT '原始识别文本',
    edited_text TEXT NULL COMMENT '用户编辑后的文本',
    engine_type VARCHAR(20) NOT NULL COMMENT '识别引擎(stepfun_stream/vosk)',
    confidence DECIMAL(5,2) NULL COMMENT '识别置信度(0-100)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0=处理中, 1=成功, 2=失败',
    error_msg VARCHAR(500) NULL COMMENT '失败原因',
    client_ip VARCHAR(50) NULL COMMENT '客户端IP',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_created_at (created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='语音识别记录表';
```

### 索引说明

| 索引名 | 字段 | 用途 |
|--------|------|------|
| PRIMARY | id | 主键查询 |
| idx_created_at | created_at | 历史列表排序 |
| idx_status | status | 按状态过滤 |

## 3. 数据流

```
录音上传 → 插入 voice_record(status=0)
    ↓
ASR 识别完成 → 更新 original_text, status=1
    ↓
用户编辑文本 → 更新 edited_text
    ↓
查看历史   → SELECT * FROM voice_record ORDER BY created_at DESC
```
