package com.premisave.auth.repository;

import com.premisave.auth.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    
    // Find by email (exact match)
    Optional<User> findByEmail(String email);
    
    // Find by username (exact match)
    Optional<User> findByUsername(String username);
    
    // Find active, non-archived users
    List<User> findByActiveTrueAndArchivedFalse();
    
    // Find archived users
    List<User> findByArchivedTrue();
    
    // Find active users
    List<User> findByActiveTrue();
    
    // Find inactive users
    List<User> findByActiveFalse();
    
    // Find verified users
    List<User> findByVerifiedTrue();
    
    // Find unverified users
    List<User> findByVerifiedFalse();
    
    // Find by role
    List<User> findByRole(com.premisave.auth.enums.Role role);
    
    // Search users across multiple fields (case-insensitive)
    @Query("{'$or': ["
            + "{'email': {'$regex': ?0, '$options': 'i'}}, "
            + "{'firstName': {'$regex': ?0, '$options': 'i'}}, "
            + "{'lastName': {'$regex': ?0, '$options': 'i'}}, "
            + "{'username': {'$regex': ?0, '$options': 'i'}}, "
            + "{'phoneNumber': {'$regex': ?0, '$options': 'i'}}"
            + "]}")
    List<User> searchUsers(String query);
    
    // Search with additional filters
    @Query("{'$and': ["
            + "{'$or': ["
            + "{'email': {'$regex': ?0, '$options': 'i'}}, "
            + "{'firstName': {'$regex': ?0, '$options': 'i'}}, "
            + "{'lastName': {'$regex': ?0, '$options': 'i'}}, "
            + "{'username': {'$regex': ?0, '$options': 'i'}}"
            + "]}, "
            + "{'active': ?1}, "
            + "{'archived': ?2}"
            + "]}")
    List<User> searchUsersWithFilters(String query, boolean active, boolean archived);
    
    // Check if email exists (for validation)
    boolean existsByEmail(String email);
    
    // Check if username exists (for validation)
    boolean existsByUsername(String username);
    
    // Find users by country
    List<User> findByCountry(String country);
    
    // Find users by language
    List<User> findByLanguage(com.premisave.auth.enums.Language language);
    
    // Find users created after a certain date
    List<User> findByCreatedAtAfter(java.time.LocalDateTime date);
    
    // Find users by last login date range
    List<User> findByLastLoginAtBetween(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);
    
    // Count users by status
    long countByActiveTrue();
    long countByActiveFalse();
    long countByArchivedTrue();
    long countByVerifiedTrue();
    long countByVerifiedFalse();
    
    // Find users with pagination support
    @Query("{'active': true, 'archived': false}")
    List<User> findActiveUsers(org.springframework.data.domain.Pageable pageable);
    
    // Find by multiple criteria
    @Query("{'$and': ["
            + "{'active': ?0}, "
            + "{'verified': ?1}, "
            + "{'archived': ?2}"
            + "]}")
    List<User> findByStatus(boolean active, boolean verified, boolean archived);
    
    // Find users with email containing specific domain
    @Query("{'email': {'$regex': ?0, '$options': 'i'}}")
    List<User> findByEmailDomain(String domain);
    
    // Find users by name pattern (first or last name)
    @Query("{'$or': ["
            + "{'firstName': {'$regex': ?0, '$options': 'i'}}, "
            + "{'lastName': {'$regex': ?0, '$options': 'i'}}"
            + "]}")
    List<User> findByNameContaining(String name);
}