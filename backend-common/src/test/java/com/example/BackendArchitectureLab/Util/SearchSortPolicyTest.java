package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 搜尋排序策略測試
 */
@DisplayName("SearchSortPolicy 測試")
class SearchSortPolicyTest {

    private final SearchSortPolicy policy = new SearchSortPolicy("id", "name", "createdTime");

    @Test
    @DisplayName("validate - 成功驗證有效的排序欄位和方向")
    void validate_shouldPass_whenFieldAndDirectionAreValid() {
        assertDoesNotThrow(() -> policy.validate("name", "asc"));
        assertDoesNotThrow(() -> policy.validate("createdTime", "desc"));
        assertDoesNotThrow(() -> policy.validate("id", "ASC"));
        assertDoesNotThrow(() -> policy.validate("name", "DeSc"));
    }

    @Test
    @DisplayName("validate - 拋出異常當排序欄位不在允許清單中")
    void validate_shouldThrowException_whenFieldNotAllowed() {
        AppException exception = assertThrows(AppException.class, () ->
                policy.validate("invalidField", "asc")
        );

        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertTrue(exception.getMessage().contains("非法的排序欄位: invalidField"));
        assertTrue(exception.getMessage().contains("允許的欄位"));
        assertEquals(400, exception.getHttpStatus());
    }

    @Test
    @DisplayName("validate - 拋出異常當排序方向不合法")
    void validate_shouldThrowException_whenDirectionInvalid() {
        AppException exception = assertThrows(AppException.class, () ->
                policy.validate("id", "invalid")
        );

        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertTrue(exception.getMessage().contains("非法的排序方向: invalid"));
        assertTrue(exception.getMessage().contains("允許的值: asc, desc"));
        assertEquals(400, exception.getHttpStatus());
    }

    @Test
    @DisplayName("validate - 拋出異常當方向為 null")
    void validate_shouldThrowException_whenDirectionNull() {
        AppException exception = assertThrows(AppException.class, () ->
                policy.validate("id", null)
        );

        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertEquals(400, exception.getHttpStatus());
    }
}
