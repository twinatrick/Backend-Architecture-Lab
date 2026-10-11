package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Entity.DiscordGfMessage;
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DiscordGfMessageMapper {
    DiscordGfMessageVo toVo(DiscordGfMessage entity);
    List<DiscordGfMessageVo> toVoList(List<DiscordGfMessage> entities);
}
