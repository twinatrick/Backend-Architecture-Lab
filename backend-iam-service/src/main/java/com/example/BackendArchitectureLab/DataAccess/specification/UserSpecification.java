package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * User 查詢規格建構器
 */
public class UserSpecification {
    
    /**
     * 根據 UserSearchQuery 建立查詢規格
     */
    public static Specification<User> buildSpecification(UserSearchQuery query) {
        if (query == null) return null;
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (query.getName() != null && !query.getName().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + query.getName().toLowerCase() + "%"
                ));
            }
            
            if (query.getEmail() != null && !query.getEmail().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("email")),
                    "%" + query.getEmail().toLowerCase() + "%"
                ));
            }
            
            if (query.getPhone() != null && !query.getPhone().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    root.get("phone"),
                    "%" + query.getPhone() + "%"
                ));
            }
            
            if (query.getDisabled() != null) {
                predicates.add(criteriaBuilder.equal(root.get("disabled"), query.getDisabled()));
            }
            
            if (query.getCreatedBy() != null && !query.getCreatedBy().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), query.getCreatedBy()));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
