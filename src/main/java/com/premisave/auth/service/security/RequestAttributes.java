package com.premisave.auth.service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads values the filters stored on the current request, so deeper services (profile,
 * user management) can know the calling session and client without changing their method signatures.
 */
public final class RequestAttributes {

    public static final String SESSION_ID = "premisave.sessionId";
    public static final String CLIENT_CONTEXT = "premisave.clientContext";

    private RequestAttributes() {
    }

    public static String currentSessionId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(SESSION_ID);
        return value instanceof String s ? s : null;
    }

    public static ClientContext currentClient() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return ClientContext.unknown();
        }
        Object value = request.getAttribute(CLIENT_CONTEXT);
        return value instanceof ClientContext ctx ? ctx : ClientContext.unknown();
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
