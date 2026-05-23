package com.example.qiniuyun_voiceinput.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "qiniu")
public class QiniuConfig {
    private String accessKey;
    private String secretKey;
    private Asr asr = new Asr();

    @Data
    public static class Asr {
        private String url;
    }
}