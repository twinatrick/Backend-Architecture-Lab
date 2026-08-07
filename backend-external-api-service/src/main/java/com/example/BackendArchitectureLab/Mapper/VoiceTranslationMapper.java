package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Entity.VoiceTranslation;
import com.example.BackendArchitectureLab.Vo.VoiceTranslationVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VoiceTranslationMapper {

    @Mapping(source = "voiceUpload.id", target = "voiceUploadId")
    VoiceTranslationVo toVo(VoiceTranslation entity);

    @Mapping(target = "voiceUpload", ignore = true)
    VoiceTranslation toEntity(VoiceTranslationVo vo);
}
