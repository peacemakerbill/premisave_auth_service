package com.premisave.auth.service.application;

import com.premisave.auth.dto.application.ApplicationDtos.FieldIssue;
import com.premisave.auth.dto.application.ApplicationDtos.ReadinessView;
import com.premisave.auth.dto.application.ApplicationDtos.RequirementView;
import com.premisave.auth.entity.ApplicationDocument;
import com.premisave.auth.entity.HomeOwnerApplication;
import com.premisave.auth.enums.DocumentStatus;
import com.premisave.auth.enums.DocumentType;
import com.premisave.auth.enums.OwnerType;
import com.premisave.auth.enums.PayoutMethod;
import com.premisave.auth.service.application.ApplicationRequirements.Requirement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Works out how complete an application is, what is still missing, and whether
 * the documents are verified. One place, so the progress bar the applicant sees,
 * the submit check and the staff "verify" check can never disagree.
 */
final class ApplicationReadiness {

    private ApplicationReadiness() {
    }

    static ReadinessView evaluate(HomeOwnerApplication a) {
        List<FieldIssue> issues = new ArrayList<>();
        int[] counts = {0, 0}; // total checks, passed checks

        OwnerType ownerType = a.getOwnerType() == null ? OwnerType.INDIVIDUAL : a.getOwnerType();

        if (ownerType == OwnerType.COMPANY) {
            check(issues, counts, "companyName", "Enter the company or organisation name", present(a.getCompanyName()));
            check(issues, counts, "companyRegistrationNumber", "Enter the company registration number",
                    present(a.getCompanyRegistrationNumber()));
        }

        check(issues, counts, "nationalIdNumber", "Enter your national ID number or passport number",
                present(a.getNationalIdNumber()) || present(a.getPassportNumber()));
        check(issues, counts, "dateOfBirth", "Enter your date of birth (you must be 18 or older)",
                ApplicationValidation.isAdult(a.getDateOfBirth()));
        check(issues, counts, "kraPin", "Enter your KRA PIN", present(a.getKraPin()));
        check(issues, counts, "phoneNumber", "Enter a phone number we can reach you on", present(a.getPhoneNumber()));
        check(issues, counts, "county", "Select your county", present(a.getCounty()));
        check(issues, counts, "town", "Enter your town or city", present(a.getTown()));
        check(issues, counts, "physicalAddress", "Enter your physical address", present(a.getPhysicalAddress()));

        check(issues, counts, "numberOfProperties", "Tell us how many properties you own or manage",
                a.getNumberOfProperties() != null && a.getNumberOfProperties() >= 1);
        check(issues, counts, "propertyTypes", "Select at least one property type",
                a.getPropertyTypes() != null && !a.getPropertyTypes().isEmpty());
        check(issues, counts, "primaryPropertyLocation", "Enter where your main property is located",
                present(a.getPrimaryPropertyLocation()));
        check(issues, counts, "managementPreference", "Tell us how you manage your properties today",
                a.getManagementPreference() != null);

        check(issues, counts, "payoutMethod", "Choose how you want to be paid", a.getPayoutMethod() != null);
        if (a.getPayoutMethod() == PayoutMethod.MPESA) {
            check(issues, counts, "mpesaNumber", "Enter the M-Pesa number to pay into", present(a.getMpesaNumber()));
        } else if (a.getPayoutMethod() == PayoutMethod.BANK_TRANSFER) {
            check(issues, counts, "bankName", "Enter your bank name", present(a.getBankName()));
            check(issues, counts, "bankAccountName", "Enter the name on the bank account", present(a.getBankAccountName()));
            check(issues, counts, "bankAccountNumber", "Enter your bank account number", present(a.getBankAccountNumber()));
        }

        check(issues, counts, "termsAccepted", "Accept the terms and conditions", a.isTermsAccepted());
        check(issues, counts, "privacyConsent", "Consent to us processing your documents and personal data", a.isPrivacyConsent());
        check(issues, counts, "declarationAccepted", "Confirm the information you gave is true", a.isDeclarationAccepted());

        // Documents
        Map<DocumentType, ApplicationDocument> byType = new EnumMap<>(DocumentType.class);
        Map<DocumentType, Boolean> rejected = new EnumMap<>(DocumentType.class);
        if (a.getDocuments() != null) {
            for (ApplicationDocument d : a.getDocuments()) {
                if (d.getType() == null) {
                    continue;
                }
                if (d.getStatus() == DocumentStatus.REJECTED) {
                    rejected.put(d.getType(), true);
                    continue;
                }
                ApplicationDocument existing = byType.get(d.getType());
                // Keep the best document of a type: a verified one beats a pending one
                if (existing == null || d.getStatus() == DocumentStatus.VERIFIED) {
                    byType.put(d.getType(), d);
                }
            }
        }

        List<RequirementView> requirementViews = new ArrayList<>();
        boolean allRequiredVerified = true;

        for (Requirement r : ApplicationRequirements.forOwnerType(ownerType)) {
            boolean uploaded = false;
            boolean verified = false;
            boolean attention = false;
            List<DocumentType> bestMissing = null;

            for (List<DocumentType> combo : r.combinations()) {
                List<DocumentType> missing = new ArrayList<>();
                boolean comboVerified = true;
                for (DocumentType type : combo) {
                    ApplicationDocument d = byType.get(type);
                    if (d == null) {
                        missing.add(type);
                        comboVerified = false;
                        if (rejected.containsKey(type)) {
                            attention = true;
                        }
                    } else if (d.getStatus() != DocumentStatus.VERIFIED) {
                        comboVerified = false;
                    }
                }
                if (missing.isEmpty()) {
                    uploaded = true;
                    if (comboVerified) {
                        verified = true;
                    }
                }
                if (bestMissing == null || missing.size() < bestMissing.size()) {
                    bestMissing = missing;
                }
            }

            if (uploaded) {
                attention = false;
            }

            if (r.required()) {
                counts[0]++;
                if (uploaded) {
                    counts[1]++;
                } else {
                    String text = attention
                            ? r.label() + ": a file was rejected, please upload a replacement"
                            : "Upload: " + r.label();
                    issues.add(new FieldIssue("documents." + r.key(), text));
                }
                if (!verified) {
                    allRequiredVerified = false;
                }
            }

            requirementViews.add(new RequirementView(
                    r.key(), r.label(), r.description(), r.required(), r.combinations(),
                    uploaded || bestMissing == null ? List.of() : bestMissing,
                    uploaded, verified, attention));
        }

        int percent = counts[0] == 0 ? 0 : (int) Math.round(counts[1] * 100.0 / counts[0]);
        return new ReadinessView(percent, issues.isEmpty(), allRequiredVerified, issues, requirementViews);
    }

    private static void check(List<FieldIssue> issues, int[] counts, String field, String message, boolean ok) {
        counts[0]++;
        if (ok) {
            counts[1]++;
        } else {
            issues.add(new FieldIssue(field, message));
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}