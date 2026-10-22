# Postman setup: Home Owner applications

Add these three folders inside the existing **auth-service** folder, next to `user-authentication`, `profile`, `social`, `location`, `home` and `admin-user-management`.

| Folder | Who can call it |
|---|---|
| `home-owner-applications` | Any signed-in user (applicants use their own applications only) |
| `staff-application-review` | ADMIN, OPERATIONS, FINANCE, SUPPORT |
| `admin-application-actions` | ADMIN only |

## Collection variables to add

| Variable | Set by |
|---|---|
| `application_id` | The test script on Start, My current application and Open application |
| `document_id` | The test script on Upload document |
| `reviewer_id` | Copy an `id` from the Reviewers request |

`base_url` and `token` already exist. Sign in as a CLIENT to use the applicant folder, and as a staff user or admin for the other two.

## Headers

| Request type | Headers |
|---|---|
| JSON body | `Authorization: Bearer {{token}}` and `Content-Type: application/json` |
| No body (GET, DELETE, bare POST) | `Authorization: Bearer {{token}}` |
| Upload document | `Authorization: Bearer {{token}}` only. Body type **form-data**. Do not set Content-Type yourself, Postman adds the boundary. |

## Test script for Start, My current application, Upload document

```javascript
const j = pm.response.json();
if (j.id) { pm.collectionVariables.set('application_id', j.id); }
if (j.documents && j.documents.length) {
  pm.collectionVariables.set('document_id', j.documents[j.documents.length - 1].id);
}
```

For **Open application (staff view)** only the first `if` is needed.

---

# Folder 1: home-owner-applications

Base path: `{{base_url}}/home-owner/applications`

### 1. Form options and checklist (meta)
`GET {{base_url}}/home-owner/applications/meta`

Returns every dropdown, the countries, the document checklist per owner type and the upload limits. Status 200.

```json
{
  "ownerTypes": [{ "value": "INDIVIDUAL", "label": "Individual landlord", "description": "You own the property in your own name." }],
  "idTypes": [{ "value": "NATIONAL_ID", "label": "National ID card", "description": null }],
  "propertyTypes": [{ "value": "APARTMENT_BLOCK", "label": "Apartment block", "description": null }],
  "managementPreferences": [{ "value": "THROUGH_AGENT", "label": "I use an agent or caretaker", "description": null }],
  "payoutMethods": [{ "value": "BANK_TRANSFER", "label": "Bank transfer", "description": null }, { "value": "MOBILE_MONEY", "label": "Mobile money", "description": null }, { "value": "PAYPAL", "label": "PayPal", "description": null }],
  "referralSources": [{ "value": "FRIEND_OR_FAMILY", "label": "Friend or family", "description": null }],
  "statuses": [{ "value": "SUBMITTED", "label": "Submitted", "description": "We have received your application and it is waiting for a reviewer." }],
  "documentTypes": [{ "value": "GOVERNMENT_ID_FRONT", "label": "ID card, front", "description": "Clear photo or scan of the front of your government-issued ID card.", "category": "Identity", "allowsMultiple": false, "maxFiles": 1 }],
  "requirementsByOwnerType": {
    "INDIVIDUAL": [{ "key": "IDENTITY", "label": "Identity", "description": "Any one of: both sides of your government ID card, the photo page of your passport, or your driving licence.", "required": true,
      "acceptedCombinations": [["GOVERNMENT_ID_FRONT", "GOVERNMENT_ID_BACK"], ["PASSPORT_BIO_PAGE"], ["DRIVING_LICENCE"]] }]
  },
  "countries": [{ "code": "KE", "name": "Kenya" }],
  "limits": { "maxFileSizeBytes": 8388608, "maxFileSizeLabel": "8MB", "allowedMimeTypes": ["application/pdf", "image/jpeg", "image/png", "image/webp"], "allowedExtensions": ["pdf", "jpg", "jpeg", "png", "webp"], "maxDocuments": 25 },
  "reviewTimeHint": "Most applications are reviewed within 3 working days of being submitted."
}
```

### 2. Can I apply? (eligibility)
`GET {{base_url}}/home-owner/applications/eligibility`

```json
{ "canApply": true, "reason": null, "openApplicationId": null, "openApplicationStatus": null }
```
When not allowed: `{ "canApply": false, "reason": "You already have an application in progress.", "openApplicationId": "6720...", "openApplicationStatus": "DRAFT" }`.

