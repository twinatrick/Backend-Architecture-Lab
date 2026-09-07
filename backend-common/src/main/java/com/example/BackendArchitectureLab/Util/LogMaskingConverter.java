package com.example.BackendArchitectureLab.Util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import java.util.List;
import java.util.regex.Pattern;

public class LogMaskingConverter extends CompositeConverter<ILoggingEvent> {

    private record MaskRule(Pattern pattern, String replacement) {
        String apply(String input) {
            return pattern.matcher(input).replaceAll(replacement);
        }
    }

    private static final String MASK_REPLACEMENT = "******";

    private static final List<MaskRule> RULES = List.of(
        // JSON credentials masking rule (password, token, secret, apiKey)
        new MaskRule(
            Pattern.compile("(\"(?:password|pwd|secret|accessToken|refreshToken|apiKey|token)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
            "$1" + MASK_REPLACEMENT + "$3"
        ),
        // Key-value, query, or form parameters masking rule
        new MaskRule(
            Pattern.compile("((?:password|pwd|secret|accessToken|refreshToken|apiKey|token)\\s*=\\s*)([^&\\s,\";]+)", Pattern.CASE_INSENSITIVE),
            "$1" + MASK_REPLACEMENT
        ),
        // Bearer Token masking rule (supports all base64 variants including +, /, =, -, _)
        new MaskRule(
            Pattern.compile("(Bearer\\s+)([^\"'\\s,;]+)", Pattern.CASE_INSENSITIVE),
            "$1" + MASK_REPLACEMENT
        ),
        // Credit card numbers (16 digits with or without delimiters)
        new MaskRule(
            Pattern.compile("\\b(\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?)(\\d{4})\\b"),
            "****-****-****-$2"
        )
    );

    @Override
    protected String transform(ILoggingEvent event, String in) {
        return mask(in);
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String masked = message;
        for (MaskRule rule : RULES) {
            masked = rule.apply(masked);
        }
        return masked;
    }
}
