package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.BotConfigVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.Search.BotConfigSearchQuery;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface IBotConfigService {
    List<BotConfigVo> findAll();
    PageResult<BotConfigVo> searchBotConfigs(BotConfigSearchQuery query);
    Flux<ServerSentEvent<List<BotConfigVo>>> streamBotConfigsChunked(int chunkSize);
    BotConfigVo findById(UUID id);
    BotConfigVo create(BotConfigVo vo);
    BotConfigVo update(UUID id, BotConfigVo vo);
    void delete(UUID id);
}
