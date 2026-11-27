package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealLineBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface MealLineBindingRepository extends JpaRepository<MealLineBinding, UUID> {
    Optional<MealLineBinding> findByOwnerUserId(UUID ownerUserId);
    Optional<MealLineBinding> findByLineUserId(String lineUserId);

    @Transactional
    void deleteByOwnerUserId(UUID ownerUserId);
}
