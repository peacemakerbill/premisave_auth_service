package com.premisave.auth.service.security;

/** Resolves an IP address to an approximate location. Never throws; returns unavailable() instead. */
public interface GeoIpService {

    GeoLocation lookup(String ip);

    /** The location of an address that was already looked up, without making a new lookup. Unavailable when it is not known. */
    default GeoLocation cached(String ip) {
        return GeoLocation.unavailable();
    }
}
