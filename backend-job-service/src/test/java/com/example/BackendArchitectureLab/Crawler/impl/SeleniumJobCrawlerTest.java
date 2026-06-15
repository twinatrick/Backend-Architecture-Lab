package com.example.BackendArchitectureLab.Crawler.impl;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeleniumJobCrawlerTest {

    @InjectMocks
    private SeleniumJobCrawler seleniumJobCrawler;

    private MockedStatic<WebDriverManager> webDriverManagerStatic;

    @BeforeEach
    void setUp() {
        webDriverManagerStatic = mockStatic(WebDriverManager.class);
        WebDriverManager mockManager = mock(WebDriverManager.class);
        webDriverManagerStatic.when(WebDriverManager::chromedriver).thenReturn(mockManager);
        seleniumJobCrawler.init();
    }

    @AfterEach
    void tearDown() {
        if (webDriverManagerStatic != null) {
            webDriverManagerStatic.close();
        }
    }

    @Test
    @DisplayName("Should return page source on successful crawl and quit driver")
    void crawl_success() {
        String url = "https://example.com/jobs";

        try (MockedConstruction<ChromeDriver> mocked = mockConstruction(ChromeDriver.class,
                (mock, context) -> {
                    WebDriver.Options options = mock(WebDriver.Options.class);
                    WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
                    when(mock.manage()).thenReturn(options);
                    when(options.timeouts()).thenReturn(timeouts);
                    when(mock.getPageSource()).thenReturn("<html>selenium content</html>");
                })) {

            String result = seleniumJobCrawler.crawl(url);

            assertEquals("<html>selenium content</html>", result);
            ChromeDriver driver = mocked.constructed().get(0);
            verify(driver).get(url);
            verify(driver).manage();
            verify(driver).quit();
            verify(timeoutsFrom(driver)).pageLoadTimeout(Duration.ofMillis(30000));
        }
    }

    @Test
    @DisplayName("Should retry on first failure and succeed on second attempt")
    void crawl_retryOnFailureThenSuccess() {
        String url = "https://example.com/jobs";

        AtomicInteger constructionCount = new AtomicInteger(0);
        try (MockedConstruction<ChromeDriver> mocked = mockConstruction(ChromeDriver.class,
                (mock, context) -> {
                    WebDriver.Options options = mock(WebDriver.Options.class);
                    WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
                    when(mock.manage()).thenReturn(options);
                    when(options.timeouts()).thenReturn(timeouts);
                    when(mock.getPageSource()).thenReturn("<html>retry success</html>");
                    if (constructionCount.getAndIncrement() == 0) {
                        doThrow(new RuntimeException("Connection failed"))
                                .when(mock).get(anyString());
                    }
                })) {

            String result = seleniumJobCrawler.crawl(url);

            assertEquals("<html>retry success</html>", result);
            assertEquals(2, mocked.constructed().size());
            for (ChromeDriver driver : mocked.constructed()) {
                verify(driver).quit();
            }
        }
    }

    @Test
    @DisplayName("Should throw RuntimeException after all retries exhausted")
    void crawl_allRetriesFail() {
        String url = "https://example.com/jobs";

        try (MockedConstruction<ChromeDriver> mocked = mockConstruction(ChromeDriver.class,
                (mock, context) -> {
                    WebDriver.Options options = mock(WebDriver.Options.class);
                    WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
                    when(mock.manage()).thenReturn(options);
                    when(options.timeouts()).thenReturn(timeouts);
                    doThrow(new RuntimeException("Persistent failure"))
                            .when(mock).get(anyString());
                })) {

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> seleniumJobCrawler.crawl(url));

            assertTrue(ex.getMessage().contains("Failed to crawl URL with Selenium"));
            assertEquals(2, mocked.constructed().size());
            for (ChromeDriver driver : mocked.constructed()) {
                verify(driver).quit();
            }
        }
    }

    @Test
    @DisplayName("Should not call driver.quit when driver creation fails")
    void crawl_noQuitWhenDriverCreationFails() {
        String url = "https://example.com/jobs";

        try (MockedConstruction<ChromeDriver> mocked = mockConstruction(ChromeDriver.class,
                (mock, context) -> {
                    throw new RuntimeException("Driver init failed");
                })) {

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> seleniumJobCrawler.crawl(url));

            assertTrue(ex.getMessage().contains("Failed to crawl URL with Selenium"));
            assertTrue(mocked.constructed().isEmpty());
        }
    }

    private static WebDriver.Timeouts timeoutsFrom(ChromeDriver driver) {
        return driver.manage().timeouts();
    }
}
