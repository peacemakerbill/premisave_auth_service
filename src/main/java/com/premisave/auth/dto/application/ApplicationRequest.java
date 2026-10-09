package com.premisave.auth.dto.application;

import com.premisave.auth.enums.IdType;
import com.premisave.auth.enums.ManagementPreference;
import com.premisave.auth.enums.OwnerType;
import com.premisave.auth.enums.PayoutMethod;
import com.premisave.auth.enums.PropertyType;
import com.premisave.auth.enums.ReferralSource;
import jakarta.validation.constraints.Email;
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
    private IdType idType;

    @Pattern(regexp = "^[A-Za-z0-9 ./\\-]{4,30}$", message = "ID number looks invalid (4 to 30 letters, digits, spaces, dots, slashes or dashes)")
    private String idNumber;

    @Size(max = 56, message = "Choose the country that issued the ID")
    private String idIssuingCountry;

    @Pattern(regexp = "^[A-Za-z0-9 ./\\-]{4,40}$", message = "Tax ID looks invalid (4 to 40 letters, digits, spaces, dots, slashes or dashes)")
    private String taxId;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 56, message = "Choose a nationality from the list")
    private String nationality;

    // Contact and address. Phone numbers use the international format, for example +254712345678.
    @Pattern(regexp = "^[+0-9 ().\\-]{7,24}$", message = "Phone number looks invalid. Include the country code, for example +254712345678")
    private String phoneNumber;

    @Pattern(regexp = "^[+0-9 ().\\-]{7,24}$", message = "Alternate phone number looks invalid. Include the country code")
    private String alternatePhoneNumber;

    /** Country name, ISO code (KE or KEN). Stored as the two letter ISO code. */
    @Size(max = 56)
    private String country;

    /** State, province, county or similar. */
    @Size(max = 80)
    private String region;

    @Size(max = 80)
    private String city;

    @Size(max = 200, message = "Street address is too long")
    private String physicalAddress;

    @Size(max = 120, message = "Postal address or ZIP code is too long")
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

    @Size(max = 60, message = "Provider name is too long")
    private String mobileMoneyProvider;

    @Pattern(regexp = "^[+0-9 ().\\-]{7,24}$", message = "Mobile money number looks invalid. Include the country code")
    private String mobileMoneyNumber;

    @Email(message = "PayPal email looks invalid")
    @Size(max = 120)
    private String paypalEmail;

    @Size(max = 80)
    private String bankName;

    @Size(max = 100)
    private String bankAccountName;

    @Pattern(regexp = "^[0-9A-Za-z\\- ]{5,34}$", message = "Account number or IBAN looks invalid")
    private String bankAccountNumber;

    @Pattern(regexp = "^[A-Za-z]{4}[A-Za-z]{2}[A-Za-z0-9]{2}([A-Za-z0-9]{3})?$", message = "SWIFT or BIC code must be 8 or 11 characters")
    private String bankSwiftCode;

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