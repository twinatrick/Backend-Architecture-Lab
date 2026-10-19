package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IBotConfigDataAccess;
import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.BotConfigMapper;
import com.example.BackendArchitectureLab.Service.IBotConfigService;
import com.example.BackendArchitectureLab.Util.BatchCacheHelper;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Vo.BotConfigVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Query.BotConfigSearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotConfigService implements IBotConfigService {

    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "version", "platform", "configKey", "configValue", "description",
            "costLimitDaily", "costAlertAt",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    private final IBotConfigDataAccess botConfigDataAccess;
    private final BotConfigMapper botConfigMapper;
    private final TransactionExecutor transactionExecutor;
    private final CacheManager cacheManager;

    @Override
    @Transactional(readOnly = true)
    public List<BotConfigVo> findAll() {
        return botConfigDataAccess.findAll().stream()
                .map(botConfigMapper::toVo)
                .toList();
    }

    @Override
    public PageResult<BotConfigVo> searchBotConfigs(BotConfigSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());

            Page<BotConfig> page = botConfigDataAccess.searchBotConfigs(query);

            List<BotConfigVo> vos = page.getContent().stream()
                    .map(botConfigMapper::toVo)
                    .toList();

            return PageResult.of(page, vos);
        });
    }

    @Override
    public Flux<ServerSentEvent<List<BotConfigVo>>> streamBotConfigsChunked(int chunkSize) {
        final int safeChunkSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<BotConfigVo>>> dataFlux = Flux.<ServerSentEvent<List<BotConfigVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<BotConfigVo> botConfigPage = transactionExecutor.executeReadOnly(() -> {
                            PageRequest pageRequest = PageRequest.of(page, safeChunkSize, Sort.by(Sort.Direction.ASC, "id"));
                            return botConfigDataAccess.findAllPaged(pageRequest)
                                    .map(botConfigMapper::toVo);
                        });

                        List<BotConfigVo> content = botConfigPage.getContent();
                        if (content.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<BotConfigVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(content)
                                .build());

                        if (botConfigPage.isLast()) {
                            sink.complete();
                        }
                        return page + 1;
                    } catch (Exception ex) {
                        log.error("Error streaming bot configs at page {}: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<BotConfigVo>> completeEvent = ServerSentEvent.<List<BotConfigVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<BotConfigVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(sequence -> ServerSentEvent.<List<BotConfigVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(Flux.concat(dataFlux, Flux.just(completeEvent)), heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("Client cancelled bot configs stream"))
                .onErrorResume(ex -> {
                    log.error("Fatal error during bot configs streaming: {}", ex.getMessage(), ex);
                    return Flux.just(ServerSentEvent.<List<BotConfigVo>>builder()
                            .event("error")
                            .data(List.of())
                            .build());
                });
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "botConfigs", key = "#id")
    public BotConfigVo findById(UUID id) {
        return botConfigDataAccess.findById(id)
                .map(botConfigMapper::toVo)
                .orElseThrow(() -> new AppException("NOT_FOUND", "BotConfig not found", 404));
    }

    @Override
    @Transactional(readOnly = true)
    public BatchResultVo<BotConfigVo> getConfigsByIds(List<UUID> ids) {
        return BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                "botConfigs",
                ids,
                missingIds -> transactionExecutor.executeReadOnly(() -> {
                    List<BotConfig> entities = botConfigDataAccess.findAllById(new ArrayList<>(missingIds));
                    List<BotConfigVo> vos = new ArrayList<>();
                    for (BotConfig entity : entities) {
                        vos.add(botConfigMapper.toVo(entity));
                    }
                    return vos;
                }),
                BotConfigVo::getId
        );
    }

    @Override
    @Transactional
    public BotConfigVo create(BotConfigVo vo) {
        BotConfig entity = botConfigMapper.toEntity(vo);
        return botConfigMapper.toVo(botConfigDataAccess.save(entity));
    }

    @Override
    @Transactional
    @CacheEvict(value = "botConfigs", key = "#id")
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
    @Transactional
    @CacheEvict(value = "botConfigs", key = "#id")
    public void delete(UUID id) {
        if (!botConfigDataAccess.existsById(id)) {
            throw new AppException("NOT_FOUND", "BotConfig not found", 404);
        }
        botConfigDataAccess.deleteById(id);
    }
}
