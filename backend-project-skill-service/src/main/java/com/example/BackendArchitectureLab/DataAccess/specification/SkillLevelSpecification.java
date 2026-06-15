package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * SkillLevel 查詢規格建構器
 */
public class SkillLevelSpecification {
    
    /**
     * 根據 SkillLevelSearchQuery 建立查詢規格
     */
    public static Specification<SkillLevel> buildSpecification(SkillLevelSearchQuery query) {
        if (query == null) return null;
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 技能ID精確查詢
            if (query.getSkillId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("skill").get("id"), query.getSkillId()));
            }
            
            // 等級數值精確查詢
            if (query.getLevelValue() != null) {
                predicates.add(criteriaBuilder.equal(root.get("levelValue"), query.getLevelValue()));
            }
            
            // 標題模糊查詢
            if (query.getTitle() != null && !query.getTitle().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("title")),
                    "%" + query.getTitle().toLowerCase() + "%"
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
