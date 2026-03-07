package com.premisave.auth.dto;

import lombok.Data;

@Data
public class UserSearchRequest {
    private String query; // email, name, etc.
}