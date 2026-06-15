package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Service.IJobPostingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobScrapingTimer {

    @Autowired
    private IJobPostingService jobPostingService;

    @Scheduled(cron = "0 0 * * * *")
    public void runJobScraping() {
        log.info("Starting hourly job scraping for all companies");
        jobPostingService.scrapeAndAnalyzeAllCompanies();
        log.info("Hourly job scraping completed");
    }
}
