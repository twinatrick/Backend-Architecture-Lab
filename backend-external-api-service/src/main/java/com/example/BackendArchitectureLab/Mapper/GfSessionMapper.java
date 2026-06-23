package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Entity.LineGfSession;
import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Vo.LineGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GfSessionMapper {
    LineGfSessionVo toVo(LineGfSession entity);
    DiscordGfSessionVo toVo(DiscordGfSession entity);
}