### 3. Start application
`POST {{base_url}}/home-owner/applications`

Body is optional. Any fields sent are saved immediately. Status **201**, returns the full application (see the sample under request 6). Add the test script above.

```json
{
  "ownerType": "INDIVIDUAL",
  "country": "KE"
}
```

Errors: `409` if you already have an open application, or the account is not a CLIENT.

### 4. My current application
`GET {{base_url}}/home-owner/applications/mine`

The open application, otherwise the latest one. Returns the full application. `404` if you never applied. Add the test script.

### 5. My applications (history)
`GET {{base_url}}/home-owner/applications`

```json
[
  {
    "id": "6720a1c2e4b0a1b2c3d4e5f6",
    "applicationNumber": "HOA-2026-000042",
    "status": "PROCESSING",
    "statusLabel": "In review",
    "statusColor": "#d97706",
    "applicantName": "John Kipchoge Doe",
    "applicantEmail": "john@example.com",
    "phoneNumber": "+254712345678",
    "ownerType": "INDIVIDUAL",
    "country": "KE",
    "region": "Nairobi County",
    "numberOfProperties": 3,
    "documentCount": 4,
    "verifiedDocumentCount": 1,
    "rejectedDocumentCount": 0,
    "completionPercent": 100,
    "reviewer": { "id": null, "name": "Premisave review team" },
    "submissionCount": 1,
    "submittedAt": "2026-10-09T10:15:30",
    "waitingHours": 5,
    "updatedAt": "2026-10-09T15:02:11"
  }
]
```

### 6. Get application
`GET {{base_url}}/home-owner/applications/{{application_id}}`

Full application. `404` if it is not yours.

```json
{
  "id": "6720a1c2e4b0a1b2c3d4e5f6",
  "applicationNumber": "HOA-2026-000042",
  "status": "INFO_REQUESTED",
  "statusLabel": "Action needed",
  "statusDescription": "The reviewer needs something from you before the review can continue.",
  "statusColor": "#dc2626",
  "nextStep": "Read the reviewer's message, fix or replace what was flagged, then resubmit.",
  "reviewTimeHint": "Most applications are reviewed within 3 working days of being submitted.",
  "latestMessage": "Please upload a clearer photo of your ID.",
  "decisionReason": null,
  "editable": true,
  "applicantId": "66f0...",
  "applicantName": "John Kipchoge Doe",
  "applicantEmail": "john@example.com",
  "ownerType": "INDIVIDUAL",
  "companyName": null,
  "companyRegistrationNumber": null,
  "idType": "NATIONAL_ID",
  "idNumber": "12345678",
  "idIssuingCountry": "KE",
  "taxId": "A123456789Z",
  "dateOfBirth": "1988-05-14",
  "nationality": "KE",
  "phoneNumber": "+254712345678",
  "alternatePhoneNumber": "+254733123456",
  "country": "KE",
  "region": "Nairobi County",
  "city": "Nairobi",
  "physicalAddress": "Ring Road Parklands, Block C",
  "postalAddress": "P.O. Box 1234-00100",
  "numberOfProperties": 3,
  "estimatedTotalUnits": 24,
  "propertyTypes": ["APARTMENT_BLOCK", "COMMERCIAL_SHOPS_OFFICES"],
  "primaryPropertyLocation": "Westlands, Nairobi",
  "propertyDescription": "Two apartment blocks and one shopping arcade",
  "managementPreference": "THROUGH_AGENT",
  "yearsAsLandlord": 6,
  "payoutMethod": "MOBILE_MONEY",
  "mobileMoneyProvider": "M-Pesa",
  "mobileMoneyNumber": "+254712345678",
  "paypalEmail": null,
  "bankName": null,
  "bankAccountName": null,
  "bankAccountNumber": null,
  "bankSwiftCode": null,
  "bankBranch": null,
  "motivation": "I want rent collection and tenant management in one place.",
  "referralSource": "FRIEND_OR_FAMILY",
  "termsAccepted": true,
  "privacyConsent": true,
  "declarationAccepted": true,
  "documents": [
    {
      "id": "b1c2d3e4-1111-2222-3333-444455556666",
      "type": "GOVERNMENT_ID_FRONT",
      "typeLabel": "ID card, front",
      "fileName": "id-front.jpg",
      "mimeType": "image/jpeg",
      "sizeBytes": 482113,
      "documentNumber": "12345678",
      "expiryDate": "2032-01-31",
      "status": "REJECTED",
      "statusLabel": "Needs replacing",
      "reviewReason": "The photo is blurry. Please upload a clear, well lit copy.",
      "reviewedBy": "Premisave review team",
      "reviewedAt": "2026-10-09T14:55:00",
      "uploadedAt": "2026-10-09T10:10:02",
      "downloadPath": "/home-owner/applications/6720a1c2e4b0a1b2c3d4e5f6/documents/b1c2d3e4-1111-2222-3333-444455556666/download"
    }
  ],
  "readiness": {
    "percent": 92,
    "canSubmit": false,
    "allRequiredVerified": false,
    "issues": [{ "field": "documents.IDENTITY", "message": "Identity: a file was rejected, please upload a replacement" }],
    "requirements": [
      { "key": "IDENTITY", "label": "Identity", "description": "Any one of: both sides of your government ID card, the photo page of your passport, or your driving licence.", "required": true,
        "acceptedCombinations": [["GOVERNMENT_ID_FRONT", "GOVERNMENT_ID_BACK"], ["PASSPORT_BIO_PAGE"], ["DRIVING_LICENCE"]],
        "stillNeeded": ["GOVERNMENT_ID_FRONT", "GOVERNMENT_ID_BACK"], "uploaded": false, "verified": false, "needsAttention": true }
    ]
  },
  "timeline": [
    { "type": "STATUS_CHANGED", "fromStatus": "PROCESSING", "toStatus": "INFO_REQUESTED", "actor": "Premisave review team", "message": "Identity: The photo is blurry...", "at": "2026-10-09T14:55:00" },
    { "type": "SUBMITTED", "fromStatus": "DRAFT", "toStatus": "SUBMITTED", "actor": "You", "message": "Application submitted", "at": "2026-10-09T10:15:30" }
  ],
  "reviewer": { "id": null, "name": "Premisave review team" },
  "submissionCount": 1,
  "submittedAt": "2026-10-09T10:15:30",
  "decidedAt": null,
  "createdAt": "2026-10-09T09:58:44",
  "updatedAt": "2026-10-09T14:55:00",
  "internalNotes": null,
  "storageFolder": null
}
```

