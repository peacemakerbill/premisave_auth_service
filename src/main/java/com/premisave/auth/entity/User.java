package com.premisave.auth.entity;

import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Document(collection = "users")
public class User implements UserDetails {

    @Id
    private String id;

    @Getter
    @Setter
    private String username;
    
    private String firstName;
    private String middleName;
    private String lastName;
    
    @Getter
    @Setter
    private String email;
    
    private String phoneNumber;
    private String address1;
    private String address2;
    private String country;
    private Language language = Language.ENGLISH;
    private String profilePictureUrl;
    private String password;
    private Role role;

    private boolean active = true;
    private boolean verified = false;
    private boolean archived = false;

    // === AUDIT FIELDS ===
    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;

    @CreatedBy
    @DocumentReference
    private User createdBy;

    @LastModifiedBy
    @DocumentReference
    private User updatedBy;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // Spring Security needs this to return the login identifier (email)
    @Override
    public String getUsername() {
        return email;  // Return email for Spring Security compatibility
    }
    
    // Custom getter for the actual username field
    public String getDisplayUsername() {
        return username;
    }
    
    // Custom setter for the actual username field
    public void setDisplayUsername(String username) {
        this.username = username;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() {
        return active && verified;
    }
}