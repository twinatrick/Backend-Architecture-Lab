package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IBotConfigDataAccess;
import com.example.BackendArchitectureLab.Vo.BotConfigVo;
import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.BotConfigMapper;
import com.example.BackendArchitectureLab.Service.IBotConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BotConfigService implements IBotConfigService {

    @Autowired
    private IBotConfigDataAccess botConfigDataAccess;

    @Autowired
    private BotConfigMapper botConfigMapper;

    @Override
    public List<BotConfigVo> findAll() {
        return botConfigDataAccess.findAll().stream()
                .map(botConfigMapper::toVo)
                .toList();
    }

    @Override
    public BotConfigVo findById(UUID id) {
        return botConfigDataAccess.findById(id)
                .map(botConfigMapper::toVo)
                .orElseThrow(() -> new AppException("NOT_FOUND", "BotConfig not found", 404));
    }

    @Override
    public BotConfigVo create(BotConfigVo vo) {
        BotConfig entity = botConfigMapper.toEntity(vo);
        return botConfigMapper.toVo(botConfigDataAccess.save(entity));
    }

    @Override
    public BotConfigVo update(UUID id, BotConfigVo vo) {
        BotConfig existing = botConfigDataAccess.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "BotConfig not found", 404));
        BotConfig updates = botConfigMapper.toEntity(vo);
        existing.setPlatform(updates.getPlatform());
        existing.setConfigKey(updates.getConfigKey());
        existing.setConfigValue(updates.getConfigValue());
        existing.setDescription(updates.getDescription());
        existing.setCostLimitDaily(updates.getCostLimitDaily());
        existing.setCostAlertAt(updates.getCostAlertAt());
        return botConfigMapper.toVo(botConfigDataAccess.save(existing));
    }

    @Override
    public void delete(UUID id) {
        if (!botConfigDataAccess.existsById(id)) {
            throw new AppException("NOT_FOUND", "BotConfig not found", 404);
        }
        botConfigDataAccess.deleteById(id);
    }
}
