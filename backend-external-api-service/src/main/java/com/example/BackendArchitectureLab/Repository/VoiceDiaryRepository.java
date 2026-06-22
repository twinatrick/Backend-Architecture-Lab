package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.VoiceDiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VoiceDiaryRepository extends JpaRepository<VoiceDiary, UUID> {
    List<VoiceDiary> findByUserIdOrderByCreatedTimeDesc(String userId);
}
