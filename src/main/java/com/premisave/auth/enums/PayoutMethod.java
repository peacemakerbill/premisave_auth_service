package com.premisave.auth.enums;

public enum PayoutMethod {
    BANK_TRANSFER("Bank transfer"),
    MOBILE_MONEY("Mobile money"),
    PAYPAL("PayPal");

    private final String label;

    PayoutMethod(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}