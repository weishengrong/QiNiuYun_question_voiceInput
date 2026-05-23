package com.example.qiniuyun_voiceinput.mapper;

import com.example.qiniuyun_voiceinput.model.entity.VoiceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface VoiceRecordMapper {

    int insert(VoiceRecord record);

    int updateById(VoiceRecord record);

    VoiceRecord selectById(@Param("id") Long id);

    List<VoiceRecord> selectPage(@Param("offset") int offset, @Param("size") int size);

    long selectCount();

    int deleteById(@Param("id") Long id);
}