package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealLineBindingCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MealLineBindingCodeRepository extends JpaRepository<MealLineBindingCode, UUID> {
    Optional<MealLineBindingCode> findByBindingCodeAndConsumedFalse(String bindingCode);
}
