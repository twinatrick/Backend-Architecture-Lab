package com.example.BackendArchitectureLab.Crawler.impl;

import com.example.BackendArchitectureLab.Crawler.IJobCrawler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;

@Slf4j
@Component
public class CompositeJobCrawler implements IJobCrawler {

    @Autowired
    private JsoupJobCrawler jsoupJobCrawler;
    @Autowired
    private SeleniumJobCrawler seleniumJobCrawler;

    @Override
    public String crawl(String url) {
        try {
            log.info("Attempting to crawl with Jsoup first: {}", url);
            return jsoupJobCrawler.crawl(url);
        } catch (Exception e) {
            String domain = extractDomain(url);
            log.warn("Jsoup crawling failed for [{}], falling back to Selenium: {}", domain, url);
            return seleniumJobCrawler.crawl(url);
        }
    }

    private String extractDomain(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            log.warn("無法解析域名 {}: {}", url, e.toString());
            return "unknown";
        }
    }
}
