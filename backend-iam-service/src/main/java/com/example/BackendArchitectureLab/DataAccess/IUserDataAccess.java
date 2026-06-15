package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Dto.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Entity.User;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access interface for User entity operations.
 * Abstracts UserRepository operations for service layer.
 */
public interface IUserDataAccess {

    /**
     * Save a user entity.
     *
     * @param user the user to save
     */
    void save(User user);

    /**
     * Find all users.
     *
     * @return list of all users
     */
    List<User> findAll();

    /**
     * Find user by email address.
     *
     * @param email the email to search for
     * @return optional user with matching email
     */
    Optional<User> findByEmail(String email);

    /**
     * Find a user by their key.
     *
     * @param key the user UUID
     * @return optional containing the user if found
     */
    Optional<User> findById(UUID key);

    /**
     * Check if a user exists by their key.
     *
     * @param key the user UUID
     * @return true if user exists, false otherwise
     */
    boolean existsById(UUID key);

    /**
     * Find all users by their keys.
     *
     * @param keys list of user UUIDs
     * @return list of users matching the keys
     */
    List<User> findAllById(List<UUID> keys);
    
    /**
     * 分頁查詢使用者
     *
     * @param query 查詢參數
     * @return 分頁結果
     */
    Page<User> searchUsers(UserSearchQuery query);
}
