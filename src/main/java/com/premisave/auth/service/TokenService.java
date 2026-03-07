package com.premisave.auth.service;

import com.premisave.auth.repository.TokenRepository;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final TokenRepository tokenRepository;

    public TokenService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    // Additional methods if needed
}