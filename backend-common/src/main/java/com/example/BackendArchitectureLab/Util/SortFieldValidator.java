package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Exception.AppException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class SortFieldValidator {

    public static void validate(String sortBy, String sortDir, Set<String> allowedFields) {
        if (!allowedFields.contains(sortBy)) {
            throw new AppException("排序欄位錯誤",
                    String.format("非法的排序欄位: %s。允許的欄位: %s",
                            sortBy, String.join(", ", allowedFields)), 400);
        }
        
        validateSortDirection(sortDir);
    }

    public static void validateSortField(String sortBy, String... allowedFields) {
        Set<String> allowedSet = Arrays.stream(allowedFields)
                .collect(Collectors.toSet());

        if (!allowedSet.contains(sortBy)) {
            throw new AppException("排序欄位錯誤",
                    String.format("非法的排序欄位: %s。允許的欄位: %s",
                            sortBy, String.join(", ", allowedFields)), 400);
        }
    }

    public static void validateSortDirection(String sortDir) {
        if (!"asc".equalsIgnoreCase(sortDir) && !"desc".equalsIgnoreCase(sortDir)) {
            throw new AppException("排序欄位錯誤",
                    String.format("非法的排序方向: %s。允許的值: asc, desc", sortDir), 400);
        }
    }
}
