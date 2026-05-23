package com.example.qiniuyun_voiceinput.model.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecordVO {
    private Long id;
    private String originalText;
    private String editedText;
    private String engineType;
    private Integer duration;
    private BigDecimal confidence;
    private Integer status;
    private LocalDateTime createdAt;
}