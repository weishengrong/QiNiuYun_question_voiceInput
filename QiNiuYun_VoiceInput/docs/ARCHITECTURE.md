# 技术选型与架构设计

## 1. 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 4.0.6 | Web 框架、依赖注入 |
| MyBatis | 4.0.1 | ORM 持久层 |
| MySQL | 8.0+ | 数据库 |
| Maven | 3.9+ | 构建工具 |
| Lombok | 最新 | Java 代码简化 |
| Qiniu Java SDK | 7.x | 七牛云 API 调用 |
| Vosk | 0.3.45 | 离线语音识别 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.x | 前端框架 |
| Vite | 5.x | 构建工具 |
| TypeScript | 5.x | 类型安全 |
| Axios | 最新 | HTTP 请求 |
| CSS3 | - | 样式（简洁美观） |

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    浏览器 (Vue 3)                         │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ MediaRecorder│  │ 文本编辑器    │  │ 历史记录面板   │  │
│  │ 录音组件      │  │ Vue组件      │  │ Vue组件        │  │
│  └──────┬──────┘  └──────────────┘  └───────────────┘  │
└─────────┼───────────────────────────────────────────────┘
          │ HTTP / WebSocket
┌─────────┴───────────────────────────────────────────────┐
│                 Spring Boot 后端                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Controller 层                                     │   │
│  │  ┌─────────┐ ┌───────────┐ ┌────────────────┐   │   │
│  │  │AudioCtrl│ │RecordCtrl │ │TextCtrl        │   │   │
│  │  └────┬────┘ └─────┬─────┘ └───────┬────────┘   │   │
│  ├───────┼────────────┼───────────────┼────────────┤   │
│  │  Service 层                                      │   │
│  │  ┌──────┴──────────┴──────────────────────┐      │   │
│  │  │  AsrService (策略模式)                    │      │   │
│  │  │  ├── QiniuAsrEngine (七牛云)             │      │   │
│  │  │  └── VoskAsrEngine (Vosk离线)           │      │   │
│  │  ├──────────────────────────────────────────┤      │   │
│  │  │  AudioService (音频处理)                  │      │   │
│  │  │  RecordService (历史记录)                 │      │   │
│  │  └──────────────────────────────────────────┘      │   │
│  ├──────────────────────────────────────────────────┤   │
│  │  Mapper 层 (MyBatis)                              │   │
│  │  ┌─────────────────┐                             │   │
│  │  │ VoiceRecordMapper│                             │   │
│  │  └────────┬────────┘                             │   │
│  ├───────────┼──────────────────────────────────────┤   │
│  │           ▼                                       │   │
│  │        MySQL                                      │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## 3. 包结构

```
com.example.qiniuyun_voiceinput/
├── QiNiuYunVoiceInputApplication.java    # 启动类
├── config/
│   ├── QiniuConfig.java                  # 七牛云配置
│   ├── WebConfig.java                    # Web 配置 (CORS等)
│   └── VoskConfig.java                   # Vosk 配置
├── controller/
│   ├── AudioController.java              # 音频上传 + 识别
│   └── RecordController.java             # 历史记录 CRUD
├── service/
│   ├── asr/
│   │   ├── AsrEngine.java                # ASR 引擎接口
│   │   ├── QiniuAsrEngine.java           # 七牛云实现
│   │   └── VoskAsrEngine.java            # Vosk 实现
│   ├── AsrService.java                   # ASR 服务门面
│   ├── AudioService.java                 # 音频处理服务
│   └── RecordService.java               # 历史记录服务
├── model/
│   ├── entity/
│   │   └── VoiceRecord.java              # 数据库实体
│   ├── dto/
│   │   ├── AsrRequest.java               # 识别请求 DTO
│   │   └── AsrResponse.java              # 识别响应 DTO
│   └── vo/
│       ├—— RecordVO.java                 # 历史记录展示 VO
│       └── ApiResult.java                # 统一响应体
├── mapper/
│   └── VoiceRecordMapper.java            # MyBatis Mapper
└── util/
    └── AudioUtils.java                   # 音频工具类
```

## 4. ASR 引擎策略模式

```
┌───────────────┐     ┌──────────────────────┐
│  AsrService    │────▶│  AsrEngine(接口)      │
│  (门面/策略调用) │     │  +recognize(audio):Str │
└───────────────┘     └──────────┬───────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
         ┌──────────▼──────┐       ┌──────────▼──────┐
         │ QiniuAsrEngine   │       │ VoskAsrEngine    │
         │ (七牛云短语音API) │       │ (本地Vosk模型)    │
         └─────────────────┘       └─────────────────┘
```

- `AsrService` 根据配置或用户选择，动态注入对应引擎
- 默认使用七牛云 API，Vosk 作为离线兜底
- 引擎可通过配置项 `asr.engine=qiniu|vosk` 切换