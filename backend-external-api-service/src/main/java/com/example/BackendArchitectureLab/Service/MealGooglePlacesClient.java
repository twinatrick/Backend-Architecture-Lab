package com.example.BackendArchitectureLab.Service;

import java.util.Optional;

public interface MealGooglePlacesClient {
    record GooglePlaceStatusResult(String placeId, String businessStatus, String movedPlaceId) {
    }

    Optional<GooglePlaceStatusResult> fetchPlaceStatus(String googlePlaceId);
}
