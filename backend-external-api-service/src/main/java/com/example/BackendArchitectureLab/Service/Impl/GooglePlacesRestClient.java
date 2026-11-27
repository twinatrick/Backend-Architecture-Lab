package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.MealProperties;
import com.example.BackendArchitectureLab.Service.MealGooglePlacesClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GooglePlacesRestClient implements MealGooglePlacesClient {
    private final MealProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<GooglePlaceStatusResult> fetchPlaceStatus(String googlePlaceId) {
        if (!properties.isGoogleEnabled()
                || !properties.isGoogleAuthorizedBackgroundTracking()
                || properties.getGoogleApiKey() == null
                || properties.getGoogleApiKey().isBlank()) {
            return Optional.empty();
        }
        try {
            String encodedId = URLEncoder.encode(googlePlaceId, StandardCharsets.UTF_8);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://places.googleapis.com/v1/places/" + encodedId))
                    .timeout(Duration.ofSeconds(8))
                    .header("X-Goog-Api-Key", properties.getGoogleApiKey())
                    .header("X-Goog-FieldMask", "id,businessStatus,movedPlace,movedPlaceId")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Google Places API returned non-2xx status {} for placeId={}", response.statusCode(), googlePlaceId);
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            String id = root.path("id").asText(googlePlaceId);
            String rawStatus = root.path("businessStatus").asText("");
            String movedPlaceId = root.path("movedPlaceId").asText("");
            if (movedPlaceId.isBlank() && root.has("movedPlace")) {
                String resource = root.path("movedPlace").asText("");
                if (resource.startsWith("places/")) {
                    movedPlaceId = resource.substring("places/".length());
                }
            }
            return Optional.of(new GooglePlaceStatusResult(id, rawStatus, movedPlaceId.isBlank() ? null : movedPlaceId));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Google Places API interrupted for placeId={}: {}", googlePlaceId, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Google Places API failed for placeId={}: {}", googlePlaceId, ex.getMessage());
            return Optional.empty();
        }
    }
}
