package com.example.qiniuyun_voiceinput.controller;

import com.example.qiniuyun_voiceinput.model.dto.AsrResponse;
import com.example.qiniuyun_voiceinput.model.entity.VoiceRecord;
import com.example.qiniuyun_voiceinput.model.vo.ApiResult;
import com.example.qiniuyun_voiceinput.service.AsrService;
import com.example.qiniuyun_voiceinput.service.AudioService;
import com.example.qiniuyun_voiceinput.service.RecordService;
import com.example.qiniuyun_voiceinput.service.asr.AsrEngine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AudioController {

    private final AudioService audioService;
    private final AsrService asrService;
    private final RecordService recordService;

    @PostMapping("/asr/recognize")
    public ApiResult<AsrResponse> recognize(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "engine", required = false) String engine,
            HttpServletRequest request) {

        if (audioFile.isEmpty()) {
            return ApiResult.error(40001, "音频文件为空");
        }

        String contentType = audioFile.getContentType();
        if (contentType != null && !contentType.startsWith("audio/")) {
            return ApiResult.error(40002, "音频格式不支持: " + contentType);
        }

        try {
            String originalName = audioFile.getOriginalFilename();
            String audioFormat = audioService.getAudioFormat(
                    originalName != null ? originalName : "audio.wav");

            VoiceRecord record = new VoiceRecord();
            record.setAudioName(originalName);
            record.setAudioSize(audioFile.getSize());
            record.setAudioFormat(audioFormat);
            record.setEngineType(engine != null ? engine : "qiniu");
            record.setStatus(0);
            record.setClientIp(request.getRemoteAddr());

            Long recordId = recordService.createRecord(record);

            File savedFile = audioService.saveAudio(audioFile);

            AsrEngine.AsrResult result = asrService.recognize(savedFile, audioFormat, engine);

            if (result.text() == null || result.text().isBlank()) {
                recordService.updateError(recordId, "识别结果为空");
                return ApiResult.error(50001, "语音识别失败，请重试或切换识别引擎");
            }

            recordService.updateResult(recordId, result.text(), result.confidence());

            AsrResponse response = recordService.getAsrResponse(recordId);
            return ApiResult.success(response);

        } catch (Exception e) {
            log.error("语音识别处理异常", e);
            return ApiResult.error(50002, "语音识别服务异常: " + e.getMessage());
        }
    }

    @GetMapping("/audio/{recordId}")
    public ResponseEntity<Resource> getAudio(@PathVariable Long recordId) {
        try {
            var record = recordService.getRecordVO(recordId);
            if (record == null) {
                return ResponseEntity.notFound().build();
            }

            String fileName = "audio_" + recordId + ".wav";
            File audioFile = audioService.getAudioFile(fileName);

            if (!audioFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(audioFile);
            String contentType = Files.probeContentType(audioFile.toPath());
            if (contentType == null) {
                contentType = "audio/wav";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("获取音频文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}