package com.premisave.auth.enums;

/** Purposes of the single-use tokens that travel by email. */
public enum ActionTokenType {
    /** "This sign-in was not me" link inside a sign-in alert. */
    REPORT_UNAUTHORIZED_SIGN_IN,
    /** Lets a pre-provisioned administrator set a password and activate the account. */
    ADMIN_INVITATION
}
