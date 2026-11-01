package com.example.BackendArchitectureLab.Util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("共用快取鍵格式判斷")
class CacheKeyValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "123e4567-e89b-12d3-a456-426614174000",
            "123E4567-E89B-12D3-A456-426614174000",
            "00000000-0000-0000-0000-000000000000"
    })
    @DisplayName("接受原有格式的 UUID，不額外限制版本或大小寫")
    void isUuidKey_shouldAcceptCanonicalUuid(String key) {
        assertTrue(CacheKeyValidator.isUuidKey(key));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "user@example.com", "1-1-1-1-1",
            "123e4567e89b12d3a456426614174000",
            "g23e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-42661417400",
            "123e4567-e89b-12d3-a456-4266141740000",
            " 123e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174000 ",
            "123e4567-e89b-12d3-a456-426614174000\n"
    })
    @DisplayName("拒絕空值、非 UUID 與帶額外字元的鍵")
    void isUuidKey_shouldRejectInvalidKey(String key) {
        assertFalse(CacheKeyValidator.isUuidKey(key));
    }
}
