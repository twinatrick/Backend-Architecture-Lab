package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Entity.LineGfSession;
import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Vo.LineGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GfSessionMapper {
    LineGfSessionVo toVo(LineGfSession entity);
    DiscordGfSessionVo toVo(DiscordGfSession entity);
    List<DiscordGfSessionVo> toDiscordVoList(List<DiscordGfSession> entities);
}
