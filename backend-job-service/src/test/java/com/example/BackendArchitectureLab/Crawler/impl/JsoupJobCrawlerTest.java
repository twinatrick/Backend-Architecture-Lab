package com.example.BackendArchitectureLab.Crawler.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.Connection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JsoupJobCrawlerTest {

    @InjectMocks
    private JsoupJobCrawler jsoupJobCrawler;

    @Test
    @DisplayName("Should throw RuntimeException when URL contains known dynamic site")
    void crawl_skipsDynamicSite() {
        String url = "https://104.com.tw/jobs";

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> jsoupJobCrawler.crawl(url));

        assertTrue(ex.getMessage().contains("104.com.tw"));
    }

    @Test
    @DisplayName("Should return HTML content when Jsoup connects successfully")
    void crawl_success() throws IOException {
        String url = "https://example.com/jobs";

        try (MockedStatic<Jsoup> jsoupStatic = mockStatic(Jsoup.class)) {
            Connection connection = mock(Connection.class);
            Document document = mock(Document.class);

            jsoupStatic.when(() -> Jsoup.connect(url)).thenReturn(connection);
            when(connection.timeout(anyInt())).thenReturn(connection);
            when(connection.userAgent(anyString())).thenReturn(connection);
            when(connection.followRedirects(anyBoolean())).thenReturn(connection);
            when(connection.get()).thenReturn(document);
            when(document.html()).thenReturn("<html>success</html>");

            String result = jsoupJobCrawler.crawl(url);

            assertEquals("<html>success</html>", result);
            jsoupStatic.verify(() -> Jsoup.connect(url));
            verify(connection).timeout(10000);
            verify(connection).get();
            verify(document).html();
        }
    }

    @Test
    @DisplayName("Should wrap IOException in RuntimeException")
    void crawl_ioException() throws IOException {
        String url = "https://example.com/jobs";

        try (MockedStatic<Jsoup> jsoupStatic = mockStatic(Jsoup.class)) {
            Connection connection = mock(Connection.class);

            jsoupStatic.when(() -> Jsoup.connect(url)).thenReturn(connection);
            when(connection.timeout(anyInt())).thenReturn(connection);
            when(connection.userAgent(anyString())).thenReturn(connection);
            when(connection.followRedirects(anyBoolean())).thenReturn(connection);
            when(connection.get()).thenThrow(new IOException("Connection timeout"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> jsoupJobCrawler.crawl(url));

            assertTrue(ex.getMessage().contains("Jsoup 抓取網頁失敗"));
            verify(connection).get();
        }
    }

    @Test
    @DisplayName("Should not skip regular site URLs")
    void crawl_regularSiteNotSkipped() throws IOException {
        String url = "https://example.com/jobs";

        try (MockedStatic<Jsoup> jsoupStatic = mockStatic(Jsoup.class)) {
            Connection connection = mock(Connection.class);
            Document document = mock(Document.class);

            jsoupStatic.when(() -> Jsoup.connect(url)).thenReturn(connection);
            when(connection.timeout(anyInt())).thenReturn(connection);
            when(connection.userAgent(anyString())).thenReturn(connection);
            when(connection.followRedirects(anyBoolean())).thenReturn(connection);
            when(connection.get()).thenReturn(document);
            when(document.html()).thenReturn("<html>ok</html>");

            String result = jsoupJobCrawler.crawl(url);

            assertEquals("<html>ok</html>", result);
            jsoupStatic.verify(() -> Jsoup.connect(url));
        }
    }

    @Test
    @DisplayName("Should skip dynamic site with subdomain URL")
    void crawl_dynamicSiteSubdomain() {
        String url = "https://careers.104.com.tw/jobs";

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> jsoupJobCrawler.crawl(url));

        assertTrue(ex.getMessage().contains("104.com.tw"));
    }
}
