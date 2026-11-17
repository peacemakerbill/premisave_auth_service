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

    /** The HTTP method of the request being handled, or null outside a request (a background job, for example). */
    public static String currentMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getMethod();
    }

    /**
     * The path of the request being handled, without the query string. Segments that look like an id or a token (24 or more
     * letters, digits and dashes) are shown as {id}, so a secret in a path can never be written to the audit trail.
     */
    public static String currentPath() {
        HttpServletRequest request = currentRequest();
        if (request == null || request.getRequestURI() == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String part : request.getRequestURI().split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            out.append('/').append(part.length() >= 24 && part.matches("[A-Za-z0-9_-]+") ? "{id}" : part);
        }
        String path = out.length() == 0 ? "/" : out.toString();
        return path.length() > 200 ? path.substring(0, 200) : path;
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
