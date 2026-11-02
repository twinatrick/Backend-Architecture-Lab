package com.example.BackendArchitectureLab.Util;

import java.util.regex.Pattern;

/**
 * 快取鍵格式判斷，供單筆與批次快取共用，避免快取實作互相依賴。
 */
public final class CacheKeyValidator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private CacheKeyValidator() {
    }

    public static boolean isUuidKey(String key) {
        return key != null && UUID_PATTERN.matcher(key).matches();
    }
}
