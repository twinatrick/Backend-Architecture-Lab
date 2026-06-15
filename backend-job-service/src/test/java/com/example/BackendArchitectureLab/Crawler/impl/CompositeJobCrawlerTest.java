package com.example.BackendArchitectureLab.Crawler.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompositeJobCrawlerTest {

    @Mock
    private JsoupJobCrawler jsoupJobCrawler;

    @Mock
    private SeleniumJobCrawler seleniumJobCrawler;

    @InjectMocks
    private CompositeJobCrawler compositeJobCrawler;

    @Test
    @DisplayName("Should return Jsoup result when Jsoup crawl succeeds")
    void crawl_jsoupSuccess() {
        String url = "https://example.com/jobs";
        when(jsoupJobCrawler.crawl(url)).thenReturn("<html>jsoup content</html>");

        String result = compositeJobCrawler.crawl(url);

        assertEquals("<html>jsoup content</html>", result);
        verify(jsoupJobCrawler).crawl(url);
        verifyNoInteractions(seleniumJobCrawler);
    }

    @Test
    @DisplayName("Should fallback to Selenium when Jsoup throws exception")
    void crawl_fallbackToSeleniumWhenJsoupThrows() {
        String url = "https://example.com/jobs";
        when(jsoupJobCrawler.crawl(url)).thenThrow(new RuntimeException("Jsoup failed"));
        when(seleniumJobCrawler.crawl(url)).thenReturn("<html>selenium content</html>");

        String result = compositeJobCrawler.crawl(url);

        assertEquals("<html>selenium content</html>", result);
        verify(jsoupJobCrawler).crawl(url);
        verify(seleniumJobCrawler).crawl(url);
    }

    @Test
    @DisplayName("Should propagate exception when both Jsoup and Selenium fail")
    void crawl_bothFail() {
        String url = "https://example.com/jobs";
        when(jsoupJobCrawler.crawl(url)).thenThrow(new RuntimeException("Jsoup failed"));
        when(seleniumJobCrawler.crawl(url)).thenThrow(new RuntimeException("Selenium failed"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> compositeJobCrawler.crawl(url));

        assertTrue(ex.getMessage().contains("Selenium failed"));
        verify(jsoupJobCrawler).crawl(url);
        verify(seleniumJobCrawler).crawl(url);
    }

    @Test
    @DisplayName("Should fallback to Selenium even with malformed URL")
    void crawl_fallbackWithMalformedUrl() {
        String url = "invalid-url:::";
        when(jsoupJobCrawler.crawl(url)).thenThrow(new RuntimeException("Jsoup failed"));
        when(seleniumJobCrawler.crawl(url)).thenReturn("<html>fallback content</html>");

        String result = compositeJobCrawler.crawl(url);

        assertEquals("<html>fallback content</html>", result);
        verify(jsoupJobCrawler).crawl(url);
        verify(seleniumJobCrawler).crawl(url);
    }
}
