package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealPhotoRepository extends JpaRepository<MealPhoto, UUID> {
    Optional<MealPhoto> findByMealIdAndOperationId(UUID mealId, UUID operationId);
    Optional<MealPhoto> findByIdAndMealId(UUID id, UUID mealId);
    List<MealPhoto> findByMealIdAndState(UUID mealId, String state);
    List<MealPhoto> findByMealId(UUID mealId);
    boolean existsByObjectKeyAndState(String objectKey, String state);

    @Query("select count(photo) from MealPhoto photo where photo.mealId = :mealId and (photo.state = 'READY' or (photo.state = 'UPLOADING' and photo.leaseUntil > :now))")
    long countOccupied(@Param("mealId") UUID mealId, @Param("now") Date now);
}
