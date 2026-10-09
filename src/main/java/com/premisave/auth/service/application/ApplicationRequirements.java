package com.premisave.auth.service.application;

import com.premisave.auth.enums.DocumentType;
import com.premisave.auth.enums.OwnerType;

import java.util.ArrayList;
import java.util.List;

/**
 * The document checklist, which depends on who is applying.
 *
 * A requirement lists one or more acceptable combinations of document types.
 * It is satisfied when every document of at least one combination is present.
 */
public final class ApplicationRequirements {

    public record Requirement(String key, String label, String description, boolean required,
                              List<List<DocumentType>> combinations) {
    }

    private ApplicationRequirements() {
    }

    public static List<Requirement> forOwnerType(OwnerType ownerType) {
        OwnerType type = ownerType == null ? OwnerType.INDIVIDUAL : ownerType;
        List<Requirement> list = new ArrayList<>();

        if (type == OwnerType.COMPANY) {
            list.add(new Requirement("COMPANY_REGISTRATION", "Company registration",
                    "Certificate of incorporation or registration for the company or organisation.",
                    true, List.of(List.of(DocumentType.CERTIFICATE_OF_INCORPORATION))));
        }
        if (type == OwnerType.PROPERTY_MANAGER) {
            list.add(new Requirement("AUTHORITY_TO_MANAGE", "Authority to manage",
                    "Power of attorney or a signed management agreement from the owner or owners.",
                    true, List.of(List.of(DocumentType.POWER_OF_ATTORNEY))));
        }

        list.add(new Requirement("IDENTITY",
                type == OwnerType.COMPANY ? "Director identity" : "Identity",
                "Both sides of your Kenyan national ID, or the bio page of your passport.",
                true, List.of(
                        List.of(DocumentType.NATIONAL_ID_FRONT, DocumentType.NATIONAL_ID_BACK),
                        List.of(DocumentType.PASSPORT_BIO_PAGE))));

        list.add(new Requirement("TAX", "KRA PIN certificate",
                type == OwnerType.COMPANY
                        ? "The company's KRA PIN certificate."
                        : "Your KRA PIN certificate.",
                true, List.of(List.of(DocumentType.KRA_PIN_CERTIFICATE))));

        list.add(new Requirement("OWNERSHIP", "Proof of property ownership",
                "One of: title deed, sale agreement, head lease or allotment letter, for at least one property.",
                true, List.of(
                        List.of(DocumentType.TITLE_DEED),
                        List.of(DocumentType.SALE_AGREEMENT),
                        List.of(DocumentType.LEASE_AGREEMENT),
                        List.of(DocumentType.ALLOTMENT_LETTER))));

        // Optional extras that make verification faster
        list.add(new Requirement("DRIVING_LICENCE", "Driving licence",
                "Optional. A second ID that can speed up verification.",
                false, List.of(List.of(DocumentType.DRIVING_LICENCE))));
        list.add(new Requirement("SELFIE_WITH_ID", "Selfie holding your ID",
                "Optional. Helps us confirm the ID is yours.",
                false, List.of(List.of(DocumentType.SELFIE_WITH_ID))));
        list.add(new Requirement("ADDRESS", "Proof of address",
                "Optional. A utility bill or bank statement from the last three months.",
                false, List.of(List.of(DocumentType.UTILITY_BILL))));
        list.add(new Requirement("TAX_COMPLIANCE", "Tax compliance certificate",
                "Optional. A current KRA tax compliance certificate.",
                false, List.of(List.of(DocumentType.TAX_COMPLIANCE_CERTIFICATE))));

        return list;
    }
}