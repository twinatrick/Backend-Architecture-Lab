package com.example.BackendArchitectureLab.Service;

import com.linecorp.bot.model.event.Event;

import java.util.List;

public interface ILineWebhookService {
    List<Event> parseEvents(String channelSecret, String body, String signature) throws Exception;
    void dispatchEvents(List<Event> events, Object service);
}
