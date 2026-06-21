package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Dto.Vo.VoiceDiaryVo;
import com.example.BackendArchitectureLab.Entity.VoiceDiary;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VoiceDiaryMapper {
    VoiceDiaryVo toDto(VoiceDiary entity);
}
