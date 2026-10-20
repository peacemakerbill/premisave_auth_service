package com.premisave.auth.service.security;

import java.util.List;

/** Outcome of the sign-in risk check. Reasons are short, human readable and safe to show to the owner. */
public record RiskAssessment(int score, List<String> reasons, boolean suspicious) {

    public static RiskAssessment none() {
        return new RiskAssessment(0, List.of(), false);
    }
}
