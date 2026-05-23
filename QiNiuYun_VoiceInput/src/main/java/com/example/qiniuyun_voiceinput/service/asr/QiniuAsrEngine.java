package com.example.qiniuyun_voiceinput.service.asr;

import com.example.qiniuyun_voiceinput.config.QiniuConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class QiniuAsrEngine implements AsrEngine {

    private final QiniuConfig qiniuConfig;

    @Override
    public AsrResult recognize(File audioFile, String audioFormat) {
        try {
            byte[] audioBytes = FileUtils.readFileToByteArray(audioFile);
            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

            JsonObject body = new JsonObject();
            body.addProperty("audioBase64", audioBase64);
            body.addProperty("lang", "MANDARIN");
            body.addProperty("scene", "GENERAL");

            String requestBody = body.toString();
            String url = qiniuConfig.getAsr().getUrl();

            com.qiniu.util.Auth auth = com.qiniu.util.Auth.create(
                    qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
            String authorization = auth.signRequest(url, requestBody.getBytes(StandardCharsets.UTF_8), "application/json");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            log.info("七牛云ASR响应: {}", responseBody);

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            if (json.has("code") && json.get("code").getAsInt() != 0) {
                int code = json.get("code").getAsInt();
                String msg = json.has("message") ? json.get("message").getAsString() : "识别失败";
                log.warn("七牛云ASR返回错误: {} - {}", code, msg);
                return new AsrResult("", 0);
            }

            if (json.has("rtn") && json.get("rtn").getAsInt() != 0) {
                int rtn = json.get("rtn").getAsInt();
                String msg = json.has("message") ? json.get("message").getAsString() : "识别失败";
                log.warn("七牛云ASR识别失败: {} - {}", rtn, msg);
                return new AsrResult("", 0);
            }

            String resultText = json.has("resultText") ? json.get("resultText").getAsString() : "";
            return new AsrResult(resultText, 95.0);

        } catch (Exception e) {
            log.error("七牛云ASR处理异常: {}", e.getMessage(), e);
            return new AsrResult("", 0);
        }
    }

    @Override
    public String getEngineType() {
        return "qiniu";
    }
}