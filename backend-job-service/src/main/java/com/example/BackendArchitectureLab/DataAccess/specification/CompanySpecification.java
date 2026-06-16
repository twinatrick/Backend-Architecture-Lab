package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.CompanySearchQuery;
import com.example.BackendArchitectureLab.Entity.Company;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class CompanySpecification {

    public static Specification<Company> buildSpecification(CompanySearchQuery query) {
        if (query == null) return null;
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
                String pattern = "%" + query.getKeyword().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)
                ));
            }

            if (query.getName() != null && !query.getName().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + query.getName().toLowerCase() + "%"
                ));
            }

            if (query.getDescription() != null && !query.getDescription().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("description")),
                    "%" + query.getDescription().toLowerCase() + "%"
                ));
            }

            if (query.getCreatedBy() != null && !query.getCreatedBy().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), query.getCreatedBy()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
