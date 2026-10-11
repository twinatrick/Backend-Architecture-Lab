package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;

import java.util.List;
import java.util.UUID;

public interface IDiscordManagementService {

    List<DiscordGfSessionVo> getSessions(String guildId, Boolean active);

    DiscordGfSessionVo getSessionById(UUID id);

    List<DiscordGfMessageVo> getSessionMessages(UUID id);

    DiscordGfSessionVo toggleSessionActive(UUID id);

    void deleteSession(UUID id);

    List<DiscordSubscriptionVo> getSubscriptions(String guildId);

    void deleteSubscription(UUID id);
}
