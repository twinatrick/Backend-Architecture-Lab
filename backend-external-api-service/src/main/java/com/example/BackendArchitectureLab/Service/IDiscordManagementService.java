package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import com.example.BackendArchitectureLab.Dto.Query.DiscordGfSessionSearchQuery;
import com.example.BackendArchitectureLab.Dto.Query.DiscordSubscriptionSearchQuery;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface IDiscordManagementService {

    PageResult<DiscordGfSessionVo> searchSessions(DiscordGfSessionSearchQuery query);

    Flux<ServerSentEvent<List<DiscordGfSessionVo>>> streamSessionsChunked(int chunkSize);

    DiscordGfSessionVo getSessionById(UUID id);

    List<DiscordGfMessageVo> getSessionMessages(UUID id);

    DiscordGfSessionVo toggleSessionActive(UUID id);

    void deleteSession(UUID id);

    PageResult<DiscordSubscriptionVo> searchSubscriptions(DiscordSubscriptionSearchQuery query);

    Flux<ServerSentEvent<List<DiscordSubscriptionVo>>> streamSubscriptionsChunked(int chunkSize);

    void deleteSubscription(UUID id);
}
