package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Dto.Query.AlertCheckLimitSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IAlertCheckLimitService;
import com.example.BackendArchitectureLab.Util.BatchCacheHelper;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.DataAccess.IAlertCheckLimitDataAccess;
import com.example.BackendArchitectureLab.Mapper.AlertCheckLimitMapper;
import com.example.BackendArchitectureLab.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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

import com.example.BackendArchitectureLab.Util.TransactionExecutor;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertCheckLimitService implements IAlertCheckLimitService {
    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "tableName", "columnName", "limitValue",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    private final TransactionExecutor transactionExecutor;
    private final IAlertCheckLimitDataAccess alertCheckLimitDataAccess;
    private final AlertCheckLimitMapper alertCheckLimitMapper;
    private final CacheManager cacheManager;

    @Override
    @Cacheable(value = "alertCheckLimits", key = "#id")
    public AlertCheckLimitVo getLimitById(UUID id) {
        return transactionExecutor.executeReadOnly(() ->
                alertCheckLimitDataAccess.findById(id)
                        .map(alertCheckLimitMapper::toVo)
                        .orElseThrow(() -> new IllegalArgumentException("找不到指定的告警限制：" + id))
        );
    }

    @Override
    public BatchResultVo<AlertCheckLimitVo> getLimitsByIds(List<UUID> ids) {
        return BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                "alertCheckLimits",
                ids,
                missingIds -> transactionExecutor.executeReadOnly(() -> {
                    List<AlertCheckLimit> entities = alertCheckLimitDataAccess.findAllById(new ArrayList<>(missingIds));
                    List<AlertCheckLimitVo> vos = new ArrayList<>();
                    for (AlertCheckLimit entity : entities) {
                        vos.add(alertCheckLimitMapper.toVo(entity));
                    }
                    return vos;
                }),
                AlertCheckLimitVo::getId
        );
    }

    @Override
    @Cacheable(value = "alertCheckLimit", key = "#tableName + '.' + #column", sync = true)
    public AlertCheckLimitVo getLimit(String tableName, String column) {
        return transactionExecutor.executeReadOnly(() -> {
            AlertCheckLimit limit = getLimitEntity(tableName, column);
            return limit == null ? null : alertCheckLimitMapper.toVo(limit);
        });
    }

    @Override
    @Transactional
    @CachePut(value = "alertCheckLimit", key = "#tableName + '.' + #column")
    public AlertCheckLimitVo insertLimit(String tableName, String column, double limitValue) {
        AlertCheckLimit alertCheckLimit = new AlertCheckLimit();
        alertCheckLimit.setTableName(tableName);
        alertCheckLimit.setColumnName(column);
        alertCheckLimit.setLimitValue(limitValue);
        List<AlertCheckLimit> limitList = alertCheckLimitDataAccess.findByTableNameAndColumnName(
                alertCheckLimit.getTableName(),
                alertCheckLimit.getColumnName()
        );
        if (limitList.isEmpty()) {
            AlertCheckLimit limit = alertCheckLimitDataAccess.save(alertCheckLimit);
            return alertCheckLimitMapper.toVo(limit);
        }
        AlertCheckLimit limit = limitList.getFirst();
        limit.setLimitValue(limitValue);
        alertCheckLimitDataAccess.save(limit);
        return alertCheckLimitMapper.toVo(limit);
    }

    @Override
    @Transactional
    public AlertCheckLimitVo addLimit(AlertCheckLimitVo alertCheckLimitVo) {
        return insertLimit(
                alertCheckLimitVo.getTableName(),
                alertCheckLimitVo.getColumnName(),
                alertCheckLimitVo.getLimitValue()
        );
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "alertCheckLimits", key = "#alertCheckLimitVo.id", condition = "#alertCheckLimitVo.id != null"),
        @CacheEvict(value = "alertCheckLimit", key = "#alertCheckLimitVo.tableName + '.' + #alertCheckLimitVo.columnName")
    })
    public AlertCheckLimitVo updateLimit(AlertCheckLimitVo alertCheckLimitVo) {
        AlertCheckLimit updated = updateEntity(alertCheckLimitMapper.toEntity(alertCheckLimitVo));
        return alertCheckLimitMapper.toVo(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertCheckLimitVo> getLimit() {
        return alertCheckLimitDataAccess.findAll().stream().map(alertCheckLimitMapper::toVo).toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "alertCheckLimits", key = "#alertCheckLimitVo.id", condition = "#alertCheckLimitVo.id != null"),
        @CacheEvict(value = "alertCheckLimit", key = "#alertCheckLimitVo.tableName + '.' + #alertCheckLimitVo.columnName")
    })
    public void deleteLimit(AlertCheckLimitVo alertCheckLimitVo) {
        deleteLimitEntity(alertCheckLimitMapper.toEntity(alertCheckLimitVo));
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResult<AlertCheckLimitVo> searchAlertCheckLimits(AlertCheckLimitSearchQuery query) {
        // 驗證排序欄位與方向
        SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());
        
        // 執行分頁查詢
        Page<AlertCheckLimit> page = alertCheckLimitDataAccess.searchAlertCheckLimits(query);
        
        // 轉換為 VO
        List<AlertCheckLimitVo> content = page.getContent().stream()
                .map(alertCheckLimitMapper::toVo)
                .toList();
        
        return PageResult.of(page, content);
    }

    @Override
    public Flux<ServerSentEvent<List<AlertCheckLimitVo>>> streamAlertCheckLimitsChunked(int chunkSize) {
        final int effectiveChunkSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<AlertCheckLimitVo>>> dataFlux = Flux.<ServerSentEvent<List<AlertCheckLimitVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<AlertCheckLimit> alertPage = transactionExecutor.executeReadOnly(() ->
                                alertCheckLimitDataAccess.findAllPaged(PageRequest.of(page, effectiveChunkSize, Sort.by("id").ascending()))
                        );

                        if (alertPage.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        List<AlertCheckLimitVo> voList = alertPage.getContent().stream()
                                .map(alertCheckLimitMapper::toVo)
                                .toList();

                        sink.next(ServerSentEvent.<List<AlertCheckLimitVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(voList)
                                .build());

                        if (!alertPage.hasNext()) {
                            sink.complete();
                            return page;
                        }
                        return page + 1;
                    } catch (Exception ex) {
                        log.error("告警檢查限制 SSE 資料串流查詢失敗 [page={}]: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<AlertCheckLimitVo>> completeEvent = ServerSentEvent.<List<AlertCheckLimitVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<AlertCheckLimitVo>>> streamWithComplete = Flux.concat(dataFlux, Flux.just(completeEvent));

        Flux<ServerSentEvent<List<AlertCheckLimitVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(sequence -> ServerSentEvent.<List<AlertCheckLimitVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(streamWithComplete, heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("客戶端中斷告警檢查限制 SSE 串流連線"))
                .onErrorResume(ex -> {
                    log.error("告警檢查限制 SSE 串流處理發生異常: {}", ex.getMessage(), ex);
                    return Flux.just(ServerSentEvent.<List<AlertCheckLimitVo>>builder()
                            .event("error")
                            .comment(ex.getMessage() != null ? ex.getMessage() : "Unknown streaming error")
                            .data(List.of())
                            .build());
                });
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "alertCheckLimits", key = "#alertCheckLimit.id", condition = "#alertCheckLimit.id != null"),
        @CacheEvict(value = "alertCheckLimit", key = "#alertCheckLimit.tableName + '.' + #alertCheckLimit.columnName")
    })
    public AlertCheckLimit updateEntity(AlertCheckLimit alertCheckLimit) {
        return alertCheckLimitDataAccess.save(alertCheckLimit);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "alertCheckLimits", key = "#alertCheckLimit.id", condition = "#alertCheckLimit.id != null"),
        @CacheEvict(value = "alertCheckLimit", key = "#alertCheckLimit.tableName + '.' + #alertCheckLimit.columnName")
    })
    public void deleteLimitEntity(AlertCheckLimit alertCheckLimit) {
        alertCheckLimitDataAccess.delete(alertCheckLimit);
    }

    private AlertCheckLimit getLimitEntity(String tableName, String column) {
        List<AlertCheckLimit> limits = alertCheckLimitDataAccess.findByTableNameAndColumnName(tableName, column);
        if (limits.isEmpty()) {
            return null;
        }
        return limits.getFirst();
    }
}
