package com.premisave.auth.enums;

/** Every document an applicant can attach, with the wording shown in the UI and in emails. */
public enum DocumentType {

    GOVERNMENT_ID_FRONT("ID card, front", "Clear photo or scan of the front of your government-issued ID card.", "Identity", false),
    GOVERNMENT_ID_BACK("ID card, back", "Clear photo or scan of the back of your government-issued ID card.", "Identity", false),
    PASSPORT_BIO_PAGE("Passport bio page", "The page of your passport with your photo and details.", "Identity", false),
    DRIVING_LICENCE("Driving licence", "A clear photo or scan of your driving licence. Include both sides if it has details on the back.", "Identity", false),
    SELFIE_WITH_ID("Selfie holding your ID", "A photo of you holding your ID next to your face. Optional, helps prevent identity fraud.", "Identity", false),

    TAX_ID_DOCUMENT("Tax ID document", "A document showing your tax ID, such as a tax registration certificate or an official tax letter. Optional.", "Tax", false),
    TAX_COMPLIANCE_CERTIFICATE("Tax compliance certificate", "A current tax compliance or good standing certificate, if your country issues one. Optional.", "Tax", false),

    TITLE_DEED("Title deed", "Title deed or certificate of lease for a property you own.", "Ownership", true),
    SALE_AGREEMENT("Sale agreement", "Signed sale agreement for a property you bought.", "Ownership", true),
    LEASE_AGREEMENT("Head lease", "Head lease, if you rent out a property you lease yourself.", "Ownership", true),
    ALLOTMENT_LETTER("Allotment letter", "Allotment letter for a property that has no title yet.", "Ownership", true),
    PROPERTY_TAX_CLEARANCE("Property tax clearance", "A recent property tax, land rates or rent clearance certificate. Optional.", "Ownership", true),

    UTILITY_BILL("Proof of address", "A utility bill or bank statement from the last three months. Optional.", "Address", false),

    CERTIFICATE_OF_INCORPORATION("Certificate of incorporation", "Company or organisation registration certificate.", "Business", false),
    DIRECTORS_REGISTER("Directors listing", "A recent official extract or register listing the company directors. Optional.", "Business", false),
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