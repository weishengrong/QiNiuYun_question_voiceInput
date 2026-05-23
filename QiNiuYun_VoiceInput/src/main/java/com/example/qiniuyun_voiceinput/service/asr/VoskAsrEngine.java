package com.example.qiniuyun_voiceinput.service.asr;

import com.example.qiniuyun_voiceinput.config.VoskConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vosk.model-path")
public class VoskAsrEngine implements AsrEngine {

    private final VoskConfig voskConfig;
    private Model model;

    @PostConstruct
    public void init() {
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            File modelFile = new File(voskConfig.getModelPath());
            if (modelFile.exists()) {
                model = new Model(voskConfig.getModelPath());
                log.info("Vosk模型加载成功: {}", voskConfig.getModelPath());
            } else {
                log.warn("Vosk模型不存在: {}，离线识别不可用", voskConfig.getModelPath());
            }
        } catch (Exception e) {
            log.error("Vosk模型加载失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (model != null) {
            model.close();
        }
    }

    @Override
    public AsrResult recognize(File audioFile, String audioFormat) {
        if (model == null) {
            log.warn("Vosk模型未加载，跳过离线识别");
            return new AsrResult("", 0);
        }

        try {
            byte[] pcmData = convertToPcm16k16bitMono(audioFile);
            if (pcmData == null || pcmData.length == 0) {
                return new AsrResult("", 0);
            }

            try (Recognizer recognizer = new Recognizer(model, voskConfig.getSampleRate())) {
                recognizer.acceptWaveForm(pcmData, pcmData.length);
                String result = recognizer.getFinalResult();

                log.info("Vosk识别结果: {}", result);

                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(result).getAsJsonObject();
                String text = json.has("text") ? json.get("text").getAsString() : "";

                return new AsrResult(text, 85.0);
            }
        } catch (Exception e) {
            log.error("Vosk识别失败: {}", e.getMessage(), e);
            return new AsrResult("", 0);
        }
    }

    private byte[] convertToPcm16k16bitMono(File audioFile) {
        try (AudioInputStream originalStream = AudioSystem.getAudioInputStream(audioFile)) {
            AudioFormat targetFormat = new AudioFormat(
                    16000f, 16, 1, true, false
            );

            try (AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, originalStream)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = convertedStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.error("音频格式转换失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String getEngineType() {
        return "vosk";
    }
}