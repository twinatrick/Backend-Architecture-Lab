package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.role WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT u.id FROM User u")
    List<UUID> findAllIds();

    @Query("SELECT u.id FROM User u")
    List<UUID> findPagedIds(Pageable pageable);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.role WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.role WHERE u.id IN :ids")
    List<User> findAllByIdsWithRoles(@Param("ids") List<UUID> ids);
}
