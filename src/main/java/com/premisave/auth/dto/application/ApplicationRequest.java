package com.premisave.auth.dto.application;

import com.premisave.auth.enums.ManagementPreference;
import com.premisave.auth.enums.OwnerType;
import com.premisave.auth.enums.PayoutMethod;
import com.premisave.auth.enums.PropertyType;
import com.premisave.auth.enums.ReferralSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Body for creating and updating a draft. Every field is optional, so the
 * frontend can save a step at a time: a field that is left out (null) is not
 * touched, and a blank text value clears that field.
 */
@Data
public class ApplicationRequest {

    private OwnerType ownerType;

    // Business
    @Size(max = 120, message = "Company name is too long")
    private String companyName;

    @Size(max = 60, message = "Registration number is too long")
    private String companyRegistrationNumber;

    // Identity and tax
    @Pattern(regexp = "^[0-9]{6,10}$", message = "National ID number must be 6 to 10 digits")
    private String nationalIdNumber;

    @Pattern(regexp = "^[A-Za-z0-9]{6,12}$", message = "Passport number must be 6 to 12 letters or digits")
    private String passportNumber;

    @Pattern(regexp = "^[A-Za-z0-9/\\-]{4,20}$", message = "Driving licence number looks invalid")
    private String drivingLicenceNumber;

    @Pattern(regexp = "^[AaPp][0-9]{9}[A-Za-z]$", message = "KRA PIN must look like A123456789B")
    private String kraPin;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 60, message = "Nationality is too long")
    private String nationality;

    // Contact and address
    @Pattern(regexp = "^\\+?[0-9 \\-]{9,18}$", message = "Phone number looks invalid")
    private String phoneNumber;

    @Pattern(regexp = "^\\+?[0-9 \\-]{9,18}$", message = "Alternate phone number looks invalid")
    private String alternatePhoneNumber;

    @Size(max = 60)
    private String country;

    @Size(max = 60)
    private String county;

    @Size(max = 80)
    private String town;

    @Size(max = 200, message = "Physical address is too long")
    private String physicalAddress;

    @Size(max = 120, message = "Postal address is too long")
    private String postalAddress;

    // Portfolio
    @Min(value = 1, message = "Enter at least 1 property")
    @Max(value = 5000, message = "Number of properties looks too high")
    private Integer numberOfProperties;

    @Min(value = 1, message = "Enter at least 1 unit")
    @Max(value = 100000, message = "Number of units looks too high")
    private Integer estimatedTotalUnits;

    @Size(max = 12)
    private List<PropertyType> propertyTypes;

    @Size(max = 150, message = "Property location is too long")
    private String primaryPropertyLocation;

    @Size(max = 600, message = "Property description must be 600 characters or fewer")
    private String propertyDescription;

    private ManagementPreference managementPreference;

    @Min(0)
    @Max(80)
    private Integer yearsAsLandlord;

    // Payout
    private PayoutMethod payoutMethod;

    @Pattern(regexp = "^\\+?[0-9 \\-]{9,18}$", message = "M-Pesa number looks invalid")
    private String mpesaNumber;

    @Size(max = 80)
    private String bankName;

    @Size(max = 100)
    private String bankAccountName;

    @Pattern(regexp = "^[0-9A-Za-z\\- ]{6,30}$", message = "Bank account number looks invalid")
    private String bankAccountNumber;

    @Size(max = 80)
    private String bankBranch;

    // About the applicant
    @Size(max = 1000, message = "Please keep this to 1000 characters or fewer")
    private String motivation;

    private ReferralSource referralSource;

    // Consent
    private Boolean termsAccepted;
    private Boolean privacyConsent;
    private Boolean declarationAccepted;
}