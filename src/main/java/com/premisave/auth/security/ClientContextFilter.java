package com.premisave.auth.security;

import com.premisave.auth.service.security.ClientContextResolver;
import com.premisave.auth.service.security.RequestAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Resolves the client address, User-Agent and device token once per request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ClientContextFilter extends OncePerRequestFilter {

    private final ClientContextResolver resolver;

    public ClientContextFilter(ClientContextResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(RequestAttributes.CLIENT_CONTEXT, resolver.resolve(request));
        chain.doFilter(request, response);
    }
}
