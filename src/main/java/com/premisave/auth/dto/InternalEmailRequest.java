package com.premisave.auth.dto;

import java.util.Map;

/**
 * A request from another Premisave service (the messenger today) to email one of our users.
 * The service knows the user by id, not by address: the address never leaves this service.
 *
 * title    the heading inside the email, for example "You are now an admin"
 * message  the sentence under the greeting
 * details  optional label and value pairs shown as a small table, in the order given
 * buttonLabel and buttonPath add a button that opens the web app at that path, for example "Open messages" and "/messages"
 * icon        optional one or two characters for the round badge at the top, for example "@". Empty means "G".
 */
public record InternalEmailRequest(
        String userId,
        String subject,
        String title,
        String message,
        Map<String, String> details,
        String buttonLabel,
        String buttonPath,
        String icon) {
}
