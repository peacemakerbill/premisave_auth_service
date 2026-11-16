package com.premisave.auth.controller;

import com.premisave.auth.dto.ContactRequest;
import com.premisave.auth.dto.ContactResponse;
import com.premisave.auth.service.ContactService;
import com.premisave.auth.service.security.ClientContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contact us. Public on purpose, so people who cannot sign in can still reach the team.
 * Signed in callers are recognised and linked to their account. Abuse is limited by ContactGuard.
 */
@RestController
@RequestMapping("/contact")
public class ContactController {

    private final ContactService contactService;
    private final ClientContextResolver clientContext;

    public ContactController(ContactService contactService, ClientContextResolver clientContext) {
        this.contactService = contactService;
        this.clientContext = clientContext;
    }

    @PostMapping
    public ResponseEntity<ContactResponse> send(@Valid @RequestBody ContactRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactService.submit(request, clientContext.resolve(http)));
    }
}
