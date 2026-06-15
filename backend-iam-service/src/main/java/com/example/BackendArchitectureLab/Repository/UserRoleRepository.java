package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
//    @Modifying
//    @Query("delete from UserRole ur where ur.user IN ?1 and ur.role IN ?2")
//    void deleteByUserAndRole(List<UUID> userKey, List<UUID> roleKey);
//    @Modifying
//    @Query("delete from UserRole ur where ur.user IN ?1 and ur.role IN ?2")
//
    void deleteAllByUserInAndRoleIn(List<User> userKey, List<Role> roleKey);

    List<UserRole> findByUserId(UUID userId);

    @Modifying
    @Query("delete from UserRole ur where ur.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from UserRole ur where ur.user.id = :userId and ur.role.id = :roleId")
    void deleteByUserIdAndRoleId(@Param("userId") UUID userId, @Param("roleId") UUID roleId);
}