### 7. Save details (partial update)
`PATCH {{base_url}}/home-owner/applications/{{application_id}}`

Only the fields sent change. A field left out is untouched. Returns the full application. Works while the status is DRAFT or INFO_REQUESTED, otherwise `409`.

```json
{
  "ownerType": "INDIVIDUAL",
  "idType": "NATIONAL_ID",
  "idNumber": "12345678",
  "idIssuingCountry": "KE",
  "dateOfBirth": "1988-05-14",
  "nationality": "KE",
  "taxId": "A123456789Z",
  "phoneNumber": "+254712345678",
  "alternatePhoneNumber": "+254733123456",
  "country": "KE",
  "region": "Nairobi County",
  "city": "Nairobi",
  "physicalAddress": "Ring Road Parklands, Block C",
  "postalAddress": "P.O. Box 1234-00100",
  "numberOfProperties": 3,
  "estimatedTotalUnits": 24,
  "propertyTypes": ["APARTMENT_BLOCK", "COMMERCIAL_SHOPS_OFFICES"],
  "primaryPropertyLocation": "Westlands, Nairobi",
  "propertyDescription": "Two apartment blocks and one shopping arcade",
  "managementPreference": "THROUGH_AGENT",
  "yearsAsLandlord": 6,
  "payoutMethod": "MOBILE_MONEY",
  "mobileMoneyProvider": "M-Pesa",
  "mobileMoneyNumber": "+254712345678",
  "motivation": "I want rent collection and tenant management in one place.",
  "referralSource": "FRIEND_OR_FAMILY",
  "termsAccepted": true,
  "privacyConsent": true,
  "declarationAccepted": true
}
```

Extra fields for other cases:

| Case | Fields |
|---|---|
| Company | `"ownerType": "COMPANY"`, `"companyName"`, `"companyRegistrationNumber"` |
| Bank payout | `"payoutMethod": "BANK_TRANSFER"`, `"bankName"`, `"bankAccountName"`, `"bankAccountNumber"` (account number or IBAN), `"bankSwiftCode"` (optional), `"bankBranch"` (optional) |
| PayPal payout | `"payoutMethod": "PAYPAL"`, `"paypalEmail"` |

