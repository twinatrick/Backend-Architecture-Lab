package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.MealGoogleQuotaUsage;
import com.example.BackendArchitectureLab.Entity.MealRestaurantLocation;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Repository.MealGoogleQuotaUsageRepository;
import com.example.BackendArchitectureLab.Repository.MealRestaurantLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MealRestaurantLocationDataAccess {
    private final MealRestaurantLocationRepository locationRepository;
    private final MealGoogleQuotaUsageRepository quotaRepository;

    public MealRestaurantLocation owned(UUID userId, UUID locationId) {
        return locationRepository.findByIdAndOwnerUserId(locationId, userId)
                .orElseThrow(() -> new AppException("MEAL_LOCATION_NOT_FOUND", "店家據點不存在或不屬於目前使用者", 404));
    }

    public Optional<MealRestaurantLocation> findOwned(UUID userId, UUID locationId) {
        return locationRepository.findByIdAndOwnerUserId(locationId, userId);
    }

    public Optional<MealRestaurantLocation> findByGooglePlaceId(UUID userId, String googlePlaceId) {
        return locationRepository.findByOwnerUserIdAndGooglePlaceId(userId, googlePlaceId);
    }

    public List<MealRestaurantLocation> findByNormalizedName(UUID userId, String normalizedName) {
        return locationRepository.findByOwnerUserIdAndNormalizedName(userId, normalizedName);
    }

    public Page<MealRestaurantLocation> listOwned(UUID userId, Pageable pageable) {
        return locationRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId, pageable);
    }

    public List<MealRestaurantLocation> findDueForCheck(Date now) {
        return locationRepository.findTop20ByBusinessStatusNotInAndNextCheckDueAtLessThanEqualOrderByNextCheckDueAtAsc(
                List.of("PERMANENTLY_CLOSED", "MOVED"), now);
    }

    public List<MealRestaurantLocation> findPendingUserConfirmations(UUID userId) {
        return locationRepository.findByOwnerUserIdAndPendingUserConfirmationTrue(userId);
    }

    public MealRestaurantLocation saveLocation(MealRestaurantLocation location) {
        return locationRepository.saveAndFlush(location);
    }

    public Optional<MealGoogleQuotaUsage> lockQuota(String quotaMonth) {
        return quotaRepository.lockByMonth(quotaMonth);
    }

    public Optional<MealGoogleQuotaUsage> findQuota(String quotaMonth) {
        return quotaRepository.findById(quotaMonth);
    }

    public MealGoogleQuotaUsage saveQuota(MealGoogleQuotaUsage quota) {
        return quotaRepository.saveAndFlush(quota);
    }
}
