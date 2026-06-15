package com.example.BackendArchitectureLab.Crawler.impl;

import com.example.BackendArchitectureLab.Crawler.IJobCrawler;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class JsoupJobCrawler implements IJobCrawler {

    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final List<String> DYNAMIC_SITES = List.of(
        "104.com.tw"
    );

    @Override
    public String crawl(String url) {
        for (String site : DYNAMIC_SITES) {
            if (url.contains(site)) {
                log.warn("Skipping Jsoup for known dynamic site: {} (url: {})", site, url);
                throw new RuntimeException("Jsoup skipped for dynamic site: " + site);
            }
        }

        try {
            log.info("Crawling URL with Jsoup: {}", url);
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .followRedirects(true)
                    .get();
            return doc.html();
        } catch (IOException e) {
            log.warn("Jsoup 抓取網頁失敗: {}", url);
            throw new RuntimeException("Jsoup 抓取網頁失敗");
        }
    }
}
