package com.premisave.auth.service.security;

/** Resolves an IP address to an approximate location. Never throws; returns unavailable() instead. */
public interface GeoIpService {

    GeoLocation lookup(String ip);
}
