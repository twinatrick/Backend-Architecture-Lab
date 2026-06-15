package com.example.BackendArchitectureLab.Crawler.impl;

import com.example.BackendArchitectureLab.Crawler.IJobCrawler;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SeleniumJobCrawler implements IJobCrawler {

    private static final int PAGE_LOAD_TIMEOUT_MS = 30000;
    private static final int RETRY_DELAY_MS = 3000;
    private static final int MAX_RETRIES = 1;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private ChromeOptions chromeOptions;

    @PostConstruct
    public void init() {
        WebDriverManager.chromedriver().setup();
        chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless");
        chromeOptions.addArguments("--no-sandbox");
        chromeOptions.addArguments("--disable-dev-shm-usage");
        chromeOptions.addArguments("--disable-gpu");
        chromeOptions.addArguments("--window-size=1920,1080");
        chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
        chromeOptions.addArguments("--user-agent=" + USER_AGENT);
        log.info("Selenium ChromeDriver initialized");
    }

    @Override
    public String crawl(String url) {
        RuntimeException lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            WebDriver driver = null;
            try {
                if (attempt > 0) {
                    log.warn("Retrying Selenium crawl (attempt {}/{}): {}", attempt + 1, MAX_RETRIES + 1, url);
                    Thread.sleep(RETRY_DELAY_MS);
                }

                driver = new ChromeDriver(chromeOptions);
                driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofMillis(PAGE_LOAD_TIMEOUT_MS));

                log.info("Crawling URL with Selenium: {}", url);
                driver.get(url);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                return driver.getPageSource();
            } catch (Exception e) {
                log.warn("Selenium crawl failed (attempt {}/{}): {}", attempt + 1, MAX_RETRIES + 1, url);
                lastException = new RuntimeException("Failed to crawl URL with Selenium: " + url, e);
            } finally {
                if (driver != null) {
                    try {
                        driver.quit();
                    } catch (Exception e) {
                        log.warn("Failed to quit Selenium driver");
                    }
                }
            }
        }

        throw lastException;
    }
}
