package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponse {
    /** Quote this when writing to the team about the message. */
    private String reference;
    private String message;
}
