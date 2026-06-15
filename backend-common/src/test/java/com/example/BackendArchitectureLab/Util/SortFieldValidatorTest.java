package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排序欄位驗證工具測試
 */
@DisplayName("SortFieldValidator 測試")
class SortFieldValidatorTest {

    @Test
    @DisplayName("validate - 成功驗證有效的排序欄位和方向（asc）")
    void validate_shouldPass_whenFieldAndDirectionAreValid_Asc() {
        Set<String> allowedFields = Set.of("id", "name", "createdTime");
        
        assertDoesNotThrow(() ->
                SortFieldValidator.validate("name", "asc", allowedFields)
        );
    }

    @Test
    @DisplayName("validate - 成功驗證有效的排序欄位和方向（desc）")
    void validate_shouldPass_whenFieldAndDirectionAreValid_Desc() {
        Set<String> allowedFields = Set.of("id", "name", "createdTime");
        
        assertDoesNotThrow(() ->
                SortFieldValidator.validate("createdTime", "desc", allowedFields)
        );
    }

    @Test
    @DisplayName("validate - 成功驗證有效的排序欄位和方向（忽略大小寫）")
    void validate_shouldPass_whenDirectionIsCaseInsensitive() {
        Set<String> allowedFields = Set.of("id", "name");
        
        assertDoesNotThrow(() -> {
            SortFieldValidator.validate("id", "ASC", allowedFields);
            SortFieldValidator.validate("name", "DESC", allowedFields);
            SortFieldValidator.validate("id", "Asc", allowedFields);
            SortFieldValidator.validate("name", "DeSc", allowedFields);
        });
    }

    @Test
    @DisplayName("validate - 拋出異常當排序欄位不在允許清單中")
    void validate_shouldThrowException_whenFieldNotAllowed() {
        Set<String> allowedFields = Set.of("id", "name", "createdTime");
        
        AppException exception = assertThrows(AppException.class, () ->
                SortFieldValidator.validate("invalidField", "asc", allowedFields)
        );
        
        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertTrue(exception.getMessage().contains("非法的排序欄位: invalidField"));
        assertTrue(exception.getMessage().contains("允許的欄位"));
        assertEquals(400, exception.getHttpStatus());
    }

    @Test
    @DisplayName("validate - 拋出異常當排序方向不合法")
    void validate_shouldThrowException_whenDirectionInvalid() {
        Set<String> allowedFields = Set.of("id", "name");
        
        AppException exception = assertThrows(AppException.class, () ->
                SortFieldValidator.validate("id", "invalid", allowedFields)
        );
        
        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertTrue(exception.getMessage().contains("非法的排序方向: invalid"));
        assertTrue(exception.getMessage().contains("允許的值: asc, desc"));
        assertEquals(400, exception.getHttpStatus());
    }

    @Test
    @DisplayName("validateSortField - 成功驗證有效的排序欄位（varargs）")
    void validateSortField_shouldPass_whenFieldIsValid() {
        assertDoesNotThrow(() ->
                SortFieldValidator.validateSortField("name", "id", "name", "createdTime")
        );
    }

    @Test
    @DisplayName("validateSortField - 拋出異常當排序欄位不在允許清單中（varargs）")
    void validateSortField_shouldThrowException_whenFieldNotAllowed() {
        AppException exception = assertThrows(AppException.class, () ->
                SortFieldValidator.validateSortField("invalidField", "id", "name", "createdTime")
        );
        
        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertTrue(exception.getMessage().contains("非法的排序欄位: invalidField"));
        assertEquals(400, exception.getHttpStatus());
    }

    @Test
    @DisplayName("validateSortDirection - 成功驗證 asc")
    void validateSortDirection_shouldPass_whenAsc() {
        assertDoesNotThrow(() ->
                SortFieldValidator.validateSortDirection("asc")
        );
    }

    @Test
    @DisplayName("validateSortDirection - 成功驗證 desc")
    void validateSortDirection_shouldPass_whenDesc() {
        assertDoesNotThrow(() ->
                SortFieldValidator.validateSortDirection("desc")
        );
    }

    @Test
    @DisplayName("validateSortDirection - 成功驗證不區分大小寫")
    void validateSortDirection_shouldPass_whenCaseInsensitive() {
        assertDoesNotThrow(() -> {
            SortFieldValidator.validateSortDirection("ASC");
            SortFieldValidator.validateSortDirection("DESC");
            SortFieldValidator.validateSortDirection("Asc");
            SortFieldValidator.validateSortDirection("DeSc");
        });
    }

    @Test
    @DisplayName("validateSortDirection - 拋出異常當方向不合法")
    void validateSortDirection_shouldThrowException_whenInvalid() {
        AppException exception = assertThrows(AppException.class, () ->
                SortFieldValidator.validateSortDirection("invalid")
        );
        
        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertTrue(exception.getMessage().contains("非法的排序方向: invalid"));
        assertEquals(400, exception.getHttpStatus());
    }

    @Test
    @DisplayName("validateSortDirection - 拋出異常當方向為空字串")
    void validateSortDirection_shouldThrowException_whenEmpty() {
        AppException exception = assertThrows(AppException.class, () ->
                SortFieldValidator.validateSortDirection("")
        );
        
        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertEquals(400, exception.getHttpStatus());
    }

    @Test
    @DisplayName("validateSortDirection - 拋出異常當方向為 null")
    void validateSortDirection_shouldThrowException_whenNull() {
        AppException exception = assertThrows(AppException.class, () ->
                SortFieldValidator.validateSortDirection(null)
        );
        
        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertEquals(400, exception.getHttpStatus());
    }
}
