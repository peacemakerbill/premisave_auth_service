package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * IP geolocation over HTTP. It does nothing unless app.security.ip-geolocation-enabled
 * is true, because every lookup sends the visitor's IP address to the configured provider.
 * Private, loopback and malformed addresses are never sent. Results are cached in memory.
 */
@Slf4j
@Service
public class HttpGeoIpService implements GeoIpService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");
    private static final int CACHE_SIZE = 5000;
    private static final long CACHE_TTL_MS = Duration.ofHours(6).toMillis();

    private final SecurityProperties properties;
    private final RestClient http;

    private final Map<String, CachedLocation> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedLocation> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private record CachedLocation(GeoLocation location, long storedAt) {
    }

    public HttpGeoIpService(SecurityProperties properties) {
        this.properties = properties;
        Duration timeout = Duration.ofMillis(Math.max(200, properties.getIpGeolocationTimeoutMs()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public GeoLocation lookup(String ip) {
        if (!properties.isIpGeolocationEnabled() || !isPublicAddress(ip)) {
            return GeoLocation.unavailable();
        }

        synchronized (cache) {
            CachedLocation hit = cache.get(ip);
            if (hit != null && System.currentTimeMillis() - hit.storedAt() < CACHE_TTL_MS) {
                return hit.location();
            }
        }

        GeoLocation location = fetch(ip);
        synchronized (cache) {
            cache.put(ip, new CachedLocation(location, System.currentTimeMillis()));
        }
        return location;
    }

    private GeoLocation fetch(String ip) {
        try {
            Map<String, Object> body = http.get()
                    .uri(properties.getIpGeolocationUrl(), ip)
                    .retrieve()
                    .body(MAP);
            if (body == null || Boolean.FALSE.equals(body.get("success"))) {
                return GeoLocation.unavailable();
            }
            String country = firstText(body, "country_name", "country");
            String region = firstText(body, "region", "region_name");
            String city = firstText(body, "city");
            return new GeoLocation(country, region, city);
        } catch (Exception e) {
            // A slow or broken provider must never affect sign-in.
            log.debug("IP geolocation unavailable: {}", e.getClass().getSimpleName());
            return GeoLocation.unavailable();
        }
    }

    private static String firstText(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.length() > 80 ? text.substring(0, 80) : text.trim();
            }
        }
        return null;
    }

    static boolean isPublicAddress(String ip) {
        if (ip == null || !IP_LITERAL.matcher(ip).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return !(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress());
        } catch (Exception e) {
            return false;
        }
    }
}
