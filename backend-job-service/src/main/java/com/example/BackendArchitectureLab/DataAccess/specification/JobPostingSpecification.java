package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.JobPostingSearchQuery;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class JobPostingSpecification {

    public static Specification<JobPosting> buildSpecification(JobPostingSearchQuery query) {
        if (query == null) return null;
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.getTitle() != null && !query.getTitle().trim().isEmpty())
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), "%" + query.getTitle().toLowerCase() + "%"));
            if (query.getCompanyId() != null && !query.getCompanyId().trim().isEmpty())
                predicates.add(criteriaBuilder.equal(root.get("company").get("id"), java.util.UUID.fromString(query.getCompanyId())));
            if (query.getCompanyName() != null && !query.getCompanyName().trim().isEmpty())
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("company").get("name")), "%" + query.getCompanyName().toLowerCase() + "%"));
            if (query.getSalaryRange() != null && !query.getSalaryRange().trim().isEmpty())
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("salaryRange")), "%" + query.getSalaryRange().toLowerCase() + "%"));
            if (query.getPostedDateStart() != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("postedDate"), query.getPostedDateStart()));
            if (query.getPostedDateEnd() != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("postedDate"), query.getPostedDateEnd()));
            if (query.getCreatedBy() != null && !query.getCreatedBy().trim().isEmpty())
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), query.getCreatedBy()));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