Validation error (400):

```json
{ "timestamp": "2026-10-09T10:00:00", "status": 400, "errors": { "phoneNumber": "Phone number looks invalid. Include the country code, for example +254712345678" } }
```

### 8. Upload document
`POST {{base_url}}/home-owner/applications/{{application_id}}/documents`

Body type **form-data**:

| Key | Type | Value | Required |
|---|---|---|---|
| `type` | Text | `GOVERNMENT_ID_FRONT` | yes |
| `file` | File | PDF, JPG, PNG or WEBP up to 8MB | yes |
| `documentNumber` | Text | `12345678` | no |
| `expiryDate` | Text | `2032-01-31` (yyyy-MM-dd, must be in the future) | no |

Document types: `GOVERNMENT_ID_FRONT`, `GOVERNMENT_ID_BACK`, `PASSPORT_BIO_PAGE`, `DRIVING_LICENCE`, `SELFIE_WITH_ID`, `TAX_ID_DOCUMENT`, `TAX_COMPLIANCE_CERTIFICATE`, `TITLE_DEED`, `SALE_AGREEMENT`, `LEASE_AGREEMENT`, `ALLOTMENT_LETTER`, `PROPERTY_TAX_CLEARANCE`, `UTILITY_BILL`, `CERTIFICATE_OF_INCORPORATION`, `DIRECTORS_REGISTER`, `POWER_OF_ATTORNEY`, `OTHER`.

Status **201**, returns the full application with the new file under `documents`. Add the test script.

