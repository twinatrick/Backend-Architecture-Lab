package com.example.BackendArchitectureLab.Service.Impl;

import org.springframework.cache.CacheManager;
import com.example.BackendArchitectureLab.Util.BatchCacheHelper;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.Search.FunctionSearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionQueryService implements IFunctionQueryService {
    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "name", "parent", "sort", "type",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    private final IFunctionDataAccess functionDataAccess;
    private final FunctionMapper functionMapper;
    private final TransactionExecutor transactionExecutor;
    private final CacheManager cacheManager;

    @Lazy
    private final IFunctionQueryService self;

    @Override
    public List<FunctionVo> getFunction() {
        return self.getFunctionListCache().getData();
    }

    @Override
    @Cacheable(value = "functions", key = "'all'", sync = true)
    public CacheListWrapper<FunctionVo> getFunctionListCache() {
        List<FunctionVo> list = functionDataAccess.findAll().stream().map(functionMapper::toVo).toList();
        return new CacheListWrapper<>(list);
    }

    @Override
    @Cacheable(value = "functions", key = "#id", sync = true)
    public FunctionVo getFunctionById(String id) {
        UUID uuid = mapUuid(id);
        if (uuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        Function function = functionDataAccess.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("Function not found")
        );
        return functionMapper.toVo(function);
    }

    @Override
    public BatchResultVo<FunctionVo> getFunctionsByIds(List<UUID> ids) {
        return BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                "functions",
                ids,
                missingUuids -> transactionExecutor.executeReadOnly(() -> {
                    List<Function> functions = functionDataAccess.findAllById(new ArrayList<>(missingUuids));
                    List<FunctionVo> list = new ArrayList<>();
                    for (Function function : functions) {
                        list.add(functionMapper.toVo(function));
                    }
                    return list;
                }),
                vo -> vo.getId() == null ? null : UUID.fromString(vo.getId())
        );
    }

    @Override
    @Cacheable(value = "functions", key = "'byname:' + #name", sync = true)
    public FunctionVo getFunctionByName(String name) {
        Function function = functionDataAccess.findFunctionByName(name);
        return function == null ? null : functionMapper.toVo(function);
    }

    @Override
    @Cacheable(value = "functions", key = "'bynameparent:' + #name + ':' + #parent", sync = true)
    public FunctionVo getFunctionByNameAndParent(String name, String parent) {
        List<Function> functionList = functionDataAccess.findFunctionByNameAndParent(name, parent);
        if (functionList.isEmpty()) {
            return null;
        }
        return functionMapper.toVo(functionList.getFirst());
    }

    @Override
    public FunctionVo getFunctionByPath(String oneLayer, String twoLayer, String threeLayer) {
        FunctionVo one = self.getFunctionByName(oneLayer);
        if (one == null) {
            return null;
        }
        FunctionVo two = self.getFunctionByNameAndParent(twoLayer, one.getId());
        if (two == null) {
            return null;
        }
        return self.getFunctionByNameAndParent(threeLayer, two.getId());
    }

    @Override
    public PageResult<FunctionVo> searchFunctions(FunctionSearchQuery query) {
        // 驗證排序欄位與方向
        SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());

        // 從快取取得所有 functions，避免重複 DB 查詢（解決 6s N+1/full-scan 問題）
        List<FunctionVo> allFunctions = self.getFunctionListCache().getData();

        // 記憶體篩選
        List<FunctionVo> filtered = allFunctions.stream()
                .filter(f -> query.getName() == null || query.getName().isBlank()
                        || (f.getName() != null && f.getName().contains(query.getName())))
                .filter(f -> query.getParent() == null || query.getParent().isBlank()
                        || query.getParent().equals(f.getParent()))
                .filter(f -> query.getType() == null
                        || (f.getType() != null && f.getType().equals(query.getType())))
                .toList();

        // 記憶體排序
        boolean isAsc = "asc".equalsIgnoreCase(query.getNormalizedSortDir());
        String sortBy = query.getSortBy();
        Comparator<FunctionVo> comparator = switch (sortBy) {
            case "name" -> Comparator.comparing(FunctionVo::getName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "type" -> Comparator.comparing(f -> f.getType() == null ? Integer.MAX_VALUE : f.getType());
            case "sort" -> Comparator.comparing(FunctionVo::getSort, Comparator.nullsLast(Comparator.naturalOrder()));
            default     -> Comparator.comparing(FunctionVo::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (!isAsc) comparator = comparator.reversed();
        List<FunctionVo> sorted = filtered.stream().sorted(comparator).toList();

        // 記憶體分頁
        int page = query.getPage();
        int size = query.getSize();
        int totalElements = sorted.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<FunctionVo> pageContent = sorted.subList(fromIndex, toIndex);

        Page<FunctionVo> functionPage = new PageImpl<>(pageContent,
                PageRequest.of(page, size), totalElements);
        return PageResult.of(functionPage, pageContent);
    }

    @Override
    public Flux<ServerSentEvent<List<FunctionVo>>> streamFunctionsChunked(int chunkSize) {
        int safeChunkSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<FunctionVo>>> dataFlux = Flux.<ServerSentEvent<List<FunctionVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<FunctionVo> functionPage = transactionExecutor.executeReadOnly(() -> {
                            Page<Function> entityPage = functionDataAccess.findAllPaged(
                                    PageRequest.of(page, safeChunkSize, Sort.by(Sort.Direction.ASC, "id"))
                            );

                            if (entityPage.isEmpty()) {
                                return new PageImpl<>(List.<FunctionVo>of(), entityPage.getPageable(), entityPage.getTotalElements());
                            }

                            List<FunctionVo> voList = entityPage.getContent().stream()
                                    .map(functionMapper::toVo)
                                    .toList();

                            return new PageImpl<>(voList, entityPage.getPageable(), entityPage.getTotalElements());
                        });

                        if (functionPage.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<FunctionVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(functionPage.getContent())
                                .build());

                        if (!functionPage.hasNext()) {
                            sink.complete();
                            return page;
                        }

                        return page + 1;
                    } catch (Exception ex) {
                        log.error("功能 SSE 分塊串流分頁查詢失敗 [page={}]: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<FunctionVo>> completeEvent = ServerSentEvent.<List<FunctionVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<FunctionVo>>> streamWithComplete = Flux.concat(dataFlux, Flux.just(completeEvent));

        Flux<ServerSentEvent<List<FunctionVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<List<FunctionVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(streamWithComplete, heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("客戶端中斷功能 SSE 串流連線"))
                .onErrorResume(ex -> {
                    log.error("功能 SSE 串流處理發生異常: {}", ex.getMessage(), ex);
                    return Flux.just(
                            ServerSentEvent.<List<FunctionVo>>builder()
                                    .event("error")
                                    .comment("串流處理發生異常，請聯繫管理員")
                                    .build()
                    );
                });
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }
}
