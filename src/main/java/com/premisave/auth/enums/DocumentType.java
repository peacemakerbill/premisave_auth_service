package com.premisave.auth.enums;

/** Every document an applicant can attach, with the wording shown in the UI and in emails. */
public enum DocumentType {

    NATIONAL_ID_FRONT("National ID, front", "Clear photo or scan of the front of your Kenyan national ID.", "Identity", false),
    NATIONAL_ID_BACK("National ID, back", "Clear photo or scan of the back of your Kenyan national ID.", "Identity", false),
    PASSPORT_BIO_PAGE("Passport bio page", "The page of your passport with your photo and details.", "Identity", false),
    DRIVING_LICENCE("Driving licence", "Front of your driving licence. Optional, but it can speed up verification.", "Identity", false),
    SELFIE_WITH_ID("Selfie holding your ID", "A photo of you holding your ID next to your face. Optional, helps prevent identity fraud.", "Identity", false),

    KRA_PIN_CERTIFICATE("KRA PIN certificate", "Your KRA PIN certificate. The PIN must match the one you entered.", "Tax", false),
    TAX_COMPLIANCE_CERTIFICATE("Tax compliance certificate", "A current KRA tax compliance certificate. Optional.", "Tax", false),

    TITLE_DEED("Title deed", "Title deed or certificate of lease for a property you own.", "Ownership", true),
    SALE_AGREEMENT("Sale agreement", "Signed sale agreement for a property you bought.", "Ownership", true),
    LEASE_AGREEMENT("Head lease", "Head lease, if you rent out a property you lease yourself.", "Ownership", true),
    ALLOTMENT_LETTER("Allotment letter", "Allotment letter for a property that has no title yet.", "Ownership", true),
    LAND_RATES_CLEARANCE("Land rates clearance", "A recent land rates or rent clearance certificate. Optional.", "Ownership", true),

    UTILITY_BILL("Proof of address", "A utility bill or bank statement from the last three months. Optional.", "Address", false),

    CERTIFICATE_OF_INCORPORATION("Certificate of incorporation", "Company or organisation registration certificate.", "Business", false),
    CR12("CR12 or company search", "A recent CR12 listing the directors. Optional.", "Business", false),
    POWER_OF_ATTORNEY("Power of attorney or management agreement", "Signed authority from the owner(s) you manage property for.", "Business", true),

    OTHER("Other supporting document", "Anything else that helps the reviewer. Up to five files.", "Other", true);

    private final String label;
    private final String description;
    private final String category;
    private final boolean allowsMultiple;

    DocumentType(String label, String description, String category, boolean allowsMultiple) {
        this.label = label;
        this.description = description;
        this.category = category;
        this.allowsMultiple = allowsMultiple;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }

    /** When false, uploading again replaces the previous file of this type. */
    public boolean isAllowsMultiple() { return allowsMultiple; }

    /** Upper bound on files of a multi-file type. */
    public int maxFiles() {
        return allowsMultiple ? (this == OTHER ? 5 : 3) : 1;
    }
}