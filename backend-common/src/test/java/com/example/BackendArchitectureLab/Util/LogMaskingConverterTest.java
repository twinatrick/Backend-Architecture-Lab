package com.example.BackendArchitectureLab.Util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LogMaskingConverterTest {

    @Mock
    private ILoggingEvent loggingEvent;

    private final LogMaskingConverter converter = new LogMaskingConverter();

    @Test
    void mask_shouldReturnNullOrEmpty_whenInputIsNullOrBlank() {
        assertNull(LogMaskingConverter.mask(null));
        assertEquals("", LogMaskingConverter.mask(""));
    }

    @Test
    void mask_shouldNotModifyNormalLogs() {
        String log = "User login successful for user: admin@example.com, requestId: req-123";
        assertEquals(log, LogMaskingConverter.mask(log));
    }

    @Test
    void mask_shouldMaskJsonPasswordAndTokens() {
        String jsonLog = "{\"username\": \"john\", \"password\": \"mySecret123\", \"accessToken\": \"jwt-token-val\"}";
        String masked = LogMaskingConverter.mask(jsonLog);

        assertTrue(masked.contains("\"password\": \"******\""));
        assertTrue(masked.contains("\"accessToken\": \"******\""));
        assertTrue(masked.contains("\"username\": \"john\""));
    }

    @Test
    void mask_shouldMaskKeyValueParameters() {
        String log = "Executing request: /api/v1/auth?token=abcdef123456&username=alice&password=mypassword";
        String masked = LogMaskingConverter.mask(log);

        assertTrue(masked.contains("token=******"));
        assertTrue(masked.contains("password=******"));
        assertTrue(masked.contains("username=alice"));
    }

    @Test
    void mask_shouldMaskBearerTokenWithStandardAndUrlSafeBase64() {
        // Standard base64 with +, /, =
        String log1 = "Headers: Authorization: Bearer eyJhbGciOi+JIUzI1Ni/IsInR5cCI6IkpXVCJ9==";
        String masked1 = LogMaskingConverter.mask(log1);
        assertEquals("Headers: Authorization: Bearer ******", masked1);

        // Bearer token inside JSON quotes
        String log2 = "{\"auth\": \"Bearer token+with/slash==\"}";
        String masked2 = LogMaskingConverter.mask(log2);
        assertEquals("{\"auth\": \"Bearer ******\"}", masked2);
    }

    @Test
    void mask_shouldMaskCreditCardNumbers() {
        String log1 = "Processing payment with card 4111-2222-3333-4444 on gateway";
        String masked1 = LogMaskingConverter.mask(log1);
        assertEquals("Processing payment with card ****-****-****-4444 on gateway", masked1);

        String log2 = "Card number: 4111222233334444";
        String masked2 = LogMaskingConverter.mask(log2);
        assertEquals("Card number: ****-****-****-4444", masked2);
    }

    @Test
    void transform_shouldDelegateToMask() {
        String log = "{\"secret\": \"super-confidential\"}";
        String result = converter.transform(loggingEvent, log);

        assertEquals("{\"secret\": \"******\"}", result);
    }

    @Test
    void lokiPatternLayout_shouldApplyMaskingToFormattedMessage() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayout layout = new PatternLayout();
        layout.setContext(lc);
        layout.getInstanceConverterMap().put("mask", LogMaskingConverter::new);
        layout.setPattern("logger=%logger %mask(%msg)");
        layout.start();

        LoggingEvent event = new LoggingEvent(
            "com.example.TestLogger",
            lc.getLogger("com.example.TestLogger"),
            Level.INFO,
            "User login attempted with password=plain-secret-pass and Bearer secret-token-xyz",
            null,
            null
        );

        String formatted = layout.doLayout(event);
        assertTrue(formatted.contains("password=******"));
        assertTrue(formatted.contains("Bearer ******"));
        assertFalse(formatted.contains("plain-secret-pass"));
        assertFalse(formatted.contains("secret-token-xyz"));

        layout.stop();
    }
}
