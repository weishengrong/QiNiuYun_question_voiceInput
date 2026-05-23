package com.example.qiniuyun_voiceinput.controller;

import com.example.qiniuyun_voiceinput.model.vo.ApiResult;
import com.example.qiniuyun_voiceinput.model.vo.RecordVO;
import com.example.qiniuyun_voiceinput.service.RecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    @GetMapping
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        List<RecordVO> records = recordService.getRecordPage(page, size);
        long total = recordService.getRecordCount();

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("records", records);

        return ApiResult.success(data);
    }

    @GetMapping("/{id}")
    public ApiResult<RecordVO> detail(@PathVariable Long id) {
        RecordVO record = recordService.getRecordVO(id);
        if (record == null) {
            return ApiResult.error(40400, "记录不存在");
        }
        return ApiResult.success(record);
    }

    @PutMapping("/{id}/text")
    public ApiResult<Void> updateText(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String editedText = body.get("editedText");
        if (editedText == null) {
            return ApiResult.error(40000, "editedText不能为空");
        }

        RecordVO record = recordService.getRecordVO(id);
        if (record == null) {
            return ApiResult.error(40400, "记录不存在");
        }

        recordService.updateEditedText(id, editedText);
        return ApiResult.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        boolean deleted = recordService.deleteRecord(id);
        if (!deleted) {
            return ApiResult.error(40400, "记录不存在");
        }
        return ApiResult.success(null);
    }
}