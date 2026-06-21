package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.BotConfigVo;

import java.util.List;
import java.util.UUID;

public interface IBotConfigService {
    List<BotConfigVo> findAll();
    BotConfigVo findById(UUID id);
    BotConfigVo create(BotConfigVo vo);
    BotConfigVo update(UUID id, BotConfigVo vo);
    void delete(UUID id);
}
