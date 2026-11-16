package com.premisave.auth.entity;

import com.premisave.auth.enums.ContactTopic;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A message sent through the Contact us form. It is saved before the email to the team goes out,
 * so a mail outage never loses what a customer wrote.
 */
@Data
@Document(collection = "contact_messages")
@CompoundIndex(name = "created_idx", def = "{'createdAt': -1}")
public class ContactMessage {

    public enum Status {
        /** Saved, email to the team not sent yet. */
        RECEIVED,
        /** The team's inbox accepted it. */
        DELIVERED,
        /** The email to the team failed; the text is still here. */
        DELIVERY_FAILED
    }

    @Id
    private String id;

    /** Short code the customer can quote, for example PRM-7K3Q9D2M. */
    private String reference;

    private String name;
    private String email;
    private ContactTopic topic;
    private String message;

    /** Set when the sender was signed in. */
    private String userId;

    /** Short hash of the client address, for spotting abuse without keeping the address. */
    private String clientHash;

    private Status status;
    private Instant createdAt;
}
