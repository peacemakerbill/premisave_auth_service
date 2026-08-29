package com.premisave.auth.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.premisave.auth.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response shape for the internal, service-to-service user-details
 * lookup (see InternalUserValidationController.getUserDetails). Field
 * names must match exactly what UserDetailsDto (the wallet service's own
 * copy of this shape, in com.premisave.wallet.dto.client) expects to
 * deserialize — Jackson matches by field name across the two services,
 * there's no shared class between them.
 *
 * Deliberately a SEPARATE, smaller DTO from UserDto — this is for
 * cross-service consumption over an internal, API-key-protected channel,
 * not the richer public/self profile shape UserDto serves for
 * browser-facing endpoints. Keeping it separate means a future change to
 * UserDto's shape (adding a browser-only field, say) can't accidentally
 * change what other services receive here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"id", "email", "role", "active", "verified", "firstName", "middleName", "lastName", "fullName"})
public class UserDetailsInternalResponse {

    private String id;
    private String email;
    private Role role;
    private boolean active;
    private boolean verified;
    private String firstName;
    private String middleName;
    private String lastName;

    /**
     * Same computed-field pattern already established in
     * UserDto.getFullName() — reused here for consistency rather than
     * inventing a different approach for the same underlying concept.
     * No backing field; Jackson serializes this as "fullName" via bean
     * convention (a public no-arg getter following the getX() naming
     * rule).
     */
    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            sb.append(firstName.trim());
        }
        if (middleName != null && !middleName.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(middleName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(lastName.trim());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}