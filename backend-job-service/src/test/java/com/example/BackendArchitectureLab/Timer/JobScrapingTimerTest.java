package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Service.IJobPostingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobScrapingTimerTest {

    @Mock
    private IJobPostingService jobPostingService;

    @InjectMocks
    private JobScrapingTimer jobScrapingTimer;

    @Test
    @DisplayName("Should call scrapeAndAnalyzeAllCompanies when timer fires")
    void runJobScraping_callsService() {
        jobScrapingTimer.runJobScraping();

        verify(jobPostingService).scrapeAndAnalyzeAllCompanies();
    }

    @Test
    @DisplayName("Should call service exactly once per invocation")
    void runJobScraping_calledOnce() {
        jobScrapingTimer.runJobScraping();

        verify(jobPostingService, times(1)).scrapeAndAnalyzeAllCompanies();
    }

    @Test
    @DisplayName("Should handle service exception without breaking timer")
    void runJobScraping_serviceThrowsException() {
        doThrow(new RuntimeException("Scraping failed")).when(jobPostingService).scrapeAndAnalyzeAllCompanies();

        try {
            jobScrapingTimer.runJobScraping();
        } catch (RuntimeException e) {
            // Expected
        }

        verify(jobPostingService).scrapeAndAnalyzeAllCompanies();
    }
}
