package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Query.MealSearchQuery;
import com.example.BackendArchitectureLab.Entity.MealPhoto;
import com.example.BackendArchitectureLab.Entity.MealRecord;
import com.example.BackendArchitectureLab.Entity.MealRestaurantLocation;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MealSpecification {
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private MealSpecification() { }

    public static Specification<MealRecord> owned(UUID userId, MealSearchQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> filters = new ArrayList<>();
            filters.add(builder.equal(root.get("userId"), userId));
            filters.add(builder.isNull(root.get("deletedAt")));
            if (query.getMealName() != null && !query.getMealName().isBlank()) {
                filters.add(builder.like(builder.lower(root.get("mealName")), pattern(query.getMealName()), '\\'));
            }
            if (query.getRestaurantName() != null && !query.getRestaurantName().isBlank()) {
                filters.add(builder.like(builder.lower(root.get("restaurantName")), pattern(query.getRestaurantName()), '\\'));
            }
            if (query.getRestaurantLocationId() != null) {
                filters.add(builder.equal(root.get("restaurantLocationId"), query.getRestaurantLocationId()));
            }
            if (query.getBusinessStatus() != null && !query.getBusinessStatus().isBlank()) {
                String normalizedStatus = query.getBusinessStatus().trim().toUpperCase(Locale.ROOT);
                if ("UNKNOWN".equals(normalizedStatus)) {
                    Subquery<UUID> locQuery = criteria.subquery(UUID.class);
                    Root<MealRestaurantLocation> loc = locQuery.from(MealRestaurantLocation.class);
                    locQuery.select(loc.get("id")).where(
                            builder.equal(loc.get("id"), root.get("restaurantLocationId")),
                            builder.equal(loc.get("businessStatus"), "UNKNOWN"));
                    filters.add(builder.or(builder.isNull(root.get("restaurantLocationId")), builder.exists(locQuery)));
                } else {
                    Subquery<UUID> locQuery = criteria.subquery(UUID.class);
                    Root<MealRestaurantLocation> loc = locQuery.from(MealRestaurantLocation.class);
                    locQuery.select(loc.get("id")).where(
                            builder.equal(loc.get("id"), root.get("restaurantLocationId")),
                            builder.equal(loc.get("businessStatus"), normalizedStatus));
                    filters.add(builder.exists(locQuery));
                }
            }
            if (query.getDateFrom() != null) {
                filters.add(builder.greaterThanOrEqualTo(root.get("eatenAt"), Date.from(query.getDateFrom().atStartOfDay(TAIPEI).toInstant())));
            }
            if (query.getDateTo() != null) {
                filters.add(builder.lessThan(root.get("eatenAt"), Date.from(query.getDateTo().plusDays(1).atStartOfDay(TAIPEI).toInstant())));
            }
            if (query.getAmountMin() != null) filters.add(builder.greaterThanOrEqualTo(root.get("amount"), query.getAmountMin()));
            if (query.getAmountMax() != null) filters.add(builder.lessThanOrEqualTo(root.get("amount"), query.getAmountMax()));
            if (Boolean.TRUE.equals(query.getUnrated())) filters.add(builder.isNull(root.get("rating")));
            if (query.getRatingMin() != null) filters.add(builder.greaterThanOrEqualTo(root.get("rating"), query.getRatingMin()));
            if (query.getRatingMax() != null) filters.add(builder.lessThanOrEqualTo(root.get("rating"), query.getRatingMax()));
            if (query.getHasPhotos() != null) {
                Subquery<UUID> photoQuery = criteria.subquery(UUID.class);
                Root<MealPhoto> photo = photoQuery.from(MealPhoto.class);
                photoQuery.select(photo.get("id")).where(builder.equal(photo.get("mealId"), root.get("id")), builder.equal(photo.get("state"), "READY"));
                Predicate exists = builder.exists(photoQuery);
                filters.add(query.getHasPhotos() ? exists : builder.not(exists));
            }
            return builder.and(filters.toArray(Predicate[]::new));
        };
    }

    private static String pattern(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
