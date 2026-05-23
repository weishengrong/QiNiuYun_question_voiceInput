package com.example.qiniuyun_voiceinput.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VoiceRecord {
    private Long id;
    private String audioName;
    private Long audioSize;
    private Integer audioDuration;
    private String audioFormat;
    private String originalText;
    private String editedText;
    private String engineType;
    private BigDecimal confidence;
    private Integer status;
    private String errorMsg;
    private String clientIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}