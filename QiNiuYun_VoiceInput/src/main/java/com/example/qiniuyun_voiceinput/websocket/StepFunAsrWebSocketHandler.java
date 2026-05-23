package com.example.qiniuyun_voiceinput.websocket;

import com.example.qiniuyun_voiceinput.config.StepFunAsrConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class StepFunAsrWebSocketHandler extends TextWebSocketHandler {

    private final StepFunAsrConfig config;
    private final Gson gson = new Gson();
    private final Map<String, StepFunBridge> bridges = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!StringUtils.hasText(config.getToken())) {
            sendJson(session, Map.of(
                    "type", "error",
                    "message", "StepFun API Token 未配置，请设置 STEPFUN_API_TOKEN"));
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        log.info("浏览器实时ASR连接已建立: {}", session.getId());
        StepFunBridge bridge = new StepFunBridge(session);
        bridges.put(session.getId(), bridge);
        bridge.connect();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        StepFunBridge bridge = bridges.get(session.getId());
        if (bridge == null) return;

        JsonObject payload = JsonParser.parseString(message.getPayload()).getAsJsonObject();
        String type = getString(payload, "type");
        if ("audio.append".equals(type)) {
            String audio = getString(payload, "audio");
            if (StringUtils.hasText(audio)) {
                bridge.sendAudio(audio);
            }
        } else if ("session.stop".equals(type)) {
            bridge.close();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("浏览器实时ASR连接关闭: {}, status={}", session.getId(), status);
        StepFunBridge bridge = bridges.remove(session.getId());
        if (bridge != null) {
            bridge.close();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("浏览器ASR WebSocket异常: {}", exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    private void sendJson(WebSocketSession session, Object payload) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(gson.toJson(payload)));
                }
            }
        } catch (Exception e) {
            log.warn("向浏览器发送ASR消息失败: {}", e.getMessage());
        }
    }

    private class StepFunBridge implements WebSocket.Listener {
        private final WebSocketSession browserSession;
        private final StringBuilder serviceMessage = new StringBuilder();
        private volatile WebSocket stepFunSocket;
        private volatile boolean configured;

        StepFunBridge(WebSocketSession browserSession) {
            this.browserSession = browserSession;
        }

        void connect() {
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .newWebSocketBuilder()
                    .header("Authorization", "Bearer " + config.getToken())
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(config.getStreamUrl()), this)
                    .whenComplete((socket, err) -> {
                        if (err != null) {
                            log.warn("连接StepFun ASR失败", err);
                            sendJson(browserSession, Map.of("type", "error", "message", "连接 StepFun ASR 失败: " + err.getMessage()));
                        } else {
                            stepFunSocket = socket;
                            log.info("StepFun ASR WebSocket连接成功");
                            sendJson(browserSession, Map.of("type", "ready"));
                        }
                    });
        }

        void sendAudio(String audio) {
            WebSocket socket = stepFunSocket;
            if (socket == null || !configured) return;
            socket.sendText(toJson(Map.of(
                    "event_id", eventId(),
                    "type", "input_audio_buffer.append",
                    "audio", audio)), true);
        }

        void close() {
            WebSocket socket = stepFunSocket;
            if (socket != null) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "browser session closed");
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            stepFunSocket = webSocket;
            webSocket.request(1);
            log.info("StepFun ASR WebSocket已打开");
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            serviceMessage.append(data);
            if (last) {
                String text = serviceMessage.toString();
                serviceMessage.setLength(0);
                handleStepFunMessage(text);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("StepFun ASR WebSocket关闭: code={}, reason={}", statusCode, reason);
            sendJson(browserSession, Map.of("type", "closed", "message", reason == null ? "" : reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("StepFun ASR WebSocket异常", error);
            sendJson(browserSession, Map.of("type", "error", "message", "StepFun ASR 异常: " + error.getMessage()));
        }

        private void handleStepFunMessage(String text) {
            try {
                JsonObject node = JsonParser.parseString(text).getAsJsonObject();
                String type = getString(node, "type");
                log.debug("StepFun ASR事件: {}", type);

                if ("session.created".equals(type)) {
                    sendSessionUpdate();
                    return;
                }

                if ("session.updated".equals(type)) {
                    configured = true;
                    sendJson(browserSession, Map.of("type", "configured"));
                    return;
                }

                if ("conversation.item.input_audio_transcription.delta".equals(type)) {
                    sendJson(browserSession, Map.of("type", "delta", "text", getString(node, "delta")));
                } else if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                    String completed = getString(node, "transcript");
                    if (!StringUtils.hasText(completed)) {
                        completed = getString(node, "text");
                    }
                    sendJson(browserSession, Map.of("type", "completed", "text", completed));
                } else if ("input_audio_buffer.speech_started".equals(type)) {
                    sendJson(browserSession, Map.of("type", "speech_started"));
                } else if ("input_audio_buffer.speech_stopped".equals(type)) {
                    sendJson(browserSession, Map.of("type", "speech_stopped"));
                } else if (type.endsWith(".error") || "error".equals(type)) {
                    String message = getErrorMessage(node);
                    sendJson(browserSession, Map.of("type", "error", "message", StringUtils.hasText(message) ? message : text));
                }
            } catch (Exception e) {
                log.warn("解析StepFun消息失败: {}", text, e);
            }
        }

        private void sendSessionUpdate() {
            WebSocket socket = stepFunSocket;
            if (socket == null) {
                log.warn("StepFun ASR session.created 已收到，但WebSocket尚未就绪");
                sendJson(browserSession, Map.of("type", "error", "message", "StepFun 连接尚未就绪，请重试"));
                return;
            }

            Map<String, Object> payload = Map.of(
                    "event_id", eventId(),
                    "type", "session.update",
                    "session", Map.of(
                            "audio", Map.of(
                                    "input", Map.of(
                                            "format", Map.of(
                                                    "type", "pcm",
                                                    "codec", "pcm_s16le",
                                                    "rate", config.getSampleRate(),
                                                    "bits", 16,
                                                    "channel", 1),
                                            "transcription", Map.of(
                                                    "model", config.getModel(),
                                                    "language", config.getLanguage(),
                                                    "enable_itn", config.isEnableItn()),
                                            "turn_detection", Map.of(
                                                    "type", "server_vad",
                                                    "silence_duration_ms", config.getSilenceDurationMs(),
                                                    "threshold", config.getVadThreshold())))));
            log.info("发送StepFun ASR session.update: model={}, language={}, sampleRate={}",
                    config.getModel(), config.getLanguage(), config.getSampleRate());
            socket.sendText(toJson(payload), true);
        }

        private String toJson(Object payload) {
            try {
                return gson.toJson(payload);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private String eventId() {
            return "event_" + UUID.randomUUID();
        }

        private String getString(JsonObject node, String name) {
            return node.has(name) && !node.get(name).isJsonNull() ? node.get(name).getAsString() : "";
        }

        private String getErrorMessage(JsonObject node) {
            String message = getString(node, "message");
            if (StringUtils.hasText(message)) {
                return message;
            }
            if (node.has("error") && node.get("error").isJsonObject()) {
                JsonObject error = node.getAsJsonObject("error");
                message = getString(error, "message");
                if (StringUtils.hasText(message)) {
                    return message;
                }
                return getString(error, "code");
            }
            return "";
        }
    }

    private String getString(JsonObject node, String name) {
        return node.has(name) && !node.get(name).isJsonNull() ? node.get(name).getAsString() : "";
    }
}
