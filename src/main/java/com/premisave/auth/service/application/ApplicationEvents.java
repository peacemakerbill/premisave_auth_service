package com.premisave.auth.service.application;

import com.premisave.auth.entity.ApplicationEvent;
import com.premisave.auth.entity.HomeOwnerApplication;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.ApplicationEventType;
import com.premisave.auth.enums.ApplicationStatus;

import java.util.ArrayList;

/** Builds timeline entries. */
final class ApplicationEvents {

    private ApplicationEvents() {
    }

    static void add(HomeOwnerApplication app, ApplicationEventType type, ApplicationStatus from, ApplicationStatus to,
                    User actor, boolean byApplicant, String message) {
        ApplicationEvent event = new ApplicationEvent();
        event.setType(type);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setByApplicant(byApplicant);
        event.setMessage(message);
        if (actor != null) {
            event.setActorId(actor.getId());
            event.setActorName(CurrentUser.fullName(actor));
            event.setActorRole(actor.getRole() == null ? null : actor.getRole().name());
        }
        if (app.getEvents() == null) {
            app.setEvents(new ArrayList<>());
        }
        app.getEvents().add(event);
    }
}