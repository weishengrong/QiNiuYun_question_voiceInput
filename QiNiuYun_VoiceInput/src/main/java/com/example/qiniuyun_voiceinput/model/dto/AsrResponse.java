package com.example.qiniuyun_voiceinput.model.dto;

import lombok.Data;

@Data
public class AsrResponse {
    private Long recordId;
    private String originalText;
    private String engineType;
    private Double duration;
    private Double confidence;
}