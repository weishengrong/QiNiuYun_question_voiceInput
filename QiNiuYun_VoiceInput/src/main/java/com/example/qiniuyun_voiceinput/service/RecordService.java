package com.example.qiniuyun_voiceinput.service;

import com.example.qiniuyun_voiceinput.mapper.VoiceRecordMapper;
import com.example.qiniuyun_voiceinput.model.dto.AsrResponse;
import com.example.qiniuyun_voiceinput.model.entity.VoiceRecord;
import com.example.qiniuyun_voiceinput.model.vo.RecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordService {

    private final VoiceRecordMapper voiceRecordMapper;

    @Transactional
    public Long createRecord(VoiceRecord record) {
        voiceRecordMapper.insert(record);
        return record.getId();
    }

    @Transactional
    public void updateResult(Long id, String text, double confidence) {
        VoiceRecord record = new VoiceRecord();
        record.setId(id);
        record.setOriginalText(text);
        record.setConfidence(BigDecimal.valueOf(confidence));
        record.setStatus(1);
        voiceRecordMapper.updateById(record);
    }

    @Transactional
    public void updateError(Long id, String errorMsg) {
        VoiceRecord record = new VoiceRecord();
        record.setId(id);
        record.setStatus(2);
        record.setErrorMsg(errorMsg);
        voiceRecordMapper.updateById(record);
    }

    @Transactional
    public void updateEditedText(Long id, String editedText) {
        VoiceRecord record = new VoiceRecord();
        record.setId(id);
        record.setEditedText(editedText);
        voiceRecordMapper.updateById(record);
    }

    public AsrResponse getAsrResponse(Long id) {
        VoiceRecord record = voiceRecordMapper.selectById(id);
        if (record == null) return null;

        AsrResponse resp = new AsrResponse();
        resp.setRecordId(record.getId());
        resp.setOriginalText(record.getOriginalText());
        resp.setEngineType(record.getEngineType());
        resp.setDuration(record.getAudioDuration() != null ? record.getAudioDuration().doubleValue() : 0);
        resp.setConfidence(record.getConfidence() != null ? record.getConfidence().doubleValue() : 0);
        return resp;
    }

    public RecordVO getRecordVO(Long id) {
        VoiceRecord record = voiceRecordMapper.selectById(id);
        return record != null ? toVO(record) : null;
    }

    public List<RecordVO> getRecordPage(int page, int size) {
        int offset = (page - 1) * size;
        return voiceRecordMapper.selectPage(offset, size)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public long getRecordCount() {
        return voiceRecordMapper.selectCount();
    }

    @Transactional
    public boolean deleteRecord(Long id) {
        return voiceRecordMapper.deleteById(id) > 0;
    }

    private RecordVO toVO(VoiceRecord record) {
        RecordVO vo = new RecordVO();
        vo.setId(record.getId());
        vo.setOriginalText(record.getOriginalText());
        vo.setEditedText(record.getEditedText());
        vo.setEngineType(record.getEngineType());
        vo.setDuration(record.getAudioDuration());
        vo.setConfidence(record.getConfidence());
        vo.setStatus(record.getStatus());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}