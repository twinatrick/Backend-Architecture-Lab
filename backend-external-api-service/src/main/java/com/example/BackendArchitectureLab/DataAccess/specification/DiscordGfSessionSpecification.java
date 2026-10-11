package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Vo.Search.DiscordGfSessionSearchQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * DiscordGfSession 條件查詢規格建構器。
 */
public class DiscordGfSessionSpecification {

    public static Specification<DiscordGfSession> buildSpecification(DiscordGfSessionSearchQuery query) {
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
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("userId")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("prompt")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("gfName")), pattern)
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

            if (query.getUserId() != null && !query.getUserId().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("userId"),
                        query.getUserId().trim()
                ));
            }

            if (query.getActive() != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), query.getActive()));
            }

            if (query.getGfName() != null && !query.getGfName().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("gfName")),
                        "%" + query.getGfName().trim().toLowerCase() + "%"
                ));
            }

            if (query.getLanguage() != null && !query.getLanguage().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("language")),
                        query.getLanguage().trim().toLowerCase()
                ));
            }

            if (query.getVoiceEnabled() != null) {
                predicates.add(criteriaBuilder.equal(root.get("voiceEnabled"), query.getVoiceEnabled()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
