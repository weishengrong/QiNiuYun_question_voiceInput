package com.example.qiniuyun_voiceinput.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class AudioService {

    @Value("${audio.upload-dir:uploads/audio}")
    private String uploadDir;

    public File saveAudio(MultipartFile file) throws IOException {
        Path dirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dirPath);

        log.info("音频文件保存目录: {}", dirPath);

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        } else {
            String contentType = file.getContentType();
            if (contentType != null) {
                ext = switch (contentType) {
                    case "audio/wav", "audio/x-wav" -> ".wav";
                    case "audio/mpeg", "audio/mp3" -> ".mp3";
                    case "audio/webm" -> ".webm";
                    case "audio/ogg" -> ".ogg";
                    default -> ".wav";
                };
            } else {
                ext = ".wav";
            }
        }

        String fileName = UUID.randomUUID() + ext;
        Path targetPath = dirPath.resolve(fileName);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("音频文件保存成功: {}", targetPath);
        return targetPath.toFile();
    }

    public String getAudioFormat(String fileName) {
        if (fileName == null) return "wav";
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex < 0) return "wav";
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    public File getAudioFile(String fileName) {
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(fileName).toFile();
    }

    public boolean deleteAudio(String fileName) {
        try {
            return Files.deleteIfExists(Paths.get(uploadDir).toAbsolutePath().normalize().resolve(fileName));
        } catch (IOException e) {
            log.error("删除音频文件失败: {}", fileName, e);
            return false;
        }
    }
}