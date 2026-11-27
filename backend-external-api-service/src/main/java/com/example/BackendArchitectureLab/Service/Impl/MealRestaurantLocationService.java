package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.MealProperties;
import com.example.BackendArchitectureLab.DataAccess.MealLineDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealRestaurantLocationDataAccess;
import com.example.BackendArchitectureLab.Dto.Request.MealDetailsRequest;
import com.example.BackendArchitectureLab.Dto.Request.ResolveRestaurantLocationRequest;
import com.example.BackendArchitectureLab.Entity.MealGoogleQuotaUsage;
import com.example.BackendArchitectureLab.Entity.MealLineBinding;
import com.example.BackendArchitectureLab.Entity.MealLineNotificationOutbox;
import com.example.BackendArchitectureLab.Entity.MealRestaurantLocation;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.MealRestaurantLocationMapper;
import com.example.BackendArchitectureLab.Service.IMealRestaurantLocationService;
import com.example.BackendArchitectureLab.Service.MealGooglePlacesClient;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.MealRestaurantLocationVo;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealRestaurantLocationService implements IMealRestaurantLocationService {
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double SAME_RESTAURANT_DISTANCE_METERS = 3.0;

    private final MealRestaurantLocationDataAccess locations;
    private final MealLineDataAccess lineDataAccess;
    private final MealRestaurantLocationMapper mapper;
    private final MealProperties properties;
    private final MealGooglePlacesClient googlePlacesClient;
    private final TransactionExecutor transactions;
    private final Validator validator;

    @Override
    public MealRestaurantLocationVo resolveOrUpdateLocation(UUID userId, ResolveRestaurantLocationRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new AppException("INVALID_LOCATION_REQUEST", "店家據點輸入資料不合法", 400);
        }
        return transactions.executeWritable(() -> {
            Date now = new Date();
            if (request.getLocationId() != null) {
                MealRestaurantLocation existing = locations.owned(userId, request.getLocationId());
                if ("NEW_BUSINESS".equalsIgnoreCase(request.getResolutionAction())) {
                    MealRestaurantLocation created = buildNewLocation(
                            userId,
                            request.getDisplayName(),
                            request.getFloorText(),
                            request.getLocationDescription(),
                            request.getGoogleMapsUrl(),
                            extractPlaceId(request.getGooglePlaceId(), request.getGoogleMapsUrl()),
                            request.getLatitude(),
                            request.getLongitude(),
                            request.getBusinessStatus() != null ? normalizeStatus(request.getBusinessStatus()) : "OPEN",
                            now);
                    return mapper.toVo(locations.saveLocation(created));
                }
                existing.setDisplayName(request.getDisplayName().trim());
                existing.setNormalizedName(normalizeName(request.getDisplayName()));
                if (request.getFloorText() != null) {
                    existing.setFloorText(request.getFloorText().trim());
                    existing.setNormalizedFloor(normalizeFloor(request.getFloorText()));
                }
                if (request.getLocationDescription() != null) {
                    existing.setLocationDescription(request.getLocationDescription().trim());
                }
                if (request.getLatitude() != null && request.getLongitude() != null) {
                    existing.setLatitude(request.getLatitude());
                    existing.setLongitude(request.getLongitude());
                }
                if ("REOPENED".equalsIgnoreCase(request.getResolutionAction())) {
                    existing.setBusinessStatus("OPEN");
                } else if (request.getBusinessStatus() != null && !request.getBusinessStatus().isBlank()) {
                    existing.setBusinessStatus(normalizeStatus(request.getBusinessStatus()));
                }
                existing.setPendingUserConfirmation(false);
                existing.setLastCheckedAt(now);
                existing.setNextCheckDueAt(nextQuarterDueDate(now));
                return mapper.toVo(locations.saveLocation(existing));
            }

            MealDetailsRequest mealReq = new MealDetailsRequest();
            mealReq.setRestaurantName(request.getDisplayName());
            mealReq.setFloor(request.getFloorText());
            mealReq.setLocationDescription(request.getLocationDescription());
            mealReq.setGoogleMapsUrl(request.getGoogleMapsUrl());
            mealReq.setGooglePlaceId(request.getGooglePlaceId());
            mealReq.setLatitude(request.getLatitude());
            mealReq.setLongitude(request.getLongitude());
            mealReq.setConfirmLocationAction(request.getResolutionAction());
            UUID resolvedId = resolveLocationInternal(userId, mealReq, now);
            return mapper.toVo(locations.owned(userId, resolvedId));
        });
    }

    @Override
    public UUID resolveLocationForMeal(UUID userId, MealDetailsRequest request) {
        return transactions.executeWritable(() -> resolveLocationInternal(userId, request, new Date()));
    }

    private UUID resolveLocationInternal(UUID userId, MealDetailsRequest request, Date now) {
        if (request.getRestaurantLocationId() != null) {
            MealRestaurantLocation explicit = locations.owned(userId, request.getRestaurantLocationId());
            handleClosedOrMovedLocation(userId, explicit, request, now);
            return explicit.getId();
        }

        String placeId = extractPlaceId(request.getGooglePlaceId(), request.getGoogleMapsUrl());
        if (placeId != null) {
            Optional<MealRestaurantLocation> byPlaceId = locations.findByGooglePlaceId(userId, placeId);
            if (byPlaceId.isPresent()) {
                MealRestaurantLocation existing = byPlaceId.get();
                if ("NEW_BUSINESS".equalsIgnoreCase(request.getConfirmLocationAction())) {
                    MealRestaurantLocation replacement = buildNewLocation(
                            userId,
                            defaultDisplayName(request.getRestaurantName(), placeId),
                            request.getFloor(),
                            request.getLocationDescription(),
                            request.getGoogleMapsUrl(),
                            null,
                            request.getLatitude(),
                            request.getLongitude(),
                            "OPEN",
                            now);
                    return locations.saveLocation(replacement).getId();
                }
                handleClosedOrMovedLocation(userId, existing, request, now);
                return existing.getId();
            }
            String displayName = defaultDisplayName(request.getRestaurantName(), placeId);
            MealRestaurantLocation created = buildNewLocation(
                    userId,
                    displayName,
                    request.getFloor(),
                    request.getLocationDescription(),
                    request.getGoogleMapsUrl(),
                    placeId,
                    request.getLatitude(),
                    request.getLongitude(),
                    "OPEN",
                    now);
            return locations.saveLocation(created).getId();
        }

        if ((request.getRestaurantName() == null || request.getRestaurantName().isBlank())
                && request.getLatitude() == null && request.getLongitude() == null
                && (request.getGoogleMapsUrl() == null || request.getGoogleMapsUrl().isBlank())) {
            return null;
        }

        if (request.getRestaurantName() == null || request.getRestaurantName().isBlank()) {
            if (request.getConfirmLocationAction() == null || request.getConfirmLocationAction().isBlank()) {
                throw new AppException("RESTAURANT_LOCATION_CONFIRMATION_REQUIRED",
                        "未提供店名時無法自動合併手動位置，請提供店名或確認以未識別位置建立", 409);
            }
            return null;
        }

        String normName = normalizeName(request.getRestaurantName());
        String normFloor = normalizeFloor(request.getFloor());
        List<MealRestaurantLocation> sameNameCandidates = locations.findByNormalizedName(userId, normName)
                .stream()
                .filter(loc -> loc.getGooglePlaceId() == null)
                .toList();

        if (request.getLatitude() != null && request.getLongitude() != null) {
            double maxMeters = SAME_RESTAURANT_DISTANCE_METERS;
            List<MealRestaurantLocation> nearbySameName = sameNameCandidates.stream()
                    .filter(loc -> loc.getLatitude() != null && loc.getLongitude() != null)
                    .filter(loc -> distanceMeters(request.getLatitude(), request.getLongitude(),
                            loc.getLatitude(), loc.getLongitude()) <= maxMeters)
                    .toList();

            if (!nearbySameName.isEmpty() && normFloor.isEmpty()
                    && (request.getConfirmLocationAction() == null || request.getConfirmLocationAction().isBlank())) {
                throw new AppException("RESTAURANT_LOCATION_CONFIRMATION_REQUIRED",
                        "3 公尺內已有同名店家據點，請填寫樓層或明確指定據點", 409);
            }

            List<MealRestaurantLocation> exactMatches = nearbySameName.stream()
                    .filter(loc -> Objects.equals(loc.getNormalizedFloor(), normFloor))
                    .toList();

            if ("CREATE_NEW".equalsIgnoreCase(request.getConfirmLocationAction())
                    || "NEW_BUSINESS".equalsIgnoreCase(request.getConfirmLocationAction())) {
                MealRestaurantLocation created = buildNewLocation(
                        userId,
                        request.getRestaurantName(),
                        request.getFloor(),
                        request.getLocationDescription(),
                        request.getGoogleMapsUrl(),
                        null,
                        request.getLatitude(),
                        request.getLongitude(),
                        "UNKNOWN",
                        now);
                return locations.saveLocation(created).getId();
            }

            if (exactMatches.size() > 1) {
                throw new AppException("RESTAURANT_LOCATION_CONFIRMATION_REQUIRED",
                        "3 公尺內同樓層存在多個同名店家據點，請明確指定 restaurantLocationId", 409);
            }
            if (exactMatches.size() == 1) {
                MealRestaurantLocation matched = exactMatches.get(0);
                handleClosedOrMovedLocation(userId, matched, request, now);
                return matched.getId();
            }
        }

        MealRestaurantLocation created = buildNewLocation(
                userId,
                request.getRestaurantName(),
                request.getFloor(),
                request.getLocationDescription(),
                request.getGoogleMapsUrl(),
                null,
                request.getLatitude(),
                request.getLongitude(),
                "UNKNOWN",
                now);
        return locations.saveLocation(created).getId();
    }

    private void handleClosedOrMovedLocation(
            UUID userId,
            MealRestaurantLocation location,
            MealDetailsRequest request,
            Date now) {
        String status = location.getBusinessStatus();
        if (!"PERMANENTLY_CLOSED".equals(status) && !"MOVED".equals(status)) {
            return;
        }
        String action = request.getConfirmLocationAction();
        if (action == null || action.isBlank()) {
            String message = "MOVED".equals(status)
                    ? "此店家據點已搬遷（新據點 ID: " + location.getMovedToLocationId() + "），請確認為補登舊紀錄（BACKFILL）或改選新據點"
                    : "此店家據點已標記歇業，請確認為補登舊紀錄（BACKFILL）、恢復營業（REOPENED）或原址新店（NEW_BUSINESS）";
            throw new AppException("RESTAURANT_LOCATION_CONFIRMATION_REQUIRED", message, 409);
        }
        if ("REOPENED".equalsIgnoreCase(action)) {
            location.setBusinessStatus("OPEN");
            location.setPendingUserConfirmation(false);
            location.setLastCheckedAt(now);
            location.setNextCheckDueAt(nextQuarterDueDate(now));
            locations.saveLocation(location);
        } else if (!"BACKFILL".equalsIgnoreCase(action) && !"USE_EXISTING".equalsIgnoreCase(action)) {
            throw new AppException("INVALID_LOCATION_CONFIRMATION", "不支援的據點確認動作: " + action, 400);
        }
    }

    @Override
    public PageResult<MealRestaurantLocationVo> listLocations(UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return transactions.executeReadOnly(() -> {
            Page<MealRestaurantLocation> locationPage = locations.listOwned(userId, PageRequest.of(safePage, safeSize));
            return PageResult.of(locationPage, locationPage.getContent().stream().map(mapper::toVo).toList());
        });
    }

    @Override
    public MealRestaurantLocationVo checkLocationStatusNow(UUID userId, UUID locationId) {
        return transactions.executeWritable(() -> {
            MealRestaurantLocation location = locations.owned(userId, locationId);
            checkSingleLocation(location, new Date(), true);
            return mapper.toVo(locations.saveLocation(location));
        });
    }

    @Override
    public int runQuarterlyStatusChecks(Date now) {
        Date effectiveNow = now != null ? now : new Date();
        return transactions.executeWritable(() -> {
            List<MealRestaurantLocation> dueList = locations.findDueForCheck(effectiveNow);
            int processed = 0;
            for (MealRestaurantLocation location : dueList) {
                if (checkSingleLocation(location, effectiveNow, false)) {
                    processed++;
                }
                locations.saveLocation(location);
            }
            return processed;
        });
    }

    @Override
    public int getMonthlyGoogleQuotaUsed(String quotaMonth) {
        return transactions.executeReadOnly(() -> locations.findQuota(quotaMonth)
                .map(MealGoogleQuotaUsage::getRequestCount)
                .orElse(0));
    }

    private boolean checkSingleLocation(MealRestaurantLocation location, Date now, boolean manualTrigger) {
        String cycleKey = currentQuarterCycleKey(now);
        if (location.getGooglePlaceId() == null || location.getGooglePlaceId().isBlank()) {
            location.setPendingUserConfirmation(true);
            location.setLastCheckedAt(now);
            location.setNextCheckDueAt(nextQuarterDueDate(now));
            if (!Objects.equals(cycleKey, location.getLastNotifiedCycle())) {
                enqueueDailySummaryNotification(
                        location.getOwnerUserId(),
                        "location-manual-confirm:" + location.getId() + ":" + cycleKey,
                        "【每季店況待確認】手動據點「" + location.getDisplayName() + "」已達每季確認週期，請確認是否仍正常營業。",
                        now);
                location.setLastNotifiedCycle(cycleKey);
            }
            return true;
        }

        if (!properties.isGoogleEnabled() || !properties.isGoogleAuthorizedBackgroundTracking()) {
            if (manualTrigger) {
                throw new AppException("GOOGLE_PLACES_NOT_AUTHORIZED",
                        "Google Places 自動背景查詢尚未啟用或未確認政策授權，目前維持原有店家狀態", 503);
            }
            return false;
        }

        if (!tryConsumeMonthlyQuota(now)) {
            log.warn("Google Places monthly hard cap ({}) reached; pausing status check for locationId={}",
                    properties.getGoogleMonthlyRequestCap(), location.getId());
            if (manualTrigger) {
                throw new AppException("GOOGLE_QUOTA_CAP_REACHED",
                        "本月 Google API 免費保守額度已達上限，已自動暫停查詢並保留原狀態", 429);
            }
            return false;
        }

        Optional<MealGooglePlacesClient.GooglePlaceStatusResult> resultOpt =
                googlePlacesClient.fetchPlaceStatus(location.getGooglePlaceId());
        if (resultOpt.isEmpty()) {
            // 外部查詢失敗時保留原狀態，不誤判歇業
            return false;
        }

        MealGooglePlacesClient.GooglePlaceStatusResult result = resultOpt.get();
        String previousStatus = location.getBusinessStatus();
        location.setLastCheckedAt(now);

        if (result.movedPlaceId() != null && !result.movedPlaceId().isBlank()
                && !Objects.equals(result.movedPlaceId(), location.getGooglePlaceId())) {
            MealRestaurantLocation newLocation = locations.findByGooglePlaceId(location.getOwnerUserId(), result.movedPlaceId())
                    .orElseGet(() -> {
                        MealRestaurantLocation created = buildNewLocation(
                                location.getOwnerUserId(),
                                location.getDisplayName() + "（搬遷新址）",
                                null,
                                "由舊據點搬遷建立",
                                null,
                                result.movedPlaceId(),
                                null,
                                null,
                                "OPEN",
                                now);
                        created.setMovedFromLocationId(location.getId());
                        return locations.saveLocation(created);
                    });
            newLocation.setMovedFromLocationId(location.getId());
            locations.saveLocation(newLocation);

            location.setBusinessStatus("MOVED");
            location.setMovedToLocationId(newLocation.getId());
            location.setNextCheckDueAt(null);
            if (!Objects.equals(previousStatus, "MOVED") && !Objects.equals(cycleKey, location.getLastNotifiedCycle())) {
                enqueueDailySummaryNotification(
                        location.getOwnerUserId(),
                        "location-status-change:" + location.getId() + ":" + cycleKey,
                        "【店況異動通知】店家「" + location.getDisplayName() + "」已搬遷至新據點（歷史餐點紀錄仍保留原快照）。",
                        now);
                location.setLastNotifiedCycle(cycleKey);
            }
            return true;
        }

        String mappedStatus = mapGoogleBusinessStatus(result.businessStatus(), previousStatus);
        location.setBusinessStatus(mappedStatus);
        if ("PERMANENTLY_CLOSED".equals(mappedStatus)) {
            location.setNextCheckDueAt(null);
        } else {
            location.setNextCheckDueAt(nextQuarterDueDate(now));
        }

        if (!Objects.equals(previousStatus, mappedStatus) && !Objects.equals(cycleKey, location.getLastNotifiedCycle())) {
            enqueueDailySummaryNotification(
                    location.getOwnerUserId(),
                    "location-status-change:" + location.getId() + ":" + cycleKey,
                    "【店況異動通知】店家「" + location.getDisplayName() + "」營業狀態更新為：" + mappedStatus,
                    now);
            location.setLastNotifiedCycle(cycleKey);
        }
        return true;
    }

    private boolean tryConsumeMonthlyQuota(Date now) {
        String monthKey = YearMonth.from(Instant.ofEpochMilli(now.getTime()).atZone(TAIPEI)).toString();
        int cap = Math.max(0, properties.getGoogleMonthlyRequestCap());
        MealGoogleQuotaUsage quota = locations.lockQuota(monthKey).orElseGet(() -> {
            MealGoogleQuotaUsage created = new MealGoogleQuotaUsage();
            created.setQuotaMonth(monthKey);
            created.setRequestCount(0);
            created.setUpdatedAt(now);
            return locations.saveQuota(created);
        });
        if (quota.getRequestCount() >= cap) {
            return false;
        }
        quota.setRequestCount(quota.getRequestCount() + 1);
        quota.setUpdatedAt(now);
        locations.saveQuota(quota);
        return true;
    }

    private void enqueueDailySummaryNotification(UUID userId, String dedupKey, String text, Date now) {
        Optional<MealLineBinding> bindingOpt = lineDataAccess.findBindingByUser(userId);
        if (bindingOpt.isEmpty() || !bindingOpt.get().isNotificationEnabled()) {
            return;
        }
        if (lineDataAccess.findOutboxByDedupKey(dedupKey).isPresent()) {
            return;
        }
        MealLineNotificationOutbox outbox = new MealLineNotificationOutbox();
        outbox.setOwnerUserId(userId);
        outbox.setLineUserId(bindingOpt.get().getLineUserId());
        outbox.setDedupKey(dedupKey);
        outbox.setMessageText(text);
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(nextDailySummaryTime(now));
        lineDataAccess.saveOutbox(outbox);
    }

    private MealRestaurantLocation buildNewLocation(
            UUID userId,
            String displayName,
            String floorText,
            String locationDescription,
            String googleMapsUrl,
            String googlePlaceId,
            BigDecimal latitude,
            BigDecimal longitude,
            String businessStatus,
            Date now) {
        MealRestaurantLocation loc = new MealRestaurantLocation();
        loc.setOwnerUserId(userId);
        loc.setDisplayName(displayName.trim());
        loc.setNormalizedName(normalizeName(displayName));
        loc.setFloorText(floorText != null && !floorText.isBlank() ? floorText.trim() : null);
        loc.setNormalizedFloor(normalizeFloor(floorText));
        loc.setLocationDescription(locationDescription != null && !locationDescription.isBlank() ? locationDescription.trim() : null);
        loc.setGoogleMapsUrl(googleMapsUrl != null && !googleMapsUrl.isBlank() ? googleMapsUrl.trim() : null);
        loc.setGooglePlaceId(googlePlaceId);
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);
        loc.setBusinessStatus(businessStatus);
        loc.setPendingUserConfirmation(false);
        loc.setLastCheckedAt(now);
        loc.setNextCheckDueAt("PERMANENTLY_CLOSED".equals(businessStatus) || "MOVED".equals(businessStatus)
                ? null
                : nextQuarterDueDate(now));
        return loc;
    }

    public static String extractPlaceId(String explicitPlaceId, String googleMapsUrl) {
        if (explicitPlaceId != null && !explicitPlaceId.isBlank()) {
            return explicitPlaceId.trim();
        }
        if (googleMapsUrl == null || googleMapsUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(googleMapsUrl.trim());
            String query = uri.getRawQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
                        String val = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                        if ("query_place_id".equalsIgnoreCase(key) || "place_id".equalsIgnoreCase(key)) {
                            if (!val.isBlank()) return val.trim();
                        }
                        if (val.startsWith("place_id:")) {
                            String extracted = val.substring("place_id:".length()).trim();
                            if (!extracted.isEmpty()) return extracted;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 非標準 URI 時退回字串檢查
        }
        int idx = googleMapsUrl.indexOf("place_id:");
        if (idx >= 0) {
            String tail = googleMapsUrl.substring(idx + "place_id:".length());
            int amp = tail.indexOf('&');
            String candidate = (amp >= 0 ? tail.substring(0, amp) : tail).trim();
            if (!candidate.isEmpty()) return candidate;
        }
        return null;
    }

    public static double distanceMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeFloor(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String defaultDisplayName(String restaurantName, String fallbackPlaceId) {
        return (restaurantName != null && !restaurantName.isBlank()) ? restaurantName.trim() : "Google店家(" + fallbackPlaceId + ")";
    }

    private static String normalizeStatus(String status) {
        String upper = status.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "OPEN", "TEMPORARILY_CLOSED", "PERMANENTLY_CLOSED", "MOVED", "UNKNOWN" -> upper;
            default -> throw new AppException("INVALID_BUSINESS_STATUS", "不支援的營業狀態: " + status, 400);
        };
    }

    private static String mapGoogleBusinessStatus(String googleStatus, String fallback) {
        if (googleStatus == null || googleStatus.isBlank()) {
            return fallback;
        }
        return switch (googleStatus.trim().toUpperCase(Locale.ROOT)) {
            case "OPERATIONAL" -> "OPEN";
            case "CLOSED_TEMPORARILY" -> "TEMPORARILY_CLOSED";
            case "CLOSED_PERMANENTLY" -> "PERMANENTLY_CLOSED";
            default -> fallback;
        };
    }

    private static String currentQuarterCycleKey(Date now) {
        ZonedDateTime zdt = Instant.ofEpochMilli(now.getTime()).atZone(TAIPEI);
        int quarter = ((zdt.getMonthValue() - 1) / 3) + 1;
        return zdt.getYear() + "-Q" + quarter;
    }

    private static Date nextQuarterDueDate(Date now) {
        ZonedDateTime zdt = Instant.ofEpochMilli(now.getTime()).atZone(TAIPEI);
        int currentQuarterFirstMonth = ((zdt.getMonthValue() - 1) / 3) * 3 + 1;
        ZonedDateTime currentQuarterStart = ZonedDateTime.of(zdt.getYear(), currentQuarterFirstMonth, 1, 0, 0, 0, 0, TAIPEI);
        ZonedDateTime nextQuarterStart = currentQuarterStart.plusMonths(3);
        return Date.from(nextQuarterStart.toInstant());
    }

    private static Date nextDailySummaryTime(Date now) {
        ZonedDateTime zdt = Instant.ofEpochMilli(now.getTime()).atZone(TAIPEI);
        ZonedDateTime today19 = zdt.withHour(19).withMinute(0).withSecond(0).withNano(0);
        if (zdt.isAfter(today19)) {
            return Date.from(today19.plusDays(1).toInstant());
        }
        return Date.from(today19.toInstant());
    }
}
