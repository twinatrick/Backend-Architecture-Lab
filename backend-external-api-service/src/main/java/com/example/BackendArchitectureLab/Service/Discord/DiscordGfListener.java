package com.example.BackendArchitectureLab.Service.Discord;

import com.example.BackendArchitectureLab.Dto.Vo.ChatRequestVo;
import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DiscordGfListener extends ListenerAdapter {

    @Autowired
    private AiPyServiceFeignClient aiPyServiceFeignClient;

    @Autowired
    private IUsageTrackService usageTrackService;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        if (content.startsWith("!chat ")) {
            String prompt = content.substring(6);
            ChatRequestVo request = new ChatRequestVo(
                    List.of(Map.of("role", "user", "content", prompt)), null, false);
            var response = aiPyServiceFeignClient.chat(request);
            usageTrackService.track("discord-gf", "chat", "char", (long) prompt.length());
            event.getChannel().sendMessage(response.getContent()).queue();
        }
    }
}
