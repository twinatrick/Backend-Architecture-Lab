package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.LineGfSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LineGfSessionRepository extends JpaRepository<LineGfSession, UUID> {
    Optional<LineGfSession> findByUserId(String userId);
}
