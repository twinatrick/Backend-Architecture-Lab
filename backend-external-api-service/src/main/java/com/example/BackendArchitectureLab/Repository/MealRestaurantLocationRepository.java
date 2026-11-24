package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealRestaurantLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealRestaurantLocationRepository extends JpaRepository<MealRestaurantLocation, UUID> {
    Optional<MealRestaurantLocation> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    Optional<MealRestaurantLocation> findByOwnerUserIdAndGooglePlaceId(UUID ownerUserId, String googlePlaceId);
    List<MealRestaurantLocation> findByOwnerUserIdAndNormalizedName(UUID ownerUserId, String normalizedName);
    Page<MealRestaurantLocation> findByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId, Pageable pageable);
    List<MealRestaurantLocation> findTop20ByBusinessStatusNotInAndNextCheckDueAtLessThanEqualOrderByNextCheckDueAtAsc(
            List<String> excludedStatuses, Date now);
    List<MealRestaurantLocation> findByOwnerUserIdAndPendingUserConfirmationTrue(UUID ownerUserId);
}
