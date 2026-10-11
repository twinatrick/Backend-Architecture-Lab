package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IDiscordGfMessageDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IDiscordGfSessionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IDiscordSubscriptionDataAccess;
import com.example.BackendArchitectureLab.Entity.DiscordGfMessage;
import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.DiscordGfMessageMapper;
import com.example.BackendArchitectureLab.Mapper.DiscordSubscriptionMapper;
import com.example.BackendArchitectureLab.Mapper.GfSessionMapper;
import com.example.BackendArchitectureLab.Service.IDiscordManagementService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import com.example.BackendArchitectureLab.Vo.Search.DiscordGfSessionSearchQuery;
import com.example.BackendArchitectureLab.Vo.Search.DiscordSubscriptionSearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordManagementService implements IDiscordManagementService {

    private static final SearchSortPolicy SEARCH_SESSION_SORT_POLICY = new SearchSortPolicy(
            "id", "version", "guildId", "channelId", "userId", "active", "prompt",
            "voiceEnabled", "voiceSampleKey", "voiceSampleText", "gfName", "gfAvatarUrl", "language",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    private static final SearchSortPolicy SEARCH_SUBSCRIPTION_SORT_POLICY = new SearchSortPolicy(
            "id", "version", "guildId", "channelId", "botType", "webhookUrl", "webhookId", "language",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    private final IDiscordGfSessionDataAccess discordGfSessionDataAccess;
    private final IDiscordGfMessageDataAccess discordGfMessageDataAccess;
    private final IDiscordSubscriptionDataAccess discordSubscriptionDataAccess;
    private final GfSessionMapper gfSessionMapper;
    private final DiscordGfMessageMapper discordGfMessageMapper;
    private final DiscordSubscriptionMapper discordSubscriptionMapper;
    private final TransactionExecutor transactionExecutor;

    @Override
    public PageResult<DiscordGfSessionVo> searchSessions(DiscordGfSessionSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            SEARCH_SESSION_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());
            Page<DiscordGfSession> page = discordGfSessionDataAccess.searchSessions(query);
            List<DiscordGfSessionVo> vos = gfSessionMapper.toDiscordVoList(page.getContent());
            return PageResult.of(page, vos);
        });
    }

    @Override
    public Flux<ServerSentEvent<List<DiscordGfSessionVo>>> streamSessionsChunked(int chunkSize) {
        final int safeChunkSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<DiscordGfSessionVo>>> dataFlux = Flux.<ServerSentEvent<List<DiscordGfSessionVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<DiscordGfSessionVo> sessionPage = transactionExecutor.executeReadOnly(() -> {
                            PageRequest pageRequest = PageRequest.of(page, safeChunkSize, Sort.by(Sort.Direction.ASC, "id"));
                            return discordGfSessionDataAccess.findAllPaged(pageRequest)
                                    .map(gfSessionMapper::toVo);
                        });

                        List<DiscordGfSessionVo> content = sessionPage.getContent();
                        if (content.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<DiscordGfSessionVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(content)
                                .build());

                        if (sessionPage.isLast()) {
                            sink.complete();
                        }
                        return page + 1;
                    } catch (Exception ex) {
                        log.error("Error streaming discord sessions at page {}: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<DiscordGfSessionVo>> completeEvent = ServerSentEvent.<List<DiscordGfSessionVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<DiscordGfSessionVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(sequence -> ServerSentEvent.<List<DiscordGfSessionVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(Flux.concat(dataFlux, Flux.just(completeEvent)), heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("Client cancelled discord sessions stream"))
                .onErrorResume(ex -> {
                    log.error("Fatal error during discord sessions streaming: {}", ex.getMessage(), ex);
                    return Flux.just(ServerSentEvent.<List<DiscordGfSessionVo>>builder()
                            .event("error")
                            .data(List.of())
                            .build());
                });
    }

    @Override
    public DiscordGfSessionVo getSessionById(UUID id) {
        return discordGfSessionDataAccess.findById(id)
                .map(gfSessionMapper::toVo)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Discord session not found", 404));
    }

    @Override
    public List<DiscordGfMessageVo> getSessionMessages(UUID id) {
        if (discordGfSessionDataAccess.findById(id).isEmpty()) {
            throw new AppException("NOT_FOUND", "Discord session not found", 404);
        }
        List<DiscordGfMessage> messages = discordGfMessageDataAccess.findMessagesBySessionId(id);
        return discordGfMessageMapper.toVoList(messages);
    }

    @Override
    public DiscordGfSessionVo toggleSessionActive(UUID id) {
        DiscordGfSession session = discordGfSessionDataAccess.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Discord session not found", 404));
        session.setActive(!Boolean.TRUE.equals(session.getActive()));
        DiscordGfSession saved = discordGfSessionDataAccess.save(session);
        return gfSessionMapper.toVo(saved);
    }

    @Override
    public void deleteSession(UUID id) {
        DiscordGfSession session = discordGfSessionDataAccess.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Discord session not found", 404));
        discordGfSessionDataAccess.delete(session);
    }

    @Override
    public PageResult<DiscordSubscriptionVo> searchSubscriptions(DiscordSubscriptionSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            SEARCH_SUBSCRIPTION_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());
            Page<DiscordSubscription> page = discordSubscriptionDataAccess.searchSubscriptions(query);
            List<DiscordSubscriptionVo> vos = discordSubscriptionMapper.toVoList(page.getContent());
            return PageResult.of(page, vos);
        });
    }

    @Override
    public Flux<ServerSentEvent<List<DiscordSubscriptionVo>>> streamSubscriptionsChunked(int chunkSize) {
        final int safeChunkSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<DiscordSubscriptionVo>>> dataFlux = Flux.<ServerSentEvent<List<DiscordSubscriptionVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<DiscordSubscriptionVo> subPage = transactionExecutor.executeReadOnly(() -> {
                            PageRequest pageRequest = PageRequest.of(page, safeChunkSize, Sort.by(Sort.Direction.ASC, "id"));
                            return discordSubscriptionDataAccess.findAllPaged(pageRequest)
                                    .map(discordSubscriptionMapper::toVo);
                        });

                        List<DiscordSubscriptionVo> content = subPage.getContent();
                        if (content.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<DiscordSubscriptionVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(content)
                                .build());

                        if (subPage.isLast()) {
                            sink.complete();
                        }
                        return page + 1;
                    } catch (Exception ex) {
                        log.error("Error streaming discord subscriptions at page {}: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<DiscordSubscriptionVo>> completeEvent = ServerSentEvent.<List<DiscordSubscriptionVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<DiscordSubscriptionVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(sequence -> ServerSentEvent.<List<DiscordSubscriptionVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(Flux.concat(dataFlux, Flux.just(completeEvent)), heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("Client cancelled discord subscriptions stream"))
                .onErrorResume(ex -> {
                    log.error("Fatal error during discord subscriptions streaming: {}", ex.getMessage(), ex);
                    return Flux.just(ServerSentEvent.<List<DiscordSubscriptionVo>>builder()
                            .event("error")
                            .data(List.of())
                            .build());
                });
    }

    @Override
    public void deleteSubscription(UUID id) {
        DiscordSubscription sub = discordSubscriptionDataAccess.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Discord subscription not found", 404));
        discordSubscriptionDataAccess.delete(sub);
    }
}
