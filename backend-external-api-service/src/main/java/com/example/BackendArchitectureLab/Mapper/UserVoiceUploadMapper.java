package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Entity.UserVoiceUpload;
import com.example.BackendArchitectureLab.Vo.UserVoiceUploadVo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserVoiceUploadMapper {
    UserVoiceUploadVo toVo(UserVoiceUpload entity);
    UserVoiceUpload toEntity(UserVoiceUploadVo vo);
}
