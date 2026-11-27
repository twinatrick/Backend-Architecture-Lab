package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Request.MealDetailsRequest;
import com.example.BackendArchitectureLab.Dto.Request.ResolveRestaurantLocationRequest;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.MealRestaurantLocationVo;

import java.util.Date;
import java.util.UUID;

public interface IMealRestaurantLocationService {
    MealRestaurantLocationVo resolveOrUpdateLocation(UUID userId, ResolveRestaurantLocationRequest request);
    UUID resolveLocationForMeal(UUID userId, MealDetailsRequest request);
    PageResult<MealRestaurantLocationVo> listLocations(UUID userId, int page, int size);
    MealRestaurantLocationVo checkLocationStatusNow(UUID userId, UUID locationId);
    int runQuarterlyStatusChecks(Date now);
    int getMonthlyGoogleQuotaUsed(String quotaMonth);
}
