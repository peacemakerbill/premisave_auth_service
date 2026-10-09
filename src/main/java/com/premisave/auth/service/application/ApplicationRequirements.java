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
                "Any one of: both sides of your government ID card, the photo page of your passport, or your driving licence.",
                true, List.of(
                        List.of(DocumentType.GOVERNMENT_ID_FRONT, DocumentType.GOVERNMENT_ID_BACK),
                        List.of(DocumentType.PASSPORT_BIO_PAGE),
                        List.of(DocumentType.DRIVING_LICENCE))));

        list.add(new Requirement("OWNERSHIP", "Proof of property ownership",
                "One of: title deed, sale agreement, head lease or allotment letter, for at least one property.",
                true, List.of(
                        List.of(DocumentType.TITLE_DEED),
                        List.of(DocumentType.SALE_AGREEMENT),
                        List.of(DocumentType.LEASE_AGREEMENT),
                        List.of(DocumentType.ALLOTMENT_LETTER))));

        // Optional extras that make verification faster
        list.add(new Requirement("TAX_ID_DOCUMENT", "Tax ID document",
                "Optional. A tax registration certificate or official tax letter that shows your tax ID.",
                false, List.of(List.of(DocumentType.TAX_ID_DOCUMENT))));
        list.add(new Requirement("SELFIE_WITH_ID", "Selfie holding your ID",
                "Optional. Helps us confirm the ID is yours.",
                false, List.of(List.of(DocumentType.SELFIE_WITH_ID))));
        list.add(new Requirement("ADDRESS", "Proof of address",
                "Optional. A utility bill or bank statement from the last three months.",
                false, List.of(List.of(DocumentType.UTILITY_BILL))));
        list.add(new Requirement("TAX_COMPLIANCE", "Tax compliance certificate",
                "Optional. A current tax compliance or good standing certificate, if your country issues one.",
                false, List.of(List.of(DocumentType.TAX_COMPLIANCE_CERTIFICATE))));

        return list;
    }
}