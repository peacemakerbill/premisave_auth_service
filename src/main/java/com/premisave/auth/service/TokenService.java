package com.premisave.auth.service;

import com.premisave.auth.repository.TokenRepository;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    public TokenService(TokenRepository tokenRepository) {
    }

    // Additional methods if needed
}