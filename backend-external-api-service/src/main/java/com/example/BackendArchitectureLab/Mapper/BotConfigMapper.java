package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Vo.BotConfigVo;
import com.example.BackendArchitectureLab.Entity.BotConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BotConfigMapper {
    BotConfigVo toDto(BotConfig entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdTime", ignore = true)
    @Mapping(target = "updatedTime", ignore = true)
    BotConfig toEntity(BotConfigVo vo);
}
