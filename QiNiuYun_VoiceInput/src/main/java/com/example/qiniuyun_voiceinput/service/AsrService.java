package com.example.qiniuyun_voiceinput.service;

import com.example.qiniuyun_voiceinput.service.asr.AsrEngine;
import com.example.qiniuyun_voiceinput.service.asr.VoskAsrEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsrService {

    private final VoskAsrEngine voskAsrEngine;

    @Value("${asr.default-engine:vosk}")
    private String defaultEngine;

    public AsrEngine.AsrResult recognize(File audioFile, String audioFormat, String engineType) {
        AsrEngine engine = getEngine(engineType);
        if (engine == null) {
            log.warn("指定的引擎 {} 不可用，回退到默认引擎 {}", engineType, defaultEngine);
            engine = getEngine(defaultEngine);
        }
        if (engine == null) {
            engine = voskAsrEngine;
        }

        log.info("使用ASR引擎: {} 识别音频: {}", engine.getEngineType(), audioFile.getName());
        return engine.recognize(audioFile, audioFormat);
    }

    private AsrEngine getEngine(String engineType) {
        return switch (engineType != null ? engineType : defaultEngine) {
            case "vosk" -> voskAsrEngine;
            default -> null;
        };
    }
}
