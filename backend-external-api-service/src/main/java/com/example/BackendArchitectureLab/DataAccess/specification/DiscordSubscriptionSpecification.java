package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import com.example.BackendArchitectureLab.Vo.Search.DiscordSubscriptionSearchQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * DiscordSubscription 條件查詢規格建構器。
 */
public class DiscordSubscriptionSpecification {

    public static Specification<DiscordSubscription> buildSpecification(DiscordSubscriptionSearchQuery query) {
        if (query == null) {
            return null;
        }
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
                String pattern = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("guildId")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("channelId")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("botType")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("webhookUrl")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("webhookId")), pattern)
                ));
            }

            if (query.getGuildId() != null && !query.getGuildId().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("guildId")),
                        "%" + query.getGuildId().trim().toLowerCase() + "%"
                ));
            }

            if (query.getChannelId() != null && !query.getChannelId().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("channelId"),
                        query.getChannelId().trim()
                ));
            }

            if (query.getBotType() != null && !query.getBotType().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("botType")),
                        query.getBotType().trim().toUpperCase()
                ));
            }

            if (query.getLanguage() != null && !query.getLanguage().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("language")),
                        query.getLanguage().trim().toLowerCase()
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
