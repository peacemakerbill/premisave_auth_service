package com.premisave.auth.service.security;

/** What could be read from a User-Agent. All of it is an unverified hint. */
public record DeviceInfo(String label, String deviceType, String os, String browser) {

    public static DeviceInfo unknown() {
        return new DeviceInfo("Unknown device", "UNKNOWN", null, null);
    }
}
