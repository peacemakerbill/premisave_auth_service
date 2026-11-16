package com.premisave.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactRequest {

    @NotBlank(message = "Please enter your name")
    @Size(min = 2, max = 100, message = "Your name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Please enter your email address")
    @Email(message = "Please enter a valid email address")
    @Size(max = 254, message = "That email address is too long")
    private String email;

    /** A topic code such as GENERAL or SECURITY (see ContactTopic). */
    @NotBlank(message = "Please choose a topic")
    @Size(max = 60, message = "Please choose a topic from the list")
    private String topic;

    @NotBlank(message = "Please write a message")
    @Size(min = 10, max = 2000, message = "Your message must be between 10 and 2000 characters")
    private String message;

    /** Hidden field the app never fills. Automated form fillers do, and those messages are dropped. */
    @Size(max = 200)
    private String website;
}
