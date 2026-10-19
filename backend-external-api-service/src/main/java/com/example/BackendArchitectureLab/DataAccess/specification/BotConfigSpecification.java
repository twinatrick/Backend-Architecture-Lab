package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Dto.Query.BotConfigSearchQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * BotConfig 條件查詢規格建構器。
 */
public class BotConfigSpecification {

    public static Specification<BotConfig> buildSpecification(BotConfigSearchQuery query) {
        if (query == null) {
            return null;
        }
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
                String pattern = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("platform")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("configKey")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("configValue")), pattern)
                ));
            }

            if (query.getPlatform() != null && !query.getPlatform().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("platform")),
                        "%" + query.getPlatform().trim().toLowerCase() + "%"
                ));
            }

            if (query.getConfigKey() != null && !query.getConfigKey().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("configKey")),
                        "%" + query.getConfigKey().trim().toLowerCase() + "%"
                ));
            }

            if (query.getDescription() != null && !query.getDescription().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("description")),
                        "%" + query.getDescription().trim().toLowerCase() + "%"
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
