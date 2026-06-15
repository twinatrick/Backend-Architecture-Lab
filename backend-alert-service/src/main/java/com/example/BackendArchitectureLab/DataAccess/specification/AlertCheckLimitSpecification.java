package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.AlertCheckLimitSearchQuery;
import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * AlertCheckLimit 查詢規格建構器
 */
public class AlertCheckLimitSpecification {
    
    /**
     * 根據 AlertCheckLimitSearchQuery 建立查詢規格
     */
    public static Specification<AlertCheckLimit> buildSpecification(AlertCheckLimitSearchQuery query) {
        if (query == null) return null;
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 表名模糊查詢
            if (query.getTableName() != null && !query.getTableName().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("tableName")),
                    "%" + query.getTableName().toLowerCase() + "%"
                ));
            }
            
            // 欄位名模糊查詢
            if (query.getColumnName() != null && !query.getColumnName().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("columnName")),
                    "%" + query.getColumnName().toLowerCase() + "%"
                ));
            }
            
            // 限制值範圍查詢
            if (query.getLimitValueMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("limitValue"), query.getLimitValueMin()));
            }
            
            if (query.getLimitValueMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("limitValue"), query.getLimitValueMax()));
            }
            
            // 創建者精確查詢
            if (query.getCreatedBy() != null && !query.getCreatedBy().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), query.getCreatedBy()));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
