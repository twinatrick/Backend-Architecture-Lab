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
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscordManagementService implements IDiscordManagementService {

    private final IDiscordGfSessionDataAccess discordGfSessionDataAccess;
    private final IDiscordGfMessageDataAccess discordGfMessageDataAccess;
    private final IDiscordSubscriptionDataAccess discordSubscriptionDataAccess;
    private final GfSessionMapper gfSessionMapper;
    private final DiscordGfMessageMapper discordGfMessageMapper;
    private final DiscordSubscriptionMapper discordSubscriptionMapper;

    @Override
    public List<DiscordGfSessionVo> getSessions(String guildId, Boolean active) {
        List<DiscordGfSession> sessions;
        if (guildId != null && active != null) {
            sessions = discordGfSessionDataAccess.findByGuildIdAndActive(guildId, active);
        } else if (guildId != null) {
            sessions = discordGfSessionDataAccess.findByGuildId(guildId);
        } else if (active != null) {
            sessions = discordGfSessionDataAccess.findByActive(active);
        } else {
            sessions = discordGfSessionDataAccess.findAll();
        }
        return gfSessionMapper.toDiscordVoList(sessions);
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
    public List<DiscordSubscriptionVo> getSubscriptions(String guildId) {
        List<DiscordSubscription> subscriptions;
        if (guildId != null && !guildId.isBlank()) {
            subscriptions = discordSubscriptionDataAccess.findByGuildId(guildId);
        } else {
            subscriptions = discordSubscriptionDataAccess.findAll();
        }
        return discordSubscriptionMapper.toVoList(subscriptions);
    }

    @Override
    public void deleteSubscription(UUID id) {
        DiscordSubscription sub = discordSubscriptionDataAccess.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Discord subscription not found", 404));
        discordSubscriptionDataAccess.delete(sub);
    }
}
