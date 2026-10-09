package com.premisave.auth.enums;

public enum PayoutMethod {
    MPESA("M-Pesa"),
    BANK_TRANSFER("Bank transfer");

    private final String label;

    PayoutMethod(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}