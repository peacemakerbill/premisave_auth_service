package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the client address.
 *
 * The connection address is the default. X-Forwarded-For is only looked at when the
 * connection comes from a proxy listed in app.security.trusted-proxies, and then the
 * list is read from the right: the first address that is not itself a trusted proxy is
 * the client. Anything a client puts at the left of the header is ignored.
 */
@Component
public class ClientIpResolver {

    /** Literal addresses only, so the matcher never triggers a DNS lookup. */
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final List<IpAddressMatcher> trusted = new ArrayList<>();

    public ClientIpResolver(SecurityProperties properties) {
        String raw = properties.getTrustedProxies();
        if (raw != null) {
            for (String entry : raw.split(",")) {
                String value = entry.trim();
                if (!value.isEmpty()) {
                    try {
                        trusted.add(new IpAddressMatcher(value));
                    } catch (RuntimeException e) {
                        // A bad entry must not stop startup; it simply trusts nothing.
                    }
                }
            }
        }
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trusted.isEmpty() || !isTrusted(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String candidate = hops[i].trim();
            if (!IP_LITERAL.matcher(candidate).matches()) {
                return remote;
            }
            if (!isTrusted(candidate)) {
                return candidate;
            }
        }
        return remote;
    }

    boolean isTrusted(String address) {
        if (address == null || !IP_LITERAL.matcher(address).matches()) {
            return false;
        }
        for (IpAddressMatcher matcher : trusted) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // malformed address: not trusted
            }
        }
        return false;
    }
}
