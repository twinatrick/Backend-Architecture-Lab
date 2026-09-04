package com.example.BackendArchitectureLab.Util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogMaskingConverter extends CompositeConverter<ILoggingEvent> {

    private static final Pattern[] MASK_PATTERNS = new Pattern[] {
        // JSON password, token, apiKey, secret
        Pattern.compile("(\"(?:password|pwd|secret|accessToken|refreshToken|apiKey|token)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
        // Key-value / Query / Form parameters: password=... or secret=...
        Pattern.compile("((?:password|pwd|secret|accessToken|refreshToken|apiKey|token)\\s*=\\s*)([^&\\s,\";]+)", Pattern.CASE_INSENSITIVE),
        // Bearer Token: Bearer ...
        Pattern.compile("(Bearer\\s+)([A-Za-z0-9-_]+(?:\\.[A-Za-z0-9-_]+)*)", Pattern.CASE_INSENSITIVE),
        // Credit card numbers (16 digits)
        Pattern.compile("\\b(\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?)(\\d{4})\\b")
    };

    private static final String MASK_REPLACEMENT = "******";

    @Override
    protected String transform(ILoggingEvent event, String in) {
        return mask(in);
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String masked = message;
        for (Pattern pattern : MASK_PATTERNS) {
            Matcher matcher = pattern.matcher(masked);
            if (matcher.find()) {
                if (pattern.pattern().contains("Bearer")) {
                    masked = matcher.replaceAll("$1" + MASK_REPLACEMENT);
                } else if (pattern.pattern().contains("\\b(\\d{4}")) {
                    masked = matcher.replaceAll("****-****-****-$2");
                } else if (pattern.pattern().startsWith("(\"")) {
                    masked = matcher.replaceAll("$1" + MASK_REPLACEMENT + "$3");
                } else {
                    masked = matcher.replaceAll("$1" + MASK_REPLACEMENT);
                }
            }
        }
        return masked;
    }
}
