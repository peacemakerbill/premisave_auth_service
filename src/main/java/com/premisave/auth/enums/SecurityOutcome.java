package com.premisave.auth.enums;

/** Whether the thing the event describes worked. Most events are SUCCESS. Failed sign-ins, lockouts and failed deliveries are FAILURE. */
public enum SecurityOutcome {
    SUCCESS,
    FAILURE
}
