package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.MealMediaDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealReceiptDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealRecordDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealRestaurantLocationDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.MealSpecification;
import com.example.BackendArchitectureLab.Dto.Query.MealSearchQuery;
import com.example.BackendArchitectureLab.Dto.Request.CreateMealRequest;
import com.example.BackendArchitectureLab.Dto.Request.MealDetailsRequest;
import com.example.BackendArchitectureLab.Entity.MealReceipt;
import com.example.BackendArchitectureLab.Entity.MealRecord;
import com.example.BackendArchitectureLab.Entity.MealRestaurantLocation;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.MealRecordMapper;
import com.example.BackendArchitectureLab.Service.IMealRecordService;
import com.example.BackendArchitectureLab.Service.IMealRestaurantLocationService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Util.MealImageValidator;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.MealReceiptOcrCandidateVo;
import com.example.BackendArchitectureLab.Vo.MealRecordVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealRecordService implements IMealRecordService {
    private static final SearchSortPolicy SORTS = new SearchSortPolicy("id", "eatenAt", "amount", "rating", "mealName");
    private final MealRecordDataAccess meals;
    private final MealMediaDataAccess media;
    private final MealReceiptDataAccess receipts;
    private final MealRestaurantLocationDataAccess locations;
    private final IMealRestaurantLocationService locationService;
    private final MealRecordMapper mapper;
    private final TransactionExecutor transactions;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Override
    public MealRecordVo create(UUID userId, CreateMealRequest request) {
        validate(request);
        String hash = fingerprint(request);
        try {
            return transactions.executeWritable(() -> {
                MealRecord existing = meals.byOperation(userId, request.getOperationId()).orElse(null);
                if (existing != null) return replay(userId, existing, hash);
                validateReceiptAndAmountExplanation(userId, null, request);
                UUID locationId = locationService.resolveLocationForMeal(userId, request);
                MealRecord meal = new MealRecord();
                mapper.update(request, meal);
                meal.setRestaurantLocationId(locationId);
                meal.setUserId(userId);
                meal.setOperationId(request.getOperationId());
                meal.setRequestHash(hash);
                return enrichVo(userId, meals.save(meal));
            });
        } catch (DataIntegrityViolationException exception) {
            // 唯一鍵競爭須在原交易結束後重新讀取，不能繼續使用已回滾的交易。
            return transactions.executeReadOnly(() -> meals.byOperation(userId, request.getOperationId())
                    .map(meal -> replay(userId, meal, hash)).orElseThrow(() -> exception));
        }
    }

    private MealRecordVo replay(UUID userId, MealRecord meal, String hash) {
        if (!Objects.equals(meal.getRequestHash(), hash)) throw new AppException("OPERATION_CONFLICT", "相同操作識別碼不能提交不同內容", 409);
        if (meal.getDeletedAt() != null) throw new AppException("MEAL_DELETED", "這次操作建立的餐點已刪除，不會重新建立", 410);
        return enrichVo(userId, meal);
    }

    @Override
    public MealRecordVo get(UUID userId, UUID id) {
        return transactions.executeReadOnly(() -> enrichVo(userId, meals.owned(userId, id, false)));
    }

    @Override
    public MealRecordVo update(UUID userId, UUID id, long revision, MealDetailsRequest request) {
        validate(request);
        return transactions.executeWritable(() -> {
            MealRecord meal = meals.owned(userId, id, true);
            if (!Objects.equals(meal.getVersion(), revision)) throw new AppException("REVISION_CONFLICT", "餐點已更新，請重新載入後確認", 409);
            validateReceiptAndAmountExplanation(userId, id, request);
            UUID locationId = locationService.resolveLocationForMeal(userId, request);
            mapper.update(request, meal);
            meal.setRestaurantLocationId(locationId);
            return enrichVo(userId, meals.save(meal));
        });
    }

    @Override
    public void delete(UUID userId, UUID id) {
        transactions.runWritable(() -> {
            MealRecord meal = meals.owned(userId, id, true);
            meal.setDeletedAt(new Date());
            media.deleteAll(id);
            meals.save(meal);
        });
    }

    @Override
    public PageResult<MealRecordVo> search(UUID userId, MealSearchQuery query) {
        validate(query);
        SORTS.validate(query.getSortBy(), query.getSortDir());
        if (query.getPage() == null || query.getSize() == null || query.getSortDir() == null) badQuery();
        if (query.getDateFrom() != null && query.getDateTo() != null && query.getDateFrom().isAfter(query.getDateTo())) badQuery();
        if (query.getAmountMin() != null && query.getAmountMax() != null && query.getAmountMin().compareTo(query.getAmountMax()) > 0) badQuery();
        if (query.getRatingMin() != null && query.getRatingMax() != null && query.getRatingMin() > query.getRatingMax()) badQuery();
        if (Boolean.TRUE.equals(query.getUnrated()) && (query.getRatingMin() != null || query.getRatingMax() != null)) badQuery();
        return transactions.executeReadOnly(() -> {
            Sort sort = Sort.by(Sort.Direction.fromString(query.getSortDir()), query.getSortBy());
            if (!"id".equals(query.getSortBy())) sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
            Page<MealRecord> page = meals.search(MealSpecification.owned(userId, query), PageRequest.of(query.getPage(), query.getSize(), sort));
            return PageResult.of(page, page.getContent().stream().map(meal -> enrichVo(userId, meal)).toList());
        });
    }

    @Override
    public Flux<ServerSentEvent<List<MealRecordVo>>> streamMealsChunked(UUID userId, int chunkSize) {
        final int safeChunkSize = Math.max(10, Math.min(chunkSize, 1000));
        MealSearchQuery emptyQuery = new MealSearchQuery();

        Flux<ServerSentEvent<List<MealRecordVo>>> dataFlux = Flux.<ServerSentEvent<List<MealRecordVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<MealRecordVo> mealPage = transactions.executeReadOnly(() -> {
                            Sort sort = Sort.by(Sort.Direction.DESC, "eatenAt").and(Sort.by(Sort.Direction.DESC, "id"));
                            PageRequest pageRequest = PageRequest.of(page, safeChunkSize, sort);
                            return meals.search(MealSpecification.owned(userId, emptyQuery), pageRequest)
                                    .map(meal -> enrichVo(userId, meal));
                        });

                        List<MealRecordVo> content = mealPage.getContent();
                        if (content.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<MealRecordVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(content)
                                .build());

                        if (mealPage.isLast()) {
                            sink.complete();
                        }
                        return page + 1;
                    } catch (Exception ex) {
                        log.error("Error streaming meals at page {}: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<MealRecordVo>> completeEvent = ServerSentEvent.<List<MealRecordVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<MealRecordVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(sequence -> ServerSentEvent.<List<MealRecordVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(Flux.concat(dataFlux, Flux.just(completeEvent)), heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("Client cancelled meals stream"))
                .onErrorResume(ex -> {
                    log.error("Fatal error during meals streaming: {}", ex.getMessage(), ex);
                    return Flux.just(ServerSentEvent.<List<MealRecordVo>>builder()
                            .event("error")
                            .data(List.of())
                            .build());
                });
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new AppException("INVALID_MEAL_REQUEST", "餐點輸入資料不合法", 400);
    }

    private void validateReceiptAndAmountExplanation(UUID userId, UUID currentMealId, MealDetailsRequest request) {
        boolean hasExplanation = request.getAmountExplanation() != null && !request.getAmountExplanation().isBlank();
        if (request.getReceiptId() == null) {
            if (!hasExplanation) {
                throw new AppException("INVALID_MEAL_REQUEST", "無消費明細時必須填寫金額說明", 400);
            }
            return;
        }
        MealReceipt receipt = receipts.owned(userId, request.getReceiptId(), false);
        Integer itemIdx = request.getReceiptItemIndex();
        if (itemIdx != null) {
            boolean alreadyRecorded = meals.findActiveByReceipt(userId, receipt.getId()).stream()
                    .anyMatch(existing -> !Objects.equals(existing.getId(), currentMealId)
                            && Objects.equals(existing.getReceiptItemIndex(), itemIdx));
            if (alreadyRecorded && !Boolean.TRUE.equals(request.getConfirmDuplicateReceiptItem())) {
                throw new AppException("DUPLICATE_RECEIPT_ITEM", "此消費明細品項已建立過餐點紀錄，請確認是否重複建立", 409);
            }
        }
        boolean matchesOcrCandidate = false;
        if (itemIdx != null && "SUCCEEDED".equals(receipt.getOcrStatus()) && receipt.getOcrCandidatesJson() != null) {
            try {
                List<MealReceiptOcrCandidateVo> candidates = objectMapper.readValue(
                        receipt.getOcrCandidatesJson(),
                        new TypeReference<List<MealReceiptOcrCandidateVo>>() {
                        });
                for (MealReceiptOcrCandidateVo candidate : candidates) {
                    if (candidate.getItemIndex() == itemIdx
                            && candidate.getAmount() != null
                            && candidate.getAmount().compareTo(request.getAmount()) == 0) {
                        matchesOcrCandidate = true;
                        break;
                    }
                }
            } catch (Exception ignored) {
                matchesOcrCandidate = false;
            }
        }
        if (!matchesOcrCandidate && !hasExplanation) {
            throw new AppException("INVALID_MEAL_REQUEST", "未選擇相符的明細 OCR 品項金額或覆寫金額時，必須填寫金額說明", 400);
        }
    }

    private MealRecordVo enrichVo(UUID userId, MealRecord meal) {
        MealRecordVo vo = mapper.toVo(meal);
        if (meal.getRestaurantLocationId() != null) {
            locations.findOwned(userId, meal.getRestaurantLocationId()).ifPresent(loc -> {
                vo.setCurrentRestaurantName(loc.getDisplayName());
                vo.setCurrentRestaurantStatus(loc.getBusinessStatus());
                vo.setMovedToLocationId(loc.getMovedToLocationId());
            });
        } else {
            vo.setCurrentRestaurantStatus("UNKNOWN");
        }
        return vo;
    }

    private static void badQuery() {
        throw new AppException("INVALID_MEAL_QUERY", "查詢區間或分頁條件不合法", 400);
    }

    private String fingerprint(CreateMealRequest request) {
        try {
            return HexFormat.of().formatHex(MealImageValidator.sha256().digest(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException exception) {
            throw new AppException("INVALID_MEAL_REQUEST", "無法解析餐點輸入", 400);
        }
    }
}
