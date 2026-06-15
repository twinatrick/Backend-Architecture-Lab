package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Entity.Skill;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 查詢規格建構器
 */
public class SkillSpecification {
    
    /**
     * 根據 SkillSearchQuery 建立查詢規格
     */
    public static Specification<Skill> buildSpecification(SkillSearchQuery query) {
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
            
            // 描述模糊查詢
            if (query.getDescription() != null && !query.getDescription().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("description")),
                    "%" + query.getDescription().toLowerCase() + "%"
                ));
            }
            
            // 創建者精確查詢
            if (query.getCreatedBy() != null && !query.getCreatedBy().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), query.getCreatedBy()));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
