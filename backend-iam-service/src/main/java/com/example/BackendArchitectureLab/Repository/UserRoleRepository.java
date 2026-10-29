package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserRole ur where ur.user in ?1 and ur.role in ?2")
    void deleteAllByUserInAndRoleIn(List<User> userKey, List<Role> roleKey);

    @EntityGraph(attributePaths = {"role", "user"})
    List<UserRole> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"role", "user"})
    List<UserRole> findByUserIdIn(Collection<UUID> userIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserRole ur where ur.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserRole ur where ur.role.id = :roleId")
    void deleteByRoleId(@Param("roleId") UUID roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserRole ur where ur.user.id = :userId and ur.role.id = :roleId")
    void deleteByUserIdAndRoleId(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserRole ur where ur.user.id = :userId and ur.role.id in :roleIds")
    void deleteByUserIdAndRoleIds(@Param("userId") UUID userId, @Param("roleIds") Collection<UUID> roleIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserRole ur where ur.user.id in :userIds and ur.role.id = :roleId")
    void deleteByUserIdsAndRoleId(@Param("userIds") Collection<UUID> userIds, @Param("roleId") UUID roleId);
}
