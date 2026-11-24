package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Dto.Request.MealDetailsRequest;
import com.example.BackendArchitectureLab.Entity.MealRecord;
import com.example.BackendArchitectureLab.Vo.MealRecordVo;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.OffsetDateTime;
import java.util.Date;

@Mapper(componentModel = "spring")
public interface MealRecordMapper {
    @Mapping(target = "revision", source = "version")
    @Mapping(target = "currentRestaurantName", ignore = true)
    @Mapping(target = "currentRestaurantStatus", ignore = true)
    @Mapping(target = "movedToLocationId", ignore = true)
    MealRecordVo toVo(MealRecord entity);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "mealName", source = "mealName")
    @Mapping(target = "eatenAt", source = "eatenAt")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "amountExplanation", source = "amountExplanation")
    @Mapping(target = "receiptId", source = "receiptId")
    @Mapping(target = "receiptItemIndex", source = "receiptItemIndex")
    @Mapping(target = "restaurantLocationId", source = "restaurantLocationId")
    @Mapping(target = "restaurantName", source = "restaurantName")
    @Mapping(target = "locationDescription", source = "locationDescription")
    @Mapping(target = "floor", source = "floor")
    @Mapping(target = "googleMapsUrl", source = "googleMapsUrl")
    @Mapping(target = "latitude", source = "latitude")
    @Mapping(target = "longitude", source = "longitude")
    @Mapping(target = "rating", source = "rating")
    @Mapping(target = "review", source = "review")
    void update(MealDetailsRequest request, @MappingTarget MealRecord entity);

    default Date toDate(OffsetDateTime value) {
        return value == null ? null : Date.from(value.toInstant());
    }
}
