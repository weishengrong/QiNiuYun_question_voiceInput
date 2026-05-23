package com.example.qiniuyun_voiceinput.service.asr;

import java.io.File;

public interface AsrEngine {

    AsrResult recognize(File audioFile, String audioFormat);

    String getEngineType();

    record AsrResult(String text, double confidence) {}
}