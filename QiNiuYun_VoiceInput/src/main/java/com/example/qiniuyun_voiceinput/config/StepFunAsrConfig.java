package com.example.qiniuyun_voiceinput.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "stepfun.asr")
public class StepFunAsrConfig {

    private String token;
    private String streamUrl = "wss://api.stepfun.com/v1/realtime/asr/stream";
    private String model = "step-asr-1.1-stream";
    private String language = "zh";
    private int sampleRate = 16000;
    private int silenceDurationMs = 800;
    private double vadThreshold = 0.5;
    private boolean enableItn = true;
}
