package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.IInitAndCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "app.init.enabled", havingValue = "true", matchIfMissing = true)
public class InitRunner {

    @Autowired
    private IInitAndCheckService initAndCheckService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        initAndCheckService.initAndCheck();
    }
}
