package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.MealLineEventDedup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealLineEventDedupRepository extends JpaRepository<MealLineEventDedup, String> {
}