Errors: `400` wrong file type or too large (413 when it exceeds the server's 10MB request limit), `409` same file already attached, or the type is already verified.

### 9. Delete document
`DELETE {{base_url}}/home-owner/applications/{{application_id}}/documents/{{document_id}}`

Returns the full application. `409` if the document is already verified.

### 10. View or download document
`GET {{base_url}}/home-owner/applications/{{application_id}}/documents/{{document_id}}/download`

Query param: `download` (`true` forces a download, default `false`). Response is the raw file with `Content-Disposition`, `Content-Type` and `X-Content-Type-Options: nosniff`. In Postman use **Send and Download**.

### 11. Submit for review
`POST {{base_url}}/home-owner/applications/{{application_id}}/submit`

No body. Returns the full application with status `SUBMITTED`. If incomplete, status **422**:

```json
{
  "timestamp": "2026-10-09T10:15:30",
  "status": 422,
  "message": "Your application is not complete yet. 2 item(s) still need attention.",
  "details": [
    { "field": "taxId", "message": "Enter your tax ID (for example TIN, PIN, SSN, EIN or VAT number)" },
    { "field": "documents.OWNERSHIP", "message": "Upload: Proof of property ownership" }
  ]
}
```

### 11b. Reapply after a rejection
`POST {{base_url}}/home-owner/applications/{{application_id}}/reapply`

No body. Works only on a `REJECTED` application whose response shows `"canReapply": true`. Returns the full application with status `INFO_REQUESTED`, the rejection reason in `latestMessage`, and `editable: true`. Fix the flagged details with `PATCH`, replace documents, then call **Submit** (step 11), which is recorded as a resubmission.

Errors: `409` when the application is not rejected, the reviewer closed it (`allowReapply: false`), the limit `HOME_OWNER_MAX_REAPPLICATIONS` was reached, or the applicant already has another open application.

### 12. Withdraw application
`POST {{base_url}}/home-owner/applications/{{application_id}}/withdraw`

Body is optional.

```json
{ "reason": "I will apply later" }
```

Returns the full application with status `WITHDRAWN`.

### 13. Delete draft
`DELETE {{base_url}}/home-owner/applications/{{application_id}}`

```json
{ "message": "Draft deleted." }
```
`409` unless the application is a DRAFT that was never submitted.

---

# Folder 2: staff-application-review

Base path: `{{base_url}}/staff/applications`. All requests need a staff token. Others get `403`.

### 14. Dashboard stats
`GET {{base_url}}/staff/applications/stats`

```json
{
  "total": 57,
  "byStatus": { "DRAFT": 12, "SUBMITTED": 9, "PROCESSING": 14, "INFO_REQUESTED": 6, "VERIFIED": 4, "ACCEPTED": 8, "REJECTED": 3, "WITHDRAWN": 1 },
  "awaitingReview": 9,
  "unassigned": 11,
  "assignedToMe": 5,
  "needsInfoFromApplicant": 6,
  "readyToPromote": 4,
  "averageDecisionHours": 41.5,
  "oldestWaitingSince": "2026-10-06T08:12:00",
  "oldestWaitingApplicationId": "6720a1c2e4b0a1b2c3d4e5f6"
}
```
`total` excludes drafts.

### 15. Review queue
`GET {{base_url}}/staff/applications`

Query params (all optional):

| Param | Example | Meaning |
|---|---|---|
| `q` | `john` | Matches application number, name, email, phone, ID number, tax ID or company |
| `status` | `SUBMITTED,PROCESSING` | One or more, comma separated. Default is everything except drafts |
| `ownerType` | `COMPANY` | `INDIVIDUAL`, `COMPANY` or `PROPERTY_MANAGER` |
| `country` | `KE` | ISO code |
| `region` | `Nairobi County` | Exact match, case insensitive |
| `assignee` | `me`, `none` or a user id | |
| `sort` | `oldest` (default), `newest`, `updated` | |
| `page`, `size` | `0`, `20` | Size is capped at 100 |

Suggested saved examples: **Unassigned waiting** (`status=SUBMITTED&assignee=none`), **My work** (`status=SUBMITTED,PROCESSING,VERIFIED&assignee=me`), **Ready to promote** (`status=VERIFIED`).

```json
{
  "content": [ { "id": "6720a1c2e4b0a1b2c3d4e5f6", "applicationNumber": "HOA-2026-000042", "status": "SUBMITTED", "statusLabel": "Submitted", "statusColor": "#2563eb", "applicantName": "John Kipchoge Doe", "applicantEmail": "john@example.com", "phoneNumber": "+254712345678", "ownerType": "INDIVIDUAL", "country": "KE", "region": "Nairobi County", "numberOfProperties": 3, "documentCount": 4, "verifiedDocumentCount": 0, "rejectedDocumentCount": 0, "completionPercent": 100, "reviewer": null, "submissionCount": 1, "submittedAt": "2026-10-09T10:15:30", "waitingHours": 5, "updatedAt": "2026-10-09T10:15:30" } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 16. Search by name, email, ID number or tax ID
`GET {{base_url}}/staff/applications?q=john`

Same response as the queue.

### 17. Reviewers (for assignment)
`GET {{base_url}}/staff/applications/reviewers`

```json
[ { "id": "66f1...", "name": "Jane Admin", "email": "jane@premisave.com", "role": "ADMIN" } ]
```

### 18. Open application (staff view)
`GET {{base_url}}/staff/applications/{{application_id}}`

Same shape as request 6, with the real reviewer, document reviewer names, `internalNotes` and `storageFolder` filled in:

```json
{
  "reviewer": { "id": "66f1...", "name": "Jane Admin" },
  "internalNotes": [ { "id": "n1", "author": "Jane Admin", "authorRole": "ADMIN", "text": "Called the applicant, confirmed the plot number.", "at": "2026-10-09T13:20:00" } ],
  "storageFolder": "HOA-2026-0007 Jane Doe"
}
```

### 19. Claim (assign to me and start review)
`POST {{base_url}}/staff/applications/{{application_id}}/claim`

No body. Assigns it to you. A SUBMITTED application moves to PROCESSING and the applicant is emailed. Returns the staff view. `409` for drafts and closed applications.

### 20. Assign to a colleague (admin)
`PUT {{base_url}}/staff/applications/{{application_id}}/assign`

```json
{ "reviewerId": "{{reviewer_id}}" }
```
Only an ADMIN can assign to someone else. Anyone can assign to themselves. Returns the staff view.

### 21. Verify a document
`POST {{base_url}}/staff/applications/{{application_id}}/documents/{{document_id}}/review`

```json
{ "status": "VERIFIED" }
```
A SUBMITTED application moves to PROCESSING. The applicant is emailed. Returns the staff view.

### 22. Reject a document and ask for a new one
`POST {{base_url}}/staff/applications/{{application_id}}/documents/{{document_id}}/review`

```json
{
  "status": "REJECTED",
  "reason": "The photo is blurry. Please upload a clear, well lit copy.",
  "requestReupload": true
}
```
`reason` is required when rejecting. With `requestReupload: true` the application also moves to INFO_REQUESTED. Rejecting a document on a VERIFIED application sends it back to PROCESSING.

### 23. Start processing
`POST {{base_url}}/staff/applications/{{application_id}}/status`

```json
{ "status": "PROCESSING" }
```

### 24. Request more information
`POST {{base_url}}/staff/applications/{{application_id}}/status`

```json
{
  "status": "INFO_REQUESTED",
  "message": "Please upload a clearer photo of your ID.",
  "internalNote": "ID number did not match the photo."
}
```
`message` is required, shown to the applicant and emailed. `internalNote` is optional and staff only.

### 25. Mark verified
`POST {{base_url}}/staff/applications/{{application_id}}/status`

```json
{
  "status": "VERIFIED",
  "message": "All documents check out.",
  "internalNote": "Checked ID and ownership documents."
}
```
Refused with **422** until every required detail is present and every required document is verified, with `details` listing what is missing.

### 26. Reject application
`POST {{base_url}}/staff/applications/{{application_id}}/status`

```json
{
  "status": "REJECTED",
  "message": "We could not match the ID number to the title deed."
}
```
`message` is required.

When rejecting, add `"allowReapply": false` to close the application so the applicant cannot reopen it (omit it or send `true` to let them correct and reapply).

Allowed status moves: SUBMITTED to PROCESSING, INFO_REQUESTED or REJECTED. PROCESSING to INFO_REQUESTED, VERIFIED or REJECTED. INFO_REQUESTED to REJECTED. VERIFIED to PROCESSING, INFO_REQUESTED or REJECTED. An admin can also reopen REJECTED to PROCESSING. `ACCEPTED` cannot be set here (`400`), use promote. Anything else returns `409` listing the allowed next steps.

### 27. Add internal note
`POST {{base_url}}/staff/applications/{{application_id}}/notes`

```json
{ "text": "Called the applicant, confirmed the plot number." }
```
Returns the staff view. Applicants never see notes.

### 28. View or download document (staff)
`GET {{base_url}}/staff/applications/{{application_id}}/documents/{{document_id}}/download`

Query param `download=true|false`. Raw file response, same as request 10.

---

# Folder 3: admin-application-actions

### 29. Make Home Owner (promote)
`POST {{base_url}}/admin/applications/{{application_id}}/promote`

Admin only. The application must be VERIFIED. Body is optional.

```json
{
  "message": "Welcome aboard! You can now list your properties.",
  "internalNote": "Approved after document check."
}
```

Response 200:

```json
{
  "applicationId": "6720a1c2e4b0a1b2c3d4e5f6",
  "applicationNumber": "HOA-2026-000042",
  "userId": "66f0...",
  "userEmail": "john@example.com",
  "previousRole": "CLIENT",
  "newRole": "HOME_OWNER",
  "status": "ACCEPTED",
  "message": "John Kipchoge Doe is now a Home Owner. They were emailed. Their new access applies to new sign-ins and token refreshes."
}
```

Errors: `403` for non-admins, `409` if the application is not VERIFIED, already ACCEPTED, or the account is not a CLIENT, `422` if something is no longer verified.

---

# Error shapes

| Status | Shape |
|---|---|
| 400 validation | `{ "timestamp", "status": 400, "errors": { "field": "message" } }` |
| 400, 403, 404, 409 | `{ "timestamp", "status", "message" }` |
| 422 | `{ "timestamp", "status": 422, "message", "details": [ { "field", "message" } ] }` |
| 413 | `{ "timestamp", "status": 413, "message" }` for oversized uploads |
| 401 | Missing or expired token |
| 403 | `{ "timestamp", "status": 403, "error": "Forbidden", "message": "You are not authorized to access this resource." }` for a non-staff token on `/staff/**` or a non-admin on `/admin/**` |

# Suggested test order

1. Sign in as a CLIENT, run **Start application**, **Save details**, upload the required documents, **Submit**.
2. Sign in as an ADMIN, run **Review queue**, **Claim**, **Verify a document** for each file, **Mark verified**.
3. Still as ADMIN, run **Make Home Owner**.
4. Sign in as the client again and run **My current application**. The status is ACCEPTED and the account role is HOME_OWNER.
