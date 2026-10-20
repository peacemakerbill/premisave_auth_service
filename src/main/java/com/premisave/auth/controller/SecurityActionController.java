package com.premisave.auth.controller;

import com.premisave.auth.dto.security.SecurityDtos.AcceptInvitationRequest;
import com.premisave.auth.dto.security.SecurityDtos.ConfirmReportRequest;
import com.premisave.auth.dto.security.SecurityDtos.ReportTokenRequest;
import com.premisave.auth.service.security.AdminInvitationService;
import com.premisave.auth.service.security.ClientContextResolver;
import com.premisave.auth.service.security.SecurityActionService;
import com.premisave.auth.service.security.SecurityActionService.ConfirmResult;
import com.premisave.auth.service.security.SecurityActionService.Preview;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoints behind the links in security emails. They are POST only on purpose: a GET that
 * changes state is triggered by mail scanners and link previews. The frontend page reads ?token=
 * from the link, calls inspect to show what happened, and calls confirm only when the owner agrees.
 */
@RestController
@RequestMapping("/auth")
public class SecurityActionController {

    private final SecurityActionService actions;
    private final AdminInvitationService invitations;
    private final ClientContextResolver clientContext;

    public SecurityActionController(SecurityActionService actions, AdminInvitationService invitations,
                                    ClientContextResolver clientContext) {
        this.actions = actions;
        this.invitations = invitations;
        this.clientContext = clientContext;
    }

    /** Shows the reported sign-in. Changes nothing and does not use up the token. */
    @PostMapping("/security-actions/inspect")
    public Preview inspect(@Valid @RequestBody ReportTokenRequest request) {
        return actions.inspect(request.token());
    }

    /** "This was not me": ends the session, removes device trust, notifies the owner. Single use. */
    @PostMapping("/security-actions/confirm")
    public ConfirmResult confirm(@Valid @RequestBody ConfirmReportRequest request, HttpServletRequest http) {
        return actions.confirmReport(request.token(), request.requestPasswordReset(), clientContext.resolve(http));
    }

    /** Lets a pre-provisioned administrator choose a password and activate the account. Single use. */
    @PostMapping("/admin-invitation/accept")
    public ResponseEntity<Map<String, String>> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request,
                                                                HttpServletRequest http) {
        invitations.accept(request.token(), request.newPassword(), request.confirmPassword(), clientContext.resolve(http));
        return ResponseEntity.ok(Map.of("message", "Your administrator account is active. You can sign in now."));
    }
}
