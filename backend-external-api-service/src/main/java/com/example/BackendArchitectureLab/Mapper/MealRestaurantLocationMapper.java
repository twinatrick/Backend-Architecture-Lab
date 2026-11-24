package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Entity.MealRestaurantLocation;
import com.example.BackendArchitectureLab.Vo.MealRestaurantLocationVo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MealRestaurantLocationMapper {
    MealRestaurantLocationVo toVo(MealRestaurantLocation entity);
}
