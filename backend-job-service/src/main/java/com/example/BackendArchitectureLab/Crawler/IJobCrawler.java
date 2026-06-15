package com.example.BackendArchitectureLab.Crawler;

/**
 * Interface for job posting crawlers.
 * Different implementations can handle different levels of website complexity.
 */
public interface IJobCrawler {

    /**
     * Crawl a URL and return the raw HTML content.
     *
     * @param url the URL to crawl
     * @return the raw HTML content
     */
    String crawl(String url);
}
