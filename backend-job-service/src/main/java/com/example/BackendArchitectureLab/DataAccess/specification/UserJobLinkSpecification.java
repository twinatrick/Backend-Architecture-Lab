package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Vo.Search.UserJobLinkSearchQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserJobLinkSpecification {

    private UserJobLinkSpecification() {
    }

    public static Specification<UserJobLink> buildSpecification(UserJobLinkSearchQuery query) {
        if (query == null) {
            return null;
        }
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.getJobTitle() != null && !query.getJobTitle().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("jobPosting").get("title")),
                        "%" + query.getJobTitle().trim().toLowerCase() + "%"
                ));
            }
            if (query.getCompanyName() != null && !query.getCompanyName().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("jobPosting").get("company").get("name")),
                        "%" + query.getCompanyName().trim().toLowerCase() + "%"
                ));
            }
            if (query.getUserId() != null && !query.getUserId().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("userId"),
                        UUID.fromString(query.getUserId().trim())
                ));
            }
            if (query.getJobPostingId() != null && !query.getJobPostingId().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("jobPosting").get("id"),
                        UUID.fromString(query.getJobPostingId().trim())
                ));
            }
            if (query.getCreatedBy() != null && !query.getCreatedBy().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("createdBy"),
                        query.getCreatedBy().trim()
                ));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
