package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Entity.Function;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Function 查詢規格建構器
 */
public class FunctionSpecification {
    
    /**
     * 根據 FunctionSearchQuery 建立查詢規格
     */
    public static Specification<Function> buildSpecification(FunctionSearchQuery query) {
        if (query == null) return null;
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 名稱模糊查詢
            if (query.getName() != null && !query.getName().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + query.getName().toLowerCase() + "%"
                ));
            }
            
            // 父功能ID精確查詢
            if (query.getParent() != null && !query.getParent().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("parent"), query.getParent()));
            }
            
            // 類型精確查詢
            if (query.getType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), query.getType()));
            }
            
            // 創建者精確查詢
            if (query.getCreatedBy() != null && !query.getCreatedBy().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), query.getCreatedBy()));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
